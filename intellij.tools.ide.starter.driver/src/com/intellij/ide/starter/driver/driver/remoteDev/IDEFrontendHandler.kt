package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.driver.waitForCondition
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IDEFrontendHandler(private val ideRemDevTestContext: IDERemDevTestContext, private val remoteDevDriverOptions: RemoteDevDriverOptions) {
  private val frontendContext = ideRemDevTestContext.frontendIDEContext

  private fun VMOptions.addDisplayIfNecessary() {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      withEnv("DISPLAY", ":$displayNum")
      withEnv(REQUIRE_FLUXBOX_VARIABLE, "true")
    }
  }

  private lateinit var backendRunContext: IDERunContext

  private lateinit var backendLogFile: Path
  private var logLinesBeforeBackendStarted = 0

  fun runInBackground(launchName: String): Deferred<IDEStartResult> {
    awaitBackendStart()
    val joinLink = awaitJoinLink()
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
    val result = GlobalScope.async {
      try {
        frontendContext.runIDE(
          commandLine = IDECommandLine.Args(listOf("thinClient", joinLink)),
          commands = CommandChain(),
          runTimeout = remoteDevDriverOptions.runTimeout,
          launchName = if (launchName.isEmpty()) "embeddedClient" else "$launchName/embeddedClient",
          configure = {
            if (frontendContext.ide.vmOptions.environmentVariables[REQUIRE_FLUXBOX_VARIABLE] != null) {
              XorgWindowManagerHandler.startFluxBox(this)
              XorgWindowManagerHandler.startRecording(this)
            }
          })
          .also {
            logOutput("Remote IDE client run ${ideRemDevTestContext.testName} completed")
          }
      }
      catch (e: Exception) {
        process.completeExceptionally(e)
        logOutput("Exception starting the frontend. Frontend is not launched: ${e.message}")
        throw e
      }
    }
    runBlocking { process.await() }
    return result
  }

  fun handleBackendContext(context: IDERunContext) {
    backendRunContext = context
  }

  fun handleBackendBeforeLaunch(context: IDERunContext) {
    backendLogFile = context.logsDir / "idea.log"
    logLinesBeforeBackendStarted = Result.runCatching { Files.readAllLines(backendLogFile).size }.getOrDefault(0)
  }

  private fun awaitForLogFile(logFile: Path) {
    waitForCondition(30.seconds, 1.seconds) { logFile.exists() }
  }

  private fun awaitBackendStart() {
    waitForCondition(2.minutes, 1.seconds) {
      logOutput("awaitBackendStart")
      this::backendRunContext.isInitialized
    }
  }

  private fun awaitJoinLink(): String {
    awaitForLogFile(backendLogFile)
    val linkEntryPrefix = "Join link: tcp"
    var linkEntry: String? = null
    waitForCondition(timeout = 14.seconds, pollInterval = 2.seconds) {
      val logLines = Files.readAllLines(backendLogFile).drop(logLinesBeforeBackendStarted)
      val joinLinkLines = logLines.filter { it.contains(linkEntryPrefix) }.distinct().also { logOutput("Found joinLinks: $it") }
      linkEntry = joinLinkLines.lastOrNull()
      linkEntry != null
    }
    val match = "tcp://.+".toRegex().find(linkEntry!!)
    if (match?.value == null) {
      error("Couldn't find link from entry: $linkEntry")
    }
    else {
      logOutput("joinLink: ${match.value}")
      return match.value
    }
  }
}
