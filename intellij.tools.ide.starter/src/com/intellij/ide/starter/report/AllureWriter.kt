package com.intellij.ide.starter.report

import io.qameta.allure.AllureResultsWriter
import io.qameta.allure.FileSystemResultsWriter
import io.qameta.allure.model.TestResult
import io.qameta.allure.model.TestResultContainer
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths

object AllureWriter: AllureResultsWriter {
  var path: Path = Paths.get("")
  override fun write(testResult: TestResult?) {
    FileSystemResultsWriter(path).write(testResult)
  }

  override fun write(testResultContainer: TestResultContainer?) {
    FileSystemResultsWriter(path).write(testResultContainer)
  }

  override fun write(source: String?, attachment: InputStream?) {
    FileSystemResultsWriter(path).write(source, attachment)
  }
}