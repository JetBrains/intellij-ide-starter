package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.ci.teamcity.withAuth
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginNotFoundException
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.plugin.checker.aws.VerificationMessage
import com.intellij.tools.plugin.checker.aws.VerificationResultType
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.di.teamCityIntelliJPerformanceServer
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent
import com.intellij.tools.plugin.checker.marketplace.MarketplaceReporter
import com.intellij.tools.plugin.checker.report.StartupFailureAnalyzer
import com.intellij.tools.plugin.checker.tests.InstallPluginTest.Companion.getMarketplaceEvent
import com.intellij.util.containers.ContainerUtil.subtract
import com.intellij.util.system.OS
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories


@ExtendWith(KillOutdatedProcessesAfterEach::class)
class InstallPluginTest {
  companion object {

    private fun setDebugBuildParamsForLocalDebug(vararg buildProperties: Pair<String, String>): Path {
      @Suppress("SSBasedInspection") val tempPropertiesFile = File.createTempFile("teamcity_", "_properties_file.properties")

      Properties().apply {
        buildProperties.forEach {
          check(it.second.isNotEmpty()) { "Property ${it.first} has empty value" }
          this.setProperty(it.first, it.second)
        }
        store(tempPropertiesFile.outputStream(), "")
      }

      return tempPropertiesFile.toPath()
    }

    init {
      if (!teamCityIntelliJPerformanceServer.isBuildRunningOnCI) {
        // use this to simplify local debug
        val systemPropertiesFilePath = setDebugBuildParamsForLocalDebug(
          Pair("teamcity.build.id", "55604"),
          Pair("teamcity.auth.userId", ""),
          Pair("teamcity.auth.password", "")
        )
        initPluginCheckerDI(systemPropertiesFilePath)
      }
      else {
        initPluginCheckerDI()
      }
    }

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      Assumptions.assumeTrue({ data().isNotEmpty() }, "Cannot run tests, because tests cases are empty")
    }

    /**
     * Json we get, isn't valid. So we have to do regexp thing before deserialization
     */
    private fun String.extractMarketplaceDetailPayload(): String {
      val matchResult = Regex("\"detail\":(.)*}").find(this)

      requireNotNull(matchResult) { "Error happened during searching for detail field in trigger parameter" }

      return matchResult.groups.single { it != null && it.value.startsWith("\"detail\"") }!!
        .value.removePrefix("\"detail\":").removeSuffix("}")
    }

    private fun deserializeMessageFromMarketplace(input: String): MarketplaceEvent {
      val jacksonMapper = jsonMapper {
        addModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      }

      val detailField = jacksonMapper.readTree(input.extractMarketplaceDetailPayload())
      return jacksonMapper.treeToValue<MarketplaceEvent>(detailField)
    }

    fun getMarketplaceEvent(): MarketplaceEvent {
      val buildProperties = TeamCityClient.run {
        get(
          fullUrl = restUri.resolve("builds/id:${CIServer.instance.asTeamCity().buildId}/resulting-properties")
        ) { it.withAuth() }
      }

      val snsMessageBody = buildProperties.path("property")
        .firstOrNull { it.get("name").asText() == "sns.message.body" }
        ?.get("value")?.asText()
      requireNotNull(snsMessageBody) { "Couldn't read the sns.message.body build configuration parameter" }

      return deserializeMessageFromMarketplace(snsMessageBody)
    }

    @JvmStatic
    fun data(): List<EventToTestCaseParams> {
      val event = getMarketplaceEvent()
      val testCase = when (event.productCode) {
        IdeProductProvider.PS.productCode -> TestCases.PS.LaravelFramework
        else -> TestCases.IU.GradleJitPackSimple
      }
      val draftParams = EventToTestCaseParams(
        event = event,
        testCase = testCase
      )

      val resultingTestCase = try {
        modifyTestCaseForIdeVersion(draftParams)
      } catch (ex: UnableToVerifyException) {
        logOutput(RuntimeException(ex.message))
        PluginUpdateMarketplaceReporter.reportUnableToVerifyError(ex.message!!)
        return emptyList()
      }

      return resultingTestCase
    }

