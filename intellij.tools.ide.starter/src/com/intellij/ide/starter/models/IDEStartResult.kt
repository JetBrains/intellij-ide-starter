package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.tools.ide.util.common.logOutput
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class IDEStartResult(
  val runContext: IDERunContext,
  val executionTime: Duration = 0.minutes,
  val vmOptionsDiff: VMOptionsDiff? = null,
  val failureError: Throwable? = null,
  /** property is not null for split mode */
  var clientResult: IDEStartResult? = null,
) {
  val context: IDETestContext get() = runContext.testContext

  val extraAttributes: MutableMap<String, String> = mutableMapOf()

  val mainReportAttributes get() = mapOf("execution time" to executionTime.toString())

  private fun logVmOptionDiff(vmOptionsDiff: VMOptionsDiff?) {
    if (vmOptionsDiff != null && !vmOptionsDiff.isEmpty) {
      logOutput("VMOptions were changed:")
      logOutput("new lines:")
      vmOptionsDiff.newLines.forEach { logOutput("  $it") }
      logOutput("removed lines:")
      vmOptionsDiff.missingLines.forEach { logOutput("  $it") }
      logOutput()
    }
  }

  init {
    logVmOptionDiff(vmOptionsDiff)
  }
}