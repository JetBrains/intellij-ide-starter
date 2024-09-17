package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useInstaller
import com.intellij.ide.starter.driver.driver.remoteDev.RemoteDevDriverHandler.Companion.rdctVmOptions
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverHandler.Companion.systemProperties
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import kotlin.time.Duration

class IDEBackendHandler(private val backendContext: IDETestContext, private val options: RemoteDevDriverOptions) {
  private fun buildBackendCommandLine(): (IDERunContext) -> IDECommandLine {
    return { _: IDERunContext ->
      if (backendContext.testCase.projectInfo == NoProject) IDECommandLine.Args(listOf("remoteDevHost"))
      else IDECommandLine.OpenTestCaseProject(backendContext, listOf("remoteDevHost"))
    }
  }

  fun run(commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    if (ConfigurationStorage.useInstaller()) {
      ConfigurationStorage.includeRuntimeModuleRepositoryInIde(true)
    }

    applyBackendVMOptionsPatch(options)

    return LocalDriverRunner().runIdeWithDriver(context = backendContext,
                                         commandLine = buildBackendCommandLine(),
                                         commands = commands,
                                         runTimeout = runTimeout,
                                         useStartupScript = useStartupScript,
                                         launchName = launchName,
                                         expectedKill = expectedKill,
                                         expectedExitCode = expectedExitCode,
                                         collectNativeThreads = collectNativeThreads,
                                         configure = configure)
  }


  private fun applyBackendVMOptionsPatch(options: RemoteDevDriverOptions): IDETestContext {
    val context = backendContext
    val vmOptions = context.ide.vmOptions
    vmOptions.configureLoggers(LogLevel.DEBUG, "#com.intellij.remoteDev.downloader.EmbeddedClientLauncher")
    vmOptions.addSystemProperty("rdct.embedded.client.use.custom.paths", true)
    vmOptions.addSystemProperty("rpc.port", options.backendWebServerPort)
    systemProperties(port = options.backendDriverPort).forEach(vmOptions::addSystemProperty)
    rdctVmOptions(options).forEach(vmOptions::addSystemProperty)
    options.backendSystemProperties.forEach(vmOptions::addSystemProperty)
    if (vmOptions.isUnderDebug()) {
      vmOptions.debug(options.backendDebugPort, suspend = false)
    }
    else {
      vmOptions.dropDebug()
    }
    return context
  }}