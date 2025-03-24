package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging
import com.intellij.ide.starter.driver.engine.remoteDev.RemDevDriver
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.time.Duration

class RemDevDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    require(context is IDERemDevTestContext) { "for split-mode context should be instance of ${IDERemDevTestContext::class.java.simpleName}" }

    addConsoleAllAppender()

    val remoteDevDriverOptions = RemoteDevDriverOptions()
    context.addRemoteDevSpecificTraces()
    val ideBackendHandler = IDEBackendHandler(context, remoteDevDriverOptions)
    val ideFrontendHandler = IDEFrontendHandler(context, remoteDevDriverOptions)

    EventsBus.subscribe(ideFrontendHandler) { event: IdeBeforeLaunchEvent ->
      logOutput("process IdeBeforeLaunchEvent: ${event.runContext}")
      ideFrontendHandler.handleBackendBeforeLaunch(event.runContext)
    }

    val backendRun = ideBackendHandler.run(commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)

    val driverWithLogging = DriverWithDetailedLogging(RemDevDriver(JmxHost(address = "127.0.0.1:${remoteDevDriverOptions.driverPort}")))
    val frontendRun = ideFrontendHandler.runInBackground(launchName, runTimeout = runTimeout)

    return RemoteDevBackgroundRun(backendRun = backendRun,
                                  frontendProcess = frontendRun.second,
                                  frontendDriver = driverWithLogging,
                                  frontendStartResult = frontendRun.first)
  }

  private fun IDERemDevTestContext.addRemoteDevSpecificTraces() {
    applyVMOptionsPatch {
      configureLoggers(LogLevel.TRACE, "jb.focus.requests")
    }
  }

  companion object {
    private val consoleAppender = ConsoleHandler().apply {
      formatter = IdeaLogRecordFormatter()
    }
  }

  private fun addConsoleAllAppender() {
    Logger.getInstance("") // force to initialize logger model
    val root = java.util.logging.Logger.getLogger("")
    val oldConsoleHandler = root.handlers.find { it is ConsoleHandler }
    if (oldConsoleHandler != null) {
      root.removeHandler(oldConsoleHandler)
    }
    // change to All for local debug
    root.level = Level.INFO
    root.addHandler(consoleAppender)
  }
}