package com.intellij.ide.starter.report.publisher.impl

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.utils.logOutput

object ConsoleTestResultPublisher : ReportPublisher {

  override fun publishResultOnSuccess(ideStartResult: IDEStartResult) {
    logOutput(ideStartResult)
  }

  override fun publishAnywayAfterRun(context: IDETestContext) {
    logOutput(context)
  }
}