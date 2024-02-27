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
import com.intellij.ide.starter.junit5.config.KillOutdatedProcesses
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.plugins.PluginNotFoundException
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.di.teamCityIntelliJPerformanceServer
import com.intellij.tools.plugin.checker.marketplace.MarketplaceEvent
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


@ExtendWith(KillOutdatedProcesses::class)
class InstallPluginTest {
  companion object {
    private val pluginsWithUI = listOf(12798, 8079, 21452, 15503, 13227, 14823, 21709, 14946, 16478, 10253, 20603, 19772, 16353, 21531, 94086, 17153,
                                       1800, 14015, 21624, 20158, 23046, 19470)

    private fun setDebugBuildParamsForLocalDebug(vararg buildProperties: Pair<String, String>): Path {
      val tempPropertiesFile = File.createTempFile("teamcity_", "_properties_file.properties")

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

    private fun getMarketplaceEvent(): MarketplaceEvent {
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

      return modifyTestCaseForIdeVersion(draftParams)
    }

    private fun modifyTestCaseForIdeVersion(params: EventToTestCaseParams): List<EventToTestCaseParams> {
      if (!IdeProductProvider.isProductSupported(params.event.productCode)) {
        logOutput(
          RuntimeException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}"))
        return emptyList()
      }

      if (params.event.productCode == IdeProductProvider.AI.productCode) {
        logOutput(
          RuntimeException("Product ${params.event.productCode} is not supported yet. Link to download it ${params.event.productLink}"))
        return emptyList()
      }

      val versionNumber = params.event.productVersion.split("-")[1].split(".")[0].toInt()
      if (versionNumber <= 200) {
        logOutput(RuntimeException("Version ${params.event.productVersion} is not supported."))
        return emptyList()
      }

      if (params.event.productVersion.startsWith("PC-231.")) {
        logOutput(RuntimeException("Product ${params.event.productCode} is not supported in 231 branch yet. " +
                                   "Since Performance Plugin is not bundled (yet) and not published."))
        return emptyList()
      }

      if (params.event.productVersion.startsWith("RM-232.")) {
        logOutput(RuntimeException("Product ${params.event.productCode} is not supported since it freezes on IDEA-320042"))
        return emptyList()
      }

      if (params.event.productVersion.startsWith("DB") && versionNumber < 232) {
        logOutput(
          RuntimeException("Product ${params.event.productVersion} is not supported: https://youtrack.jetbrains.com/issue/DBE-16528"))
        return emptyList()
      }

      if (params.event.productCode == IdeProductProvider.DS.productCode) {
        logOutput(RuntimeException("Product ${params.event.productCode} is not supported yet. There is some issue with the license."))
        return emptyList()
      }

      if (params.event.pricingModel == "PAID") {
        logOutput(RuntimeException("Paid plugins are not supported yet. Plugin id: ${params.event.pluginId}"))
        return emptyList()
      }

      if (pluginsWithUI.contains(params.event.pluginId)) {
        logOutput(RuntimeException("Plugins with UI on startup are not supported yet. Plugin id: ${params.event.pluginId}"))
        return emptyList()
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
  }

  private fun createTestContext(params: EventToTestCaseParams, configurator: IDETestContext.()->Unit = {}): IDETestContext {
    val testContext = Starter
      .newContext(testName = CurrentTestMethod.hyphenateWithClass(), testCase = params.testCase)
      .prepareProjectCleanImport()
      .setSharedIndexesDownload(enable = true)
      .setLicense(System.getenv("LICENSE_KEY"))

    testContext.configurator()

    return testContext
  }

  @ParameterizedTest
  @MethodSource("data")
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  fun installPluginTest(params: EventToTestCaseParams) {
    val testContextWithoutPlugin = createTestContext(params)
    val ideRunContextWithoutPlugin = testContextWithoutPlugin.runIDE(commands = CommandChain().exitApp()).runContext
    val errorsWithoutPlugin = ErrorReporterToCI.collectErrors(ideRunContextWithoutPlugin.logsDir)

    try {
      val testContext = createTestContext(params) { pluginConfigurator.installPluginFromURL(params.event.file) }
      val ideRunContext = testContext.runIDE(commands = CommandChain().exitApp()).runContext
      val errors = ErrorReporterToCI.collectErrors(ideRunContext.logsDir)

      val diff = subtract(errors, errorsWithoutPlugin).toList()

      ErrorReporterToCI.reportErrors(ideRunContext, diff)
    }
    catch (ex: Exception) {
      when (ex) {
        is IOException, //plugin is in removal state and not available
        is PluginNotFoundException -> return //don't run the test if plugin was removed by author
        else -> throw ex
      }
    }
  }
}