    private fun modifyTestCaseForIdeVersion(params: EventToTestCaseParams): List<EventToTestCaseParams> {
      if (!IdeProductProvider.isProductSupported(params.event.productCode)) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}")
      }

      if (params.event.productCode == IdeProductProvider.AI.productCode) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}")
      }

      val versionNumber = params.event.productVersion.split("-")[1].split(".")[0].toInt()
      if (versionNumber <= 203) {
        throw UnableToVerifyException("Version ${params.event.productVersion} is not supported.")
      }

      if (params.event.productVersion.startsWith("PC-231.")) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported in 231 branch yet. " +
                                      "Since Performance Plugin is not bundled (yet) and not published.")
      }

      if (params.event.productVersion.startsWith("RM-232.")) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported since it freezes on IDEA-320042")
      }

      if (params.event.productVersion.startsWith("DB") && versionNumber < 232) {
        throw UnableToVerifyException("Product ${params.event.productVersion} is not supported: https://youtrack.jetbrains.com/issue/DBE-16528")
      }

      if (params.event.productCode == IdeProductProvider.DS.productCode) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported yet. There is some issue with the license.")
      }

      if (params.event.pricingModel == "PAID") {
        throw UnableToVerifyException("Paid plugins are not supported yet. Plugin id: ${params.event.pluginId}")
      }

      if (params.event.productCode == IdeProductProvider.QA.productCode) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported yet. There is some issue with the license.")
      }

      if (params.event.productCode == IdeProductProvider.RR.productCode) {
        throw UnableToVerifyException("Product ${params.event.productCode} is not supported yet. There is some issue with the license.")
      }

      if (params.event.productCode == IdeProductProvider.RD.productCode && versionNumber < 243) {
        throw UnableToVerifyException("${params.event.productCode} older than 2024.3 is not supported because it reports 1s freezes as failed tests.")
      }

      if (params.event.productCode == IdeProductProvider.GW.productCode) {
        throw UnableToVerifyException("${params.event.productCode} is not supported yet.")
      }

      val link = params.event.productLink.substring(0, params.event.productLink.indexOf(".tar.gz"))
      val downloadLink: String = link + when (OS.CURRENT) {
        OS.Linux -> ".tar.gz"
        OS.macOS -> {
          if (SystemInfo.OS_ARCH == "aarch64") "-aarch64.dmg"
          else ".dmg"
        }
        OS.Windows -> ".exe"
        else -> throw RuntimeException("OS is not supported")
      }

      val ideInfo = IdeProductProvider.getProducts().single { it.productCode == params.event.productCode }
        .copy(downloadURI = URI(downloadLink), buildType = params.event.productType ?: "")

      val paramsWithAppropriateIde = params.onIDE(ideInfo)
      val numericProductVersion = paramsWithAppropriateIde.event.getNumericProductVersion()

      return listOf(paramsWithAppropriateIde.copy(
        testCase = paramsWithAppropriateIde.testCase.copy(ideInfo = ideInfo.copy(buildNumber = numericProductVersion))))
    }

    fun handleTimeout(testContext: IDETestContext, errorsWithoutPlugin: List<Error>) {
      val runContext = IDERunContext(testContext, launchName = "with-plugin")
      val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
      val pluginErrors = subtract(errors, errorsWithoutPlugin).toMutableList()

      TimeoutAnalyzer.analyzeTimeout(runContext)?.let { pluginErrors.add(it) }
      PluginUpdateMarketplaceReporter.reportIdeErrors(pluginErrors)
      ErrorReporterToCI.reportErrors(runContext, pluginErrors)
    }

    fun handleStartupFailure(testContext: IDETestContext, errorsWithoutPlugin: List<Error>, exception: Exception) {
      val runContext = IDERunContext(testContext, launchName = "with-plugin")
      val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
      val pluginErrors = mutableListOf<Error>()

      StartupFailureAnalyzer.analyzeStartupFailure(runContext)?.let { pluginErrors.add(it) }
      if (pluginErrors.isEmpty()) throw exception

      pluginErrors.addAll(subtract(errors, errorsWithoutPlugin))
      PluginUpdateMarketplaceReporter.reportIdeErrors(pluginErrors)
      ErrorReporterToCI.reportErrors(runContext, pluginErrors)
    }
  }

  private fun createTestContext(params: EventToTestCaseParams, configurator: IDETestContext.()->Unit = {}): IDETestContext {
    val testContext = Starter
      .newContext(testName = "install-plugin-test", testCase = params.testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .setLicense(System.getenv("LICENSE_KEY"))
      .applyVMOptionsPatch {
        addSystemProperty("llm.show.ai.promotion.window.on.start", false)
        addSystemProperty("ide.show.tips.on.startup.default.value", false)
      }

    testContext.configurator()

    return testContext
  }

  @ParameterizedTest
  @MethodSource("data")
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  fun installPluginTest(params: EventToTestCaseParams) {
    val testContextWithoutPlugin = createTestContext(params)
    val ideRunContextWithoutPlugin = testContextWithoutPlugin.runIDE(launchName = "without-plugin", commands = CommandChain().exitApp()).runContext
    val errorsWithoutPlugin = ErrorReporterToCI.collectErrors(ideRunContextWithoutPlugin.logsDir)

    val testContext: IDETestContext
    try {
      testContext = createTestContext(params) { pluginConfigurator.installPluginFromURL(params.event.file) }
    }
    catch (ex: Exception) {
      when (ex) {
        is IOException, //plugin is in removal state and not available
        is PluginNotFoundException -> return //don't run the test if plugin was removed by author
        else -> throw ex
      }
    }

    try {
      val ideRunContext = testContext.runIDE(
        launchName = "with-plugin",
        commandLine = { IDECommandLine.OpenTestCaseProject(testContext, listOf("-Dperformance.watcher.unresponsive.interval.ms=10000")) },
        commands = CommandChain().exitApp()
      ).runContext
      val errors = ErrorReporterToCI.collectErrors(ideRunContext.logsDir)

      val pluginErrors = subtract(errors, errorsWithoutPlugin).toList()

      ErrorReporterToCI.reportErrors(ideRunContext, pluginErrors)
      PluginUpdateMarketplaceReporter.reportIdeErrors(pluginErrors)
    }
    catch (ex: Exception) {
      when {
        ex is ExecTimeoutException -> handleTimeout(testContext, errorsWithoutPlugin)
        ex.message?.contains("failed with code 3") == true -> handleStartupFailure(testContext, errorsWithoutPlugin, ex)
        else -> throw ex
      }
    }
  }
}

