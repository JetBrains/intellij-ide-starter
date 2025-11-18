package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.ci.teamcity.withAuth
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider.IU
import com.intellij.ide.starter.junit5.config.KillOutdatedProcessesAfterEach
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginNotFoundException
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.plugin.checker.aws.SqsClientWrapper
import com.intellij.tools.plugin.checker.aws.VerificationMessage
import com.intellij.tools.plugin.checker.aws.VerificationResultType
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.di.teamCityIntelliJPerformanceServer
import com.intellij.tools.plugin.checker.marketplace.MarketplaceClient
import com.intellij.tools.plugin.checker.marketplace.Plugin
import com.intellij.tools.plugin.checker.sarif.*
import software.amazon.awssdk.regions.Region.EU_WEST_1
import com.intellij.util.containers.ContainerUtil.subtract
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class InstallPluginAfterUpdateIdeTest {
  private lateinit var errorsWithoutPlugin: List<Error>

  private data class ConfigurationData(
    val type: String,
    val url: String,
    val currentBatchIndex: Int,
  )

  companion object {
    private fun JsonNode.getProperty(name: String): String {
      return this
        .first { it.get("name").asText() == name }
        .get("value")
        .asText()
    }

    private fun getConfigurationData(): ConfigurationData {
      val buildProperties = TeamCityClient.run {
        get(
          fullUrl = restUri.resolve("builds/id:${CIServer.instance.asTeamCity().buildId}/resulting-properties")
        ) { it.withAuth() }
      }

      val propertyNode = buildProperties.path("property")

      return ConfigurationData(
        propertyNode.getProperty("ide.type"),
        propertyNode.getProperty("ide.download.url"),
        propertyNode.getProperty("batch.index").toInt(),
      )
    }


    private fun <T> splitIntoBuckets(list: List<T>): List<List<T>> {
      val batchesCount = 20
      val bucketSize = list.size / batchesCount
      val remainder = list.size % batchesCount
      return (0 until batchesCount).map { i ->
        val start = i * bucketSize + minOf(i, remainder)
        val end = (i + 1) * bucketSize + minOf(i + 1, remainder)
        list.subList(start, end)
      }
    }

    private fun createTestContext(case: TestCase<*>, configurator: IDETestContext.() -> Unit = {}): IDETestContext {
      val testContext = Starter
        .newContext(testName = "install plugin test", testCase = case)
        .prepareProjectCleanImport()
        .setSharedIndexesDownload(enable = true)
        .setLicense(System.getenv("LICENSE_KEY"))

      testContext.configurator()

      return testContext
    }

    @JvmStatic
    fun pluginsProvider(): List<Arguments> {
      initPluginCheckerDI()
      val configurationData = getConfigurationData()
      val case = TestCases.IU.GradleJitPackSimple
        .copy(
          ideInfo = IU.copy(
            downloadURI = URI(configurationData.url)
          )
        )

      val context = createTestContext(case)

      val plugins = MarketplaceClient.getPluginsForBuild(configurationData.type, context.ide.build)


      println("Current batch index: ${configurationData.currentBatchIndex}")
      val pluginsForThisBucket = splitIntoBuckets(plugins)[configurationData.currentBatchIndex]

      return pluginsForThisBucket.map { Arguments.of(context to it, it.name) }
    }
  }

  @Test
  @BeforeAll
  fun runIdeWithoutPlugin() {
    initPluginCheckerDI()
    val configurationData = getConfigurationData()
    val case = TestCases.IU.GradleJitPackSimple
      .copy(
        ideInfo = IU.copy(
          downloadURI = URI(configurationData.url)
        )
      )

    val context = createTestContext(case)
    val ideRunContextWithoutPlugin = context.runIDE(launchName = "without-plugin", commands = CommandChain().exitApp()).runContext
    errorsWithoutPlugin = ErrorReporterToCI.collectErrors(ideRunContextWithoutPlugin.logsDir)
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("pluginsProvider")
  fun validatePlugin(data: Pair<IDETestContext, Plugin>, pluginName: String) {
    val (context, plugin) = data
    val contextWithPlugin = createTestContext(context.testCase)
    val pluginPath = contextWithPlugin.paths.testHome.resolve(plugin.id + ".zip").createFile()

    try {
      MarketplaceClient.downloadPlugin(plugin, pluginPath)
    } catch (e: Exception) {
      when (e) {
        is IOException, is PluginNotFoundException -> return
        else -> throw e
      }
    }

    contextWithPlugin.apply { pluginConfigurator.installPluginFromPath(pluginPath) }

    try {
      val ideRunContext = contextWithPlugin.runIDE(launchName = "with-plugin-${plugin.id}", commands = CommandChain().exitApp(), runTimeout = 3.minutes).runContext
      val errorsWithPlugin = ErrorReporterToCI.collectErrors(ideRunContext.logsDir)
      val diff = subtract(errorsWithPlugin, errorsWithoutPlugin).toList()

      ErrorReporterToCI.reportErrors(ideRunContext, diff)
      NewInstallerMarketplaceReporter.reportPluginErrors(plugin, diff, context.ide.build)
    }
    catch (ex: Exception) {
      when (ex) {
        is ExecTimeoutException -> handleTimeout(contextWithPlugin, errorsWithoutPlugin, plugin, context.ide.build)
        else -> throw ex
      }
    }
  }

  private fun handleTimeout(
    testContext: IDETestContext,
    errorsWithoutPlugin: List<Error>,
    plugin: Plugin,
    ideVersion: String
  ) {
    val runContext = IDERunContext(testContext, launchName = "with-plugin-${plugin.id}")
    val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
    val pluginErrors = subtract(errors, errorsWithoutPlugin).toMutableList()
    TimeoutAnalyzer.analyzeTimeout(runContext)?.let { pluginErrors.add(it) }
    NewInstallerMarketplaceReporter.reportPluginErrors(plugin, pluginErrors, ideVersion)
  }
}

object NewInstallerMarketplaceReporter {

  private val buildUrl = "https://intellij-plugins-performance.teamcity.com/buildConfiguration/" +
                         "PluginPlatformTests_NewInstallerExternalPluginsCheckerTests/" +
                         "${System.getenv("BUILD_NUMBER")}?guest=1"

  fun reportPluginErrors(
    plugin: Plugin,
    errors: List<Error>,
    ideVersion: String
  ) {
    val verificationResult = when {
      errors.isEmpty() -> VerificationResultType.OK
      errors.size == 1 && errors.first().messageText.contains("due to a dialog being shown") -> VerificationResultType.WARNINGS
      else -> VerificationResultType.PROBLEMS
    }

    val url = when {
      verificationResult == VerificationResultType.OK -> buildUrl
      else -> {
        generateSarifReport(plugin, errors, ideVersion)
        "${plugin.id}/sarif.json"
      }
    }

    val verdict = when (verificationResult) {
      VerificationResultType.OK -> "No issues occurred during the IDE run with the plugin installed"
      else -> "${errors.size} ${if (errors.size == 1) "issue" else "issues"} occurred during the IDE run with the plugin installed"
    }

    val message = VerificationMessage(
      verdict = verdict,
      resultType = verificationResult,
      url = url,
      id = null,
      verificationType = "IDE_UPDATE",
      verifierVersion = null,
      ideVersion = ideVersion,
      updateId = plugin.updateId.toInt()
    )

    sendSqsMessage(message)
  }

  private fun generateSarifReport(
    plugin: Plugin,
    errors: List<Error>,
    ideVersion: String
  ) {
    val artifactsLocation = "$buildUrl&buildTab=artifacts#%2Finstall-plugin-test%2Fwith-plugin-${plugin.id}"

    val sarifReport = SarifReport(
      runs = listOf(
        Run(
          tool = Tool(
            driver = Driver(
              name = "IntellijIdeStarter",
              informationUri = "https://github.com/JetBrains/intellij-ide-starter",
              semanticVersion = ideVersion
            )
          ),
          artifacts = listOf(Artifact(Location(artifactsLocation))),
          results = errors.map { error ->
            Result(
              ruleId = error.messageText,
              message = Message(
                text = error.stackTraceContent
              )
            )
          }
        )
      )
    )

    val mapper = jsonMapper {
      addModule(kotlinModule())
      enable(SerializationFeature.INDENT_OUTPUT)
    }

    val artifactsDir = GlobalPaths.instance.artifactsDirectory
    val sarifPath = artifactsDir
      .resolve("sarif-reports/${plugin.id}")
      .createDirectories()
      .resolve("sarif.json")

    logOutput("##teamcity[setParameter name='starter.sarif.reports.path' value='$artifactsDir/sarif-reports']")
    logOutput("##teamcity[progressMessage 'Writing SARIF report for plugin ${plugin.id} to $sarifPath']")
    mapper.writeValue(File(sarifPath.toString()), sarifReport)

    logOutput("##teamcity[setParameter name='starter.upload.sarif' value='true']")

    TeamCityClient.publishTeamCityArtifacts(
      source = sarifPath,
      artifactPath = "install-plugin-test/with-plugin-${plugin.id}",
      artifactName = "sarif.json",
      zipContent = false
    )
  }

  private fun sendSqsMessage(message: VerificationMessage) {
    if (!teamCityIntelliJPerformanceServer.isBuildRunningOnCI) return
    val sqsClient = SqsClientWrapper("https://sqs.eu-west-1.amazonaws.com/046864285642/production__external-services", EU_WEST_1)

    try {
      sqsClient.sendMessage(message)
    } finally {
      sqsClient.closeClient()
    }
  }
}