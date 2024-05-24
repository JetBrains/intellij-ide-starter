package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getProcessList
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.collections.single
import kotlin.collections.singleOrNull
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.text.drop
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.toInt
import kotlin.time.Duration.Companion.hours

object XorgWindowManagerHandler {

  private val displayNumber = AtomicInteger(10)
  // see LinuxIdeDistribution.linuxCommandLine()
  private val resolution = "1920x1080"

  // region xvfb
  private val xvfbName = "Xvfb"



  private fun getRunningDisplays(): List<Int> {
    logOutput("Looking for running displays")
    val found = getProcessList()
      .filter { it.command.contains(xvfbName) }.map {
        logOutput(it.command)
        it.command.split(" ")
          .single { arg -> arg.startsWith(":") }
          .drop(1)
          .toInt()
      }
    logOutput("Found $xvfbName displays: $found")
    return found
  }

  fun provideDisplay(): Int {
    val displays = getRunningDisplays()
    return displays.singleOrNull() ?: runXvfb()
  }

  private fun runXvfb(): Int {
    val number = displayNumber.getAndIncrement()
    val display = ":$number"
    perTestSupervisorScope.async {
      ProcessExecutor(
        presentableName = "Run $xvfbName",
        timeout = 2.hours,
        args = listOf("/usr/bin/$xvfbName", display, "-ac", "-screen", "0", "${resolution}x24", "-nolisten", "tcp", "-wr"),
        workDir = null,
        stdoutRedirect = ExecOutputRedirect.ToStdOut("[$xvfbName]"),
        stderrRedirect = ExecOutputRedirect.ToStdOut("[$xvfbName-err]")
      ).start()
    }
    logOutput("Started $xvfbName display: $display")
    return number
  }

  // endregion xvfb

  // region ffmpeg

  private val ffmpegName = "ffmpeg"
  fun subscribeToStartRecording() {
    EventsBus.subscribe("subscribeToStartRecording") { ideLaunchEvent: IdeLaunchEvent ->
      perTestSupervisorScope.async {
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
        ).start()
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
      perTestSupervisorScope.async {
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
          ).start()
        }
        else {
          logOutput("$fluxboxName is already running on display $displayWithColumn")
        }
      }
    }
  }
  // endregion Fluxbox
}