package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.JsonNode
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
import com.intellij.tools.plugin.checker.aws.VerificationMessage
import com.intellij.tools.plugin.checker.aws.VerificationResultType
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.marketplace.MarketplaceClient
import com.intellij.tools.plugin.checker.marketplace.MarketplaceReporter
import com.intellij.tools.plugin.checker.marketplace.Plugin
import com.intellij.tools.plugin.checker.report.StartupFailureAnalyzer
import com.intellij.util.containers.ContainerUtil.subtract
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.IOException
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KillOutdatedProcessesAfterEach::class)
class InstallPluginAfterIdeUpdateTest {
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
      val batchesCount = 50
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
        .newContext(testName = "install-plugin-test", testCase = case)
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

  @Suppress("unused")
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
      IdeUpdateMarketplaceReporter.reportPluginErrors(plugin, diff, "${context.ide.productCode}-${context.ide.build}")
    }
    catch (ex: Exception) {
      when {
        ex is ExecTimeoutException ->
          handleTimeout(contextWithPlugin, errorsWithoutPlugin, plugin, "${context.ide.productCode}-${context.ide.build}")
        ex.message?.contains("failed with code 3") == true ->
          handleStartupFailure(contextWithPlugin, errorsWithoutPlugin, plugin, "${context.ide.productCode}-${context.ide.build}", ex)
        else -> throw ex
      }
    }
  }

  private fun handleTimeout(testContext: IDETestContext, errorsWithoutPlugin: List<Error>, plugin: Plugin, productVersion: String) {
    val runContext = IDERunContext(testContext, launchName = "with-plugin-${plugin.id}")
    val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
    val pluginErrors = subtract(errors, errorsWithoutPlugin).toMutableList()

    TimeoutAnalyzer.analyzeTimeout(runContext)?.also { pluginErrors.add(0, it) }
    IdeUpdateMarketplaceReporter.reportPluginErrors(plugin, pluginErrors, productVersion)
    ErrorReporterToCI.reportErrors(runContext, pluginErrors)
  }

  private fun handleStartupFailure(testContext: IDETestContext, errorsWithoutPlugin: List<Error>, plugin: Plugin, productVersion: String, exception: Exception) {
    val runContext = IDERunContext(testContext, launchName = "with-plugin-${plugin.id}")
    val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
    val pluginErrors = subtract(errors, errorsWithoutPlugin).toMutableList()

    StartupFailureAnalyzer.analyzeStartupFailure(runContext)?.also {
      pluginErrors.add(0, it)
    } ?: throw exception

    IdeUpdateMarketplaceReporter.reportPluginErrors(plugin, pluginErrors, productVersion)
    ErrorReporterToCI.reportErrors(runContext, pluginErrors)
  }
}

object IdeUpdateMarketplaceReporter : MarketplaceReporter() {

  private val buildUrl: String
    get() {
      val buildTypeId = System.getenv("BUILD_TYPE_ID") ?: "PluginPlatformTests_NewInstallerExternalPluginsCheckerTests"
      val buildId = CIServer.instance.asTeamCity().buildId
      return "https://intellij-plugins-performance.teamcity.com/buildConfiguration/$buildTypeId/$buildId?guest=1"
    }

  fun reportPluginErrors(plugin: Plugin, errors: List<Error>, productVersion: String) {
    val verificationResult = classifyErrors(errors)

    val url = when {
      verificationResult == VerificationResultType.OK -> buildUrl
      else -> {
        val sarifPath = GlobalPaths.instance.artifactsDirectory
          .resolve("sarif-reports/${plugin.id}/$productVersion")
          .createDirectories()
          .resolve("sarif.json")

        generateSarifReport(
          errors = errors,
          semanticVersion = productVersion,
          artifactsLocation = "$buildUrl&buildTab=artifacts#%2Finstall-plugin-test%2Fwith-plugin-${plugin.id}",
          sarifPath = sarifPath,
          artifactPath = "install-plugin-test/with-plugin-${plugin.id}"
        )
        "${plugin.id}/$productVersion/sarif.json"
      }
    }

    val message = VerificationMessage(
      verdict = generateVerdict(errors),
      resultType = verificationResult,
      url = url,
      id = null,
      verificationType = "IDE_PERFORMANCE",
      verifierVersion = null,
      ideVersion = productVersion,
      updateId = plugin.updateId.toInt()
    )

    sendSqsMessage(message)
  }
}
