package com.intellij.ide.starter.examples

import com.intellij.ide.starter.runner.SetupException
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler

class EapLicenseHelper : TestExecutionExceptionHandler {

  override fun handleTestExecutionException(context: ExtensionContext?, throwable: Throwable?) {
    if (throwable is SetupException) {
      if (throwable.message!!.contains(regex=Regex("EAP build.*expired"))) {
        logOutput("Skipping the test because the EAP build has expired")
        Assumptions.assumeTrue(false)
      } else {
        throw throwable
      }
    }
  }
}
