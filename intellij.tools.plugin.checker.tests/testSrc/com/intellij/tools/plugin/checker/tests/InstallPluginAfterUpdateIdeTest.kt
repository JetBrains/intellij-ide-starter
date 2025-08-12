package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.ci.teamcity.withAuth
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider.IU
import com.intellij.ide.starter.junit5.config.KillOutdatedProcesses
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginNotFoundException
import com.intellij.ide.starter.report.Error
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.marketplace.MarketplaceClient
import com.intellij.tools.plugin.checker.marketplace.Plugin
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
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.minutes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(KillOutdatedProcesses::class)
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
      val batchesCount = 100
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

    val ideRunContext =
      contextWithPlugin.runIDE(launchName = "with-plugin-${plugin.id}", commands = CommandChain().exitApp(), runTimeout = 3.minutes).runContext
    val errorsWithPlugin = ErrorReporterToCI.collectErrors(ideRunContext.logsDir)

    val diff = subtract(errorsWithPlugin, errorsWithoutPlugin).toList()
    ErrorReporterToCI.reportErrors(ideRunContext, diff)
  }
}