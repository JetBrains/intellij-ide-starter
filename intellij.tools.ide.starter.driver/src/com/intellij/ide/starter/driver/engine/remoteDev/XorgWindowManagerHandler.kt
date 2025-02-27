package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getProcessList
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.getRunningDisplays
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.async
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.hours

object XorgWindowManagerHandler {

  fun provideDisplay(): Int {
    return getRunningDisplays().firstOrNull() ?: throw IllegalStateException("No display found")
  }

  // region Fluxbox
  private val fluxboxName = "fluxbox"

  private fun isFluxBoxIsRunning(displayWithColumn: String): Boolean {
    val running = getProcessList()
      .filter { it.command.contains(fluxboxName) }
      .map {
        it.command.split(" ")
          .single { arg -> arg.startsWith(":") }
      }.contains(displayWithColumn)
    logOutput("$fluxboxName is running: $running")
    return running
  }

  private fun verifyFluxBoxInstalled() {
    ProcessExecutor(
      presentableName = "which $fluxboxName",
      args = listOf("which", fluxboxName),
      workDir = null,
      expectedExitCode = 0
    ).start()
  }

  fun startFluxBox(ideRunContext: IDERunContext) {
    perClientSupervisorScope.async {
      val displayWithColumn = ideRunContext.testContext.ide.vmOptions.environmentVariables["DISPLAY"]!!

      if (!isFluxBoxIsRunning(displayWithColumn)) {
        verifyFluxBoxInstalled()
        val fluxboxRunLog = ideRunContext.logsDir / "$fluxboxName.log"
        ProcessExecutor(
          presentableName = "Start $fluxboxName",
          timeout = 2.hours,
          args = listOf("/usr/bin/${fluxboxName}", "-display", displayWithColumn),
          workDir = null,
          stdoutRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog.toFile()),
          stderrRedirect = ExecOutputRedirect.ToFile(fluxboxRunLog.toFile())
        ).startCancellable()
      }
      else {
        logOutput("$fluxboxName is already running on display $displayWithColumn")
      }
    }
  }
  // endregion Fluxbox
}