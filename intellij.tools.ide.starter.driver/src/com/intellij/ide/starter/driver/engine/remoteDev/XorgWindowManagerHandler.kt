package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getProcessList
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.utils.getRunningDisplays
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.async
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.hours

object XorgWindowManagerHandler {

  // see LinuxIdeDistribution.linuxCommandLine()
  private val resolution = "1920x1080"

  fun provideDisplay(): Int {
    return getRunningDisplays().firstOrNull() ?: throw IllegalStateException("No display found")
  }

  // region ffmpeg

  fun subscribeToStartRecording() {
    val ffmpegName = "ffmpeg"
    EventsBus.subscribe("subscribeToStartRecording") { ideLaunchEvent: IdeLaunchEvent ->
      perClientSupervisorScope.async {
        val ideRunContext = ideLaunchEvent.runContext
        val displayWithColumn = ideRunContext.testContext.ide.vmOptions.environmentVariables["DISPLAY"]!!
        val recordingFile = ideRunContext.logsDir / "screen.mkv"
        val ffmpegLogFile = ideRunContext.logsDir / "$ffmpegName.log"
        ProcessExecutor(
          presentableName = "Start screen recording",
          timeout = 2.hours,
          args = listOf("/usr/bin/$ffmpegName", "-f", "x11grab", "-video_size", resolution, "-framerate", "3", "-i",
                        displayWithColumn,
                        "-codec:v", "libx264", "-preset", "superfast", recordingFile.pathString),
          workDir = null,
          stdoutRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile()),
          stderrRedirect = ExecOutputRedirect.ToFile(ffmpegLogFile.toFile())
        ).startCancellable()
      }
    }
  }
  // endregion ffmpeg

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

  fun subscribeToStartFluxBox() {
    EventsBus.subscribe("subscribeToStartFluxBox") { ideLaunchEvent: IdeLaunchEvent ->
      perClientSupervisorScope.async {
        val ideRunContext = ideLaunchEvent.runContext
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
  }
  // endregion Fluxbox
}