object PluginUpdateMarketplaceReporter : MarketplaceReporter() {

  private val marketplaceEvent = getMarketplaceEvent()

  private val buildUrl = "https://intellij-plugins-performance.teamcity.com/buildConfiguration/" +
                         "PluginPlatformTests_ExternalPluginsCheckerTests/" +
                         "${System.getenv("BUILD_NUMBER")}?guest=1"

  fun reportUnableToVerifyError(reason: String) {
    val message = VerificationMessage(
      "Unable to verify: $reason",
      VerificationResultType.UNABLE_TO_VERIFY,
      buildUrl,
      marketplaceEvent.id,
      marketplaceEvent.verificationType,
      null
    )

    sendSqsMessage(message)
  }

  fun reportIdeErrors(errors: List<Error>) {
    val verificationResult = classifyErrors(errors)

    val url = when {
      verificationResult == VerificationResultType.OK -> buildUrl
      else -> {
        val sarifPath = GlobalPaths.instance.artifactsDirectory
          .resolve("sarif-reports/${marketplaceEvent.pluginId}/${marketplaceEvent.id}")
          .createDirectories()
          .resolve("sarif.json")

        generateSarifReport(
          errors = errors,
          semanticVersion = marketplaceEvent.productVersion,
          artifactsLocation = "$buildUrl&buildTab=artifacts#%2Finstall-plugin-test%2Fwith-plugin",
          sarifPath = sarifPath,
          artifactPath = "install-plugin-test"
        )
        "${marketplaceEvent.pluginId}/${marketplaceEvent.id}/sarif.json"
      }
    }

    val message = VerificationMessage(
      generateVerdict(errors),
      verificationResult,
      url,
      marketplaceEvent.id,
      marketplaceEvent.verificationType,
      null
    )

    sendSqsMessage(message)
  }
}

internal class UnableToVerifyException(reason: String) : RuntimeException(reason)
