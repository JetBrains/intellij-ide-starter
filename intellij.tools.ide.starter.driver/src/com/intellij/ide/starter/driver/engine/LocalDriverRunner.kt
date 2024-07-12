package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.ide.starter.driver.engine.DriverHandler.Companion.systemProperties
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import io.qameta.allure.Allure
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.log
import kotlin.time.Duration

class LocalDriverRunner : DriverRunner {
  override fun runIdeWithDriver(context: IDETestContext, commandLine: (IDERunContext) -> IDECommandLine, commands: Iterable<MarshallableCommand>, runTimeout: Duration, useStartupScript: Boolean, launchName: String, expectedKill: Boolean, expectedExitCode: Int, collectNativeThreads: Boolean, configure: IDERunContext.() -> Unit): BackgroundRun {
    val driver = DriverWithDetailedLogging(Driver.create())
    val currentStep = Allure.getLifecycle().currentTestCaseOrStep
    val process = CompletableDeferred<ProcessHandle>()
    EventsBus.subscribe(process) { event: IdeLaunchEvent ->
      process.complete(event.ideProcess.toHandle())
    }
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
        logError("Error during IDE execution", e)
        process.completeExceptionally(e)
        throw e
      }
    }
    return runBlocking { BackgroundRun (runResult, driver, process.await()) }
  }

  private fun IDERunContext.provideDriverProperties() {
    addVMOptionsPatch {
      for (entry in systemProperties(port = 7777)) {
        addSystemProperty(entry.key, entry.value)
      }
    }
  }
}