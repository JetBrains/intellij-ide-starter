package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.driver.engine.DriverHandler
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.awaitility.Awaitility.await
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.exists

class IDERemoteClientHandler(private val hostContext: IDETestContext, private val clientContext: IDETestContext) {

  private lateinit var joinLink: String
  private lateinit var hostRunContext: IDERunContext

  private fun runClient(options: RemoteDevDriverOptions, launchName: String): IDEStartResult {
    awaitHostStart()
    joinLink = awaitJoinLink(hostRunContext.logsDir / "idea.log")

    val vmOptions = clientContext.ide.vmOptions
    DriverHandler.systemProperties(port = options.driverPort).forEach(vmOptions::addSystemProperty)
    RemoteDevDriverHandler.rdctVmOptions(options).forEach(vmOptions::addSystemProperty)
    options.systemProperties.forEach(vmOptions::addSystemProperty)
    vmOptions.addSystemProperty("rpc.port", options.webServerPort)
    if (vmOptions.isUnderDebug()) {
      vmOptions.debug(options.debugPort, suspend = false)
    }
    else {
      vmOptions.dropDebug()
    }

    return try {
      clientContext.runIDE(
        commandLine = IDECommandLine.Args(listOf("thinClient", joinLink)),
        commands = CommandChain(),
        runTimeout = options.runTimeout,
        launchName = if (launchName.isEmpty()) "embeddedClient" else "$launchName/embeddedClient")
        .also {
          logOutput("Remote IDE client run ${hostContext.testName} completed")
        }
    }
    catch (e: Throwable) {
      logError("Error during JBClient IDE execution", e)
      throw e
    }
  }

  fun runClientInBackground(options: RemoteDevDriverOptions, launchName: String): Deferred<IDEStartResult> {
    return perTestSupervisorScope.async {
      runClient(options, launchName)
    }
  }

  fun onHostStarted(event: IdeLaunchEvent) {
    hostRunContext = event.runContext
  }

  private fun awaitForLogFile(logFile: Path) {
    await()
      .pollInterval(1, TimeUnit.SECONDS)
      .atMost(30, TimeUnit.SECONDS)
      .until { logFile.exists() }
  }

  private fun awaitHostStart() {
    await()
      .pollInterval(1, TimeUnit.SECONDS)
      .atMost(2, TimeUnit.MINUTES)
      .until { this::hostRunContext.isInitialized }
  }

  private fun awaitJoinLink(logFile: Path): String {
    awaitForLogFile(logFile)
    val linkEntryPrefix = "Join link: tcp"
    var linkEntry: String? = null
    await()
      .pollInterval(1, TimeUnit.SECONDS)
      .atMost(14, TimeUnit.SECONDS)
      .until {
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