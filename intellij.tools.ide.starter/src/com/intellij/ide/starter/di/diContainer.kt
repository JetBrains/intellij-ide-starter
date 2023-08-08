package com.intellij.ide.starter.di

import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.community.PublicIdeDownloader
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.IdeProductImp
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.InstallerGlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.report.publisher.impl.ConsoleTestResultPublisher
import com.intellij.ide.starter.runner.CodeBuilderHost
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.DI
import org.kodein.di.bindArgSet
import org.kodein.di.bindFactory
import org.kodein.di.bindSingleton
import java.net.URI

/**
 * Reinitialize / override bindings for this DI container in your module before executing tests
 * https://docs.kodein.org/kodein-di/7.9/core/bindings.html
 *
 * E.g:
 * ```
 * di = DI {
 *      extend(di)
 *      bindSingleton<GlobalPaths>(overrides = true) { YourImplementationOfPaths() }
 *    }
 * ```
 * */
var di = DI {
  bindSingleton<GlobalPaths> { InstallerGlobalPaths() }
  bindSingleton<CIServer> { NoCIServer }
  bindSingleton<FailureDetailsOnCI> { object : FailureDetailsOnCI {} }
  bindSingleton<CodeInjector> { CodeBuilderHost() }
  bindFactory { testContext: IDETestContext -> PluginConfigurator(testContext) }
  bindSingleton<IdeDownloader> { PublicIdeDownloader }
  bindFactory<IdeInfo, IdeInstallator> { ideInfo -> IdeInstallerFactory().createInstaller(ideInfo) }

  // you can extend DI with frameworks, specific to IDE language stack
  bindArgSet<IDETestContext, Framework>()
  importAll(ideaFrameworksDI)

  // you can extend DI with build tools, specific to IDE language stack
  bindArgSet<IDETestContext, BuildTool>()
  importAll(ideaBuildToolsDI)

  bindSingleton<List<ReportPublisher>> { listOf(ConsoleTestResultPublisher) }
  bindSingleton<IdeProduct> { IdeProductImp }
  bindSingleton<CurrentTestMethod> { CurrentTestMethod }
  bindSingleton<IdeInfoConfigurable> {
    object : IdeInfoConfigurable {
      override fun resetDIToDefaultDownloading() = usePublicIdeDownloader()
    }
  }
  bindSingleton<ConfigurationStorage> { StarterConfigurationStorage() }
  bindSingleton(tag = "teamcity.uri") { URI("https://buildserver.labs.intellij.net").normalize() }
}.apply {
  logOutput("Starter DI was initialized")
}
