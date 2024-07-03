package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.driver.driver.remoteDev.RemoteDevDriverHandler.Companion.rdctVmOptions
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverHandler.Companion.systemProperties
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging
import com.intellij.ide.starter.driver.engine.LocalDriverRunner
import com.intellij.ide.starter.driver.engine.remoteDev.RemDevDriver
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

const val REQUIRE_FLUXBOX_VARIABLE = "REQUIRE_FLUXBOX"

class RemDevDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    val options = RemoteDevDriverOptions()
    context.applyHostVMOptionsPatch(options)

    val fromInstaller = StarterConfigurationStorage.shouldRunOnInstaller()
    if (fromInstaller) {
      ConfigurationStorage.instance().put(StarterConfigurationStorage.INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY, true)
    }

    val clientContext = Starter.newTestContainer().createFromExisting(context.testName,
                                           context.testCase.copy(ideInfo = context.testCase.ideInfo.copy(platformPrefix = "JetBrainsClient", executableFileName = getClientExecutableFileName(context.testCase.ideInfo, fromInstaller))),
                                                                      existingContext = context)
    val ideRemoteClientHandler = IDERemoteClientHandler(context, clientContext)

    val driver = DriverWithDetailedLogging(RemDevDriver(JmxHost(address = "127.0.0.1:${options.driverPort}")))
    val driverDeferred = CompletableDeferred<Driver>()
    EventsBus.subscribe(driverDeferred) { event: IdeLaunchEvent ->
      if (driverDeferred.isCompleted) return@subscribe
      withTimeoutOrNull(3.minutes) {
        ideRemoteClientHandler.onHostStarted(event)
        while (!driver.isConnected) {
          delay(3.seconds)
        }
        driverDeferred.complete(driver)
      } ?: driverDeferred.completeExceptionally(TimeoutException("Driver couldn't connect"))
    }

    val hostRun = LocalDriverRunner().runIdeWithDriver(context, context.buildBackendCommandLine(), commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)
    detectDisplayIfNecessary(clientContext)
    val clientRun = ideRemoteClientHandler.runClientInBackground(options, launchName)

    return runBlocking { RemoteDevBackgroundRun(clientRun, hostRun.startResult, hostRun.driver, driverDeferred.await(), hostRun.process) }
  }

  private fun IDETestContext.buildBackendCommandLine(): (IDERunContext) -> IDECommandLine {
    return { _: IDERunContext ->
      if (this.testCase.projectInfo == NoProject) IDECommandLine.Args(listOf("remoteDevHost"))
      else IDECommandLine.OpenTestCaseProject(this, listOf("remoteDevHost"))
    }
  }

  private fun getClientExecutableFileName(ideInfo: IdeInfo, fromInstaller: Boolean) =
    when {
      (SystemInfo.isLinux || SystemInfo.isWindows) && fromInstaller -> "jetbrains_client"
      else -> ideInfo.executableFileName
    }

  private fun IDETestContext.applyHostVMOptionsPatch(options: RemoteDevDriverOptions): IDETestContext {
    val context = this
    val vmOptions = context.ide.vmOptions
    vmOptions.configureLoggers(LogLevel.DEBUG, "#com.intellij.remoteDev.downloader.EmbeddedClientLauncher")
    vmOptions.addSystemProperty("rdct.embedded.client.use.custom.paths", true)
    vmOptions.addSystemProperty("rpc.port", options.hostWebServerPort)
    systemProperties(port = options.hostDriverPort).forEach(vmOptions::addSystemProperty)
    rdctVmOptions(options).forEach(vmOptions::addSystemProperty)
    options.hostSystemProperties.forEach(vmOptions::addSystemProperty)
    if (vmOptions.isUnderDebug()) {
      vmOptions.debug(options.hostDebugPort, suspend = false)
    }
    else {
      vmOptions.dropDebug()
    }
    return context
  }

  private fun detectDisplayIfNecessary(clientContext: IDETestContext) {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      val clientVmOptions = clientContext.ide.vmOptions
      clientVmOptions.withEnv("DISPLAY", ":$displayNum")
      clientVmOptions.withEnv(REQUIRE_FLUXBOX_VARIABLE, "true")
    }
  }
}