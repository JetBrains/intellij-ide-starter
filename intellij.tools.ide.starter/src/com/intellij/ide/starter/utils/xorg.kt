package com.intellij.ide.starter.utils

import com.intellij.ide.starter.process.getProcessList
import com.intellij.tools.ide.util.common.logOutput

fun getRunningDisplays(): List<Int> {
  logOutput("Looking for running displays")
  val fullProcessList = getProcessList()
  val isDisplayArg = { arg: String -> arg.startsWith(":") }
  val found = fullProcessList
    .filter { it.command == "Xvfb" }
    .also { foundCandidates -> logOutput("Found Xvfb processes: ${foundCandidates.joinToString("\n") { it.description }}") }
    .filter { it.arguments?.any(isDisplayArg) == true }
    .map {
      it.arguments?.singleOrNull(isDisplayArg)
        ?.drop(1)
        ?.toIntOrNull()
      ?: error("Cannot parse Xvfb display number from ${it.description}")
    }
  logOutput("Found Xvfb displays: $found")
  if (found.isEmpty()) {
    logOutput("Full process list was: ${fullProcessList.joinToString("\n")}")
  }
  return found
}