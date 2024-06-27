package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.getProcessList
import com.intellij.tools.ide.util.common.logOutput

fun getRunningDisplays(): List<Int> {
  logOutput("Looking for running displays")
  val found = getProcessList()
    .filter { it.command.contains("Xvfb") && !it.command.contains("-auth") }.map {
      logOutput(it.command)
      it.command.split(" ")
        .single { arg -> arg.startsWith(":") }
        .drop(1)
        .toInt()
    }
  logOutput("Found Xvfb displays: $found")
  return found
}