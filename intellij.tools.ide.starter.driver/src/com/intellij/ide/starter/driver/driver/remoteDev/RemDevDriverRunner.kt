package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.impl.JmxHost
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.DriverRunner
import com.intellij.ide.starter.driver.engine.DriverWithDetailedLogging
import com.intellij.ide.starter.driver.engine.remoteDev.RemDevDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import kotlin.time.Duration

const val REQUIRE_FLUXBOX_VARIABLE = "REQUIRE_FLUXBOX"

class RemDevDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    require(context is IDERemDevTestContext) { "for split-mode context should be instance of ${IDERemDevTestContext::class.java.simpleName}" }

    val remoteDevDriverOptions = RemoteDevDriverOptions()
    val ideBackendHandler = IDEBackendHandler(context, remoteDevDriverOptions)
    val ideFrontendHandler = IDEFrontendHandler(context, remoteDevDriverOptions)

    EventsBus.subscribe(ideFrontendHandler) { event: IdeLaunchEvent ->
      ideFrontendHandler.handleBackendContext(event.runContext)
    }
    val backendRun = ideBackendHandler.run(commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)

    val driverWithLogging = DriverWithDetailedLogging(RemDevDriver(JmxHost(address = "127.0.0.1:${remoteDevDriverOptions.driverPort}")))
    val frontendRun = ideFrontendHandler.runInBackground(launchName)

    return RemoteDevBackgroundRun(frontendRun, backendRun.startResult, backendRun.driver, driverWithLogging, backendRun.process)
  }
}