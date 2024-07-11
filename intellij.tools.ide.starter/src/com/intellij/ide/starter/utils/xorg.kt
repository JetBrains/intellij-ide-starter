package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.getProcessList
import com.intellij.tools.ide.util.common.logOutput

fun getRunningDisplays(): List<Int> {
  logOutput("Looking for running displays")
  val fullProcessList = getProcessList()
  val found = fullProcessList
    .filter { it.command.contains("Xvfb") }
    .map {
      logOutput(it.command)
      it.command.split(" ")
        .single { arg -> arg.startsWith(":") }
        .drop(1)
        .toInt()
    }
  logOutput("Found Xvfb displays: $found")
  if (found.isEmpty()) {
    logOutput("Full process list was: ${fullProcessList.map { it.command }.map { !it.startsWith("[") }.joinToString("\n" )}")
  }
  return found
}