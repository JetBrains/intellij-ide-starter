package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
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
    val remoteDevDriverOptions = RemoteDevDriverOptions()

    val ideBackendHandler = IDEBackendHandler(context, remoteDevDriverOptions)
    val ideFrontendHandler = IDEFrontendHandler(backendContext = context, remoteDevDriverOptions)

    EventsBus.subscribe("waiting backend start") { event: IdeLaunchEvent ->
      ideFrontendHandler.handleBackendContext(event.runContext)
    }
    val backendRun = ideBackendHandler.run(commands, runTimeout, useStartupScript, launchName, expectedKill, expectedExitCode, collectNativeThreads, configure)

    val driver = DriverWithDetailedLogging(RemDevDriver(JmxHost(address = "127.0.0.1:${remoteDevDriverOptions.driverPort}")))
    val driverDeferred = getDriverDeferred(driver)
    val frontendRun = ideFrontendHandler.runInBackground(launchName)

    return runBlocking { RemoteDevBackgroundRun(frontendRun, backendRun.startResult, backendRun.driver, driverDeferred.await(), backendRun.process) }
  }

  private fun getDriverDeferred(driver: DriverWithDetailedLogging): CompletableDeferred<Driver> {
    val driverDeferred = CompletableDeferred<Driver>()
    EventsBus.subscribe("waiting client start") { event: IdeLaunchEvent ->
      if (driverDeferred.isCompleted) return@subscribe
      withTimeoutOrNull(3.minutes) {
        while (!driver.isConnected) {
          delay(3.seconds)
        }
        driverDeferred.complete(driver)
      } ?: driverDeferred.completeExceptionally(TimeoutException("Driver couldn't connect to frontend"))
    }
    return driverDeferred
  }
}