package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.driver.engine.DriverHandler.Companion.systemProperties
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import kotlinx.coroutines.*
import kotlinx.coroutines.async
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LocalDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    val driver = DriverWithDetailedLogging(Driver.create())
    val driverDeferred = CompletableDeferred<Driver>()
    var process: ProcessHandle? = null
    EventsBus.subscribe(driverDeferred) { event: IdeLaunchEvent ->
      if (driverDeferred.isCompleted) return@subscribe

      process = event.ideProcess.toHandle()
      withTimeoutOrNull(3.minutes) {
        while (!driver.isConnected) {
          delay(3.seconds)
        }
        driverDeferred.complete(driver)
      } ?: driverDeferred.completeExceptionally(TimeoutException("Driver couldn't connect"))
    }
    val currentStep = Allure.getLifecycle().currentTestCaseOrStep
    val runResult = GlobalScope.async {
      Allure.getLifecycle().setCurrentTestCase(currentStep.orElse(UUID.randomUUID().toString()))
      try {
        context.runIDE(commandLine,
                       commands,
                       runTimeout,
                       useStartupScript,
                       launchName,
                       expectedKill,
                       expectedExitCode,
                       collectNativeThreads) {
          provideDriverProperties()
          configure()
        }

      }
      catch (e: Throwable) {
        driverDeferred.completeExceptionally(e)
        logError("Error during IDE execution", e)
        throw e
      }
    }
    return runBlocking { BackgroundRun(runResult, driverDeferred.await(), process) }
  }

  private fun IDERunContext.provideDriverProperties() {
    addVMOptionsPatch {
      for (entry in systemProperties(port = 7777)) {
        addSystemProperty(entry.key, entry.value)
      }
    }
  }
}