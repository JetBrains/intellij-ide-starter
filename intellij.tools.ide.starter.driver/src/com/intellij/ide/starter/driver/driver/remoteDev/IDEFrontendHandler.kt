package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.driver.engine.remoteDev.XorgWindowManagerHandler
import com.intellij.ide.starter.driver.waitForCondition
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
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

class IDEFrontendHandler(private val backendContext: IDETestContext, private val remoteDevDriverOptions: RemoteDevDriverOptions) {

  private val executableFileName = when {
    (SystemInfo.isLinux || SystemInfo.isWindows) && StarterConfigurationStorage.shouldRunOnInstaller() -> "jetbrains_client"
    else -> backendContext.testCase.ideInfo.executableFileName
  }

  private val clientTestCase = backendContext.testCase.copy(ideInfo = backendContext.testCase.ideInfo.copy(
    platformPrefix = "JetBrainsClient",
    executableFileName = executableFileName
  ))

  private val frontendContext = Starter.newTestContainer().createFromExisting(testName = backendContext.testName,
                                                                              testCase = clientTestCase,
                                                                              existingContext = backendContext)

  private fun VMOptions.addDisplayIfNecessary() {
    if (SystemInfo.isLinux && System.getenv("DISPLAY") == null) {
      val displayNum = XorgWindowManagerHandler.provideDisplay()
      withEnv("DISPLAY", ":$displayNum")
      withEnv(REQUIRE_FLUXBOX_VARIABLE, "true")
    }
  }

  private lateinit var backendRunContext: IDERunContext

  fun runInBackground(launchName: String): Deferred<IDEStartResult> {
    awaitBackendStart()
    val joinLink = awaitJoinLink(backendRunContext.logsDir / "idea.log")
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
            logOutput("Remote IDE client run ${backendContext.testName} completed")
          }
      }
      catch (e: Exception) {
        process.completeExceptionally(e)
        logOutput("Exception starting the client. Client is not launched: ${e.message}")
        throw e
      }
    }
    runBlocking { process.await() }
    return result
  }

  fun handleBackendContext(backedRunContext: IDERunContext) {
    backendRunContext = backedRunContext
  }

  private fun awaitForLogFile(logFile: Path) {
    waitForCondition(30.seconds, 1.seconds) { logFile.exists() }
  }

  private fun awaitBackendStart() {
    waitForCondition(2.minutes, 1.seconds) { this::backendRunContext.isInitialized }
  }

  private fun awaitJoinLink(logFile: Path): String {
    awaitForLogFile(logFile)
    val linkEntryPrefix = "Join link: tcp"
    var linkEntry: String? = null
    waitForCondition(14.seconds, 1.seconds) {
      val logLines = Files.readAllLines(logFile)
      val foundEntry = logLines.find { it.contains(linkEntryPrefix) }
      linkEntry = foundEntry
      linkEntry != null
    }
    val match = "tcp://.+".toRegex().find(linkEntry!!)
    if (match?.value == null) {
      error("Couldn't find link from entry: $linkEntry")
    }
    else {
      return match.value
    }
  }
}