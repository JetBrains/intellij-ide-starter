package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.getProcessList
import com.intellij.tools.ide.util.common.logOutput

fun getRunningDisplays(): List<Int> {
  logOutput("Looking for running displays")
  val fullProcessList = getProcessList()
  val found = fullProcessList
    .filter { it.command.contains("Xvfb") }
    .map {
      it.arguments?.singleOrNull { arg -> arg.startsWith(":") }
        ?.drop(1)
        ?.toIntOrNull()
      ?: error("Cannot parse Xvfb display number from ${it.commandLine}")
    }
  logOutput("Found Xvfb displays: $found")
  if (found.isEmpty()) {
    logOutput("Full process list was: ${fullProcessList.joinToString("\n")}")
  }
  return found
}