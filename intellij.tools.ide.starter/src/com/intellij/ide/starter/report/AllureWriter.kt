package com.intellij.ide.starter.report

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import io.qameta.allure.AllureResultsWriter
import io.qameta.allure.FileSystemResultsWriter
import io.qameta.allure.model.TestResult
import io.qameta.allure.model.TestResultContainer
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.div

object AllureWriter: AllureResultsWriter {
  var path: Path = di.direct.instance<GlobalPaths>().testHomePath / "tmp" / "allure"
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