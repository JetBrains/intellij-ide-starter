package com.intellij.tools.plugin.checker.tests

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.ci.teamcity.asTeamCity
import com.intellij.ide.starter.ci.teamcity.withAuth
import com.intellij.ide.starter.ide.IdeProductProvider.IU
import com.intellij.ide.starter.junit5.config.KillOutdatedProcesses
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.ide.starter.report.ErrorReporterToCI
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.plugin.checker.data.TestCases
import com.intellij.tools.plugin.checker.di.initPluginCheckerDI
import com.intellij.tools.plugin.checker.marketplace.MarketplaceClient
import com.intellij.tools.plugin.checker.marketplace.Plugin
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.URI
import kotlin.io.path.createFile

@ExtendWith(KillOutdatedProcesses::class)
class InstallPluginAfterUpdateIdeTest {
  private data class ConfigurationData(val type: String, val version: String, val url: String)
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
      initPluginCheckerDI()

      return ConfigurationData(
        propertyNode.getProperty("ide.type"),
        propertyNode.getProperty("ide.version"),
        propertyNode.getProperty("ide.download.url")
      )
    }

    @JvmStatic
    fun pluginsProvider(): List<Arguments> {
      val configurationData = getConfigurationData()
      val case = TestCases.IU.GradleJitPackSimple
        .copy(
          ideInfo = IU.copy(
            buildNumber = configurationData.version,
            downloadURI = URI(configurationData.url)
          )
        )
      val plugins = MarketplaceClient.getPluginsForBuild(configurationData.type, configurationData.version)
      return plugins.map { Arguments.of(case to it, it.name) }
    }
  }

  @ParameterizedTest(name = "Validate plugin {1}")
  @MethodSource("pluginsProvider")
  fun validatePlugin(data: Pair<TestCase<RemoteArchiveProjectInfo>, Plugin>, pluginName: String) {
    val (case, plugin) = data
    val context = Starter.newContext(testName = "install_plugin_${data.second.id}", testCase = case)
    val pluginPath = context.paths.testHome.resolve(plugin.id + ".zip").createFile()
    MarketplaceClient.downloadPlugin(plugin, pluginPath.toFile())
    context.apply { pluginConfigurator.installPluginFromPath(pluginPath) }
    val runContext = context.runIDE(commands = CommandChain().exitApp()).runContext
    val errors = ErrorReporterToCI.collectErrors(runContext.logsDir)
    ErrorReporterToCI.reportErrors(runContext, errors)
  }
}