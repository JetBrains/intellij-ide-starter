// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.report.publisher

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult

interface ReportPublisher {
  /**
   * Publish report only if run ide return result @see com.intellij.ide.starter.runner.IDERunContext.runIDE
   */
  fun publishResult(ideStartResult: IDEStartResult)

  /**
   * Publish report even if error occurred during run ide
   */
  fun publishAfterRun(context: IDETestContext)
}