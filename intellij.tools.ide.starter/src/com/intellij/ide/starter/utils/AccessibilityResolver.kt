package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

object AccessibilityResolver {

  private enum class TccCommand(val value: String) {
    LIST("--list"),
    REMOVE("--remove"),
    INSERT("--insert");
  }

  private enum class TccService(val value: String) {
    ACCESSIBILITY("kTCCServiceAccessibility"),
    POST_EVENT("kTCCServicePostEvent"),
    SCREEN_CAPTURE("kTCCServiceScreenCapture"),
    SERVICE_LISTEN("kTCCServiceListenEvent")
  }


  private fun getSystemVersion(): Double {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor("sw_vers", null, stdoutRedirect = stdout, args = listOf("sw_vers", "-productVersion"), timeout = 3.seconds).start()
    return stdout.read().substringBeforeLast(".").toDouble()
  }

  private fun isTccUtilInstalled(): Boolean {
    return Files.exists(Paths.get("/opt/homebrew/bin/tccutil"))
  }

  private fun isSIPDisabled(): Boolean {
    val stdout = ExecOutputRedirect.ToString()
    ProcessExecutor("csrutil", null, stdoutRedirect = stdout, args = listOf("csrutil", "status"), timeout = 3.seconds).start()
    return stdout.read().contains("status: disabled")
  }

  private fun performTccCommand(
    command: TccCommand,
    idePath: String,
    service: TccService = TccService.ACCESSIBILITY
  ) {
    ProcessExecutor("tccutil", null,
                    stdoutRedirect = ExecOutputRedirect.ToStdOut("tccutil"),
                    stderrRedirect = ExecOutputRedirect.ToStdOut("tccutil"),
                    args = listOf("sudo", "/opt/homebrew/bin/tccutil", "-s", service.value, command.value, idePath),
                    timeout = 3.seconds
    ).start()
    when (command) {
      TccCommand.INSERT -> logOutput("Inserted to accessibility list (%s): %s".format(service.value, idePath))
      TccCommand.REMOVE -> logOutput("Removed from accessibility list (%s): %s".format(service.value, idePath))
      TccCommand.LIST -> logOutput("accessibility list reached successfully")
    }
  }


  fun addToList() {
    if (!OS.CURRENT.equals(OS.macOS)) return
    try {
      if (!isTccUtilInstalled()) {
        logOutput("TCC is not installed")
        return
      }
      if (getSystemVersion() >= 10.11 && !isSIPDisabled()) {
        logOutput("SIP is enabled")
        return
      }

      performTccCommand(TccCommand.INSERT, "/opt/homebrew/Cellar/bash/5.2.26/bin/bash")
      performTccCommand(TccCommand.INSERT, "/opt/homebrew/Cellar/bash/5.2.26/bin/bash", TccService.POST_EVENT)
      performTccCommand(TccCommand.INSERT, "/opt/homebrew/Cellar/bash/5.2.26/bin/bash", TccService.SCREEN_CAPTURE)
      performTccCommand(TccCommand.INSERT, "/opt/homebrew/Cellar/bash/5.2.26/bin/bash", TccService.SERVICE_LISTEN)

    }
    catch (e: IOException) {
      logError("Adding to TCC failed")
    }
  }
}