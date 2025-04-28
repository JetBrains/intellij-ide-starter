package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.coroutine.perClassSupervisorScope
import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.driver.engine.IDEHandle
import com.intellij.ide.starter.driver.engine.IDEProcessHandle
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.*
import kotlin.time.Duration

internal class IDEFrontendHandler(private val ideRemDevTestContext: IDERemDevTestContext, private val remoteDevDriverOptions: RemoteDevDriverOptions) {
  private val frontendContext = ideRemDevTestContext.frontendIDEContext

  private fun VMOptions.addDisplayIfNecessary() {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      withEnv("DISPLAY", ":$displayNum")
    }
  }

  fun runInBackground(launchName: String, joinLink: String, runTimeout: Duration = remoteDevDriverOptions.runTimeout): Pair<Deferred<IDEStartResult>, IDEHandle> {
    frontendContext.ide.vmOptions.let {
      //setup xDisplay
      it.addDisplayIfNecessary()

      //add driver related vmOptions
      DriverHandler.systemProperties(port = remoteDevDriverOptions.driverPort).forEach(it::addSystemProperty)
      RemoteDevDriverHandler.rdctVmOptions(remoteDevDriverOptions).forEach(it::addSystemProperty)

      //add system properties from test
      remoteDevDriverOptions.systemProperties.forEach(it::addSystemProperty)

      it.addSystemProperty("rpc.port", remoteDevDriverOptions.webServerPort)
      if (it.isUnderDebug()) {
        it.debug(remoteDevDriverOptions.debugPort, suspend = false)
      }
      else {
        it.dropDebug()
      }
    }
    val process = CompletableDeferred<ProcessHandle>()
    EventsBus.subscribe(process) { event: IdeLaunchEvent ->
      process.complete(event.ideProcess.toHandle())
    }
    val result = perClassSupervisorScope.async {
      try {
        frontendContext.runIdeSuspending (
          commandLine = IDECommandLine.Args(listOf("thinClient", joinLink)),
          commands = CommandChain(),
          runTimeout = runTimeout,
          launchName = if (launchName.isEmpty()) "embeddedClient" else "$launchName/embeddedClient",
          configure = {
            if (System.getenv("DISPLAY") == null && frontendContext.ide.vmOptions.environmentVariables["DISPLAY"] != null && SystemInfo.isLinux) {
              // It means the ide will be started on a new display, so we need to add win manager
              XorgWindowManagerHandler.startFluxBox(this)
            }
            withScreenRecording()
          })
          .also {
            logOutput("Remote IDE Frontend run ${ideRemDevTestContext.testName} completed")
          }
      }
      catch (e: Exception) {
        process.completeExceptionally(e)
        logOutput("Exception starting the frontend. Frontend is not launched: ${e.message}")
        throw e
      }
    }
    return Pair(result, runBlocking { IDEProcessHandle(process.await()) })
  }
}
