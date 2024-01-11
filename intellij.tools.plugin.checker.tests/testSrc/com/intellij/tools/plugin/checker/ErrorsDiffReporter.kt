package com.intellij.tools.plugin.checker

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.report.AllureReport
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.util.SystemProperties
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object ErrorsDiffReporter {
  fun collectErrors(rootErrorsDir: Path): List<Error> {
    if (!rootErrorsDir.isDirectory()) return emptyList()
    val errorsDirectories = rootErrorsDir.listDirectoryEntries()

    val errors = mutableListOf<Error>()

    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(ErrorReporter.MESSAGE_FILENAME).toFile()
      val stacktraceFile = errorDir.resolve(ErrorReporter.STACKTRACE_FILENAME).toFile()

      if (!(messageFile.exists() && stacktraceFile.exists())) continue

      val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())
      val stackTraceContent = stacktraceFile.readText().trimIndent().trim()

      errors.add(Error(messageText, stackTraceContent))
    }

    return errors
  }

  fun reportErrors(runContext: IDERunContext, errors: List<Error>) {
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return
    for (error in errors) {
      val messageText = error.messageText
      val stackTraceContent = error.stackTraceContent
      val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(error.stackTraceContent).hashCode())

      val testName = if (error.stackTraceContent.startsWith(error.messageText)) {
        val maxLength = (ErrorReporter.MAX_TEST_NAME_LENGTH - onlyLettersHash.length).coerceAtMost(stackTraceContent.length)
        val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
        "($onlyLettersHash $extractedTestName)"
      }
      else {
        "($onlyLettersHash ${messageText.substring(0, ErrorReporter.MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()})"
      }

      val failureDetailsProvider = FailureDetailsOnCI.instance
      val failureDetailsMessage = failureDetailsProvider.getFailureDetails(runContext)

      if (CIServer.instance.isTestFailureShouldBeIgnored(messageText)) {
        CIServer.instance.ignoreTestFailure(testName = generifyErrorMessage(testName),
                                            message = failureDetailsMessage,
                                            details = stackTraceContent)
      }
      else {
        CIServer.instance.reportTestFailure(testName = generifyErrorMessage(testName),
                                            message = failureDetailsMessage,
                                            details = stackTraceContent)
        AllureReport.reportFailure(messageText,
                                   stackTraceContent,
                                   failureDetailsProvider.getLinkToCIArtifacts(runContext))
      }
    }
  }
}

data class Error(val messageText: String, val stackTraceContent: String) {
  private val generifiedStackTraceContent: String = generifyErrorMessage(stackTraceContent)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Error) return false

    return messageText == other.messageText && generifiedStackTraceContent == other.generifiedStackTraceContent
  }

  override fun hashCode(): Int {
    var result = messageText.hashCode()
    result = 31 * result + generifiedStackTraceContent.hashCode()
    return result
  }
}
