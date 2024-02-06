package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.report.ErrorReporter.Companion.MESSAGE_FILENAME
import com.intellij.ide.starter.report.ErrorReporter.Companion.STACKTRACE_FILENAME
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.jvm.optionals.getOrNull

object ErrorReporterToCI: ErrorReporter {
  /**
   * Read files from errors directories, written by performance testing plugin.
   * Report them as an individual failures on CI
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  override fun reportErrorsAsFailedTests(rootErrorsDir: Path, runContext: IDERunContext, isRunSuccessful: Boolean) {
    reportErrors(runContext, collectErrors(rootErrorsDir))
  }

  fun collectErrors(rootErrorsDir: Path): List<Error> {
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    if (!rootErrorsDir.isDirectory()) return listOf()

    val errorsDirectories = rootErrorsDir.listDirectoryEntries()
    val errors = mutableListOf<Error>()

    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(MESSAGE_FILENAME).toFile()
      if (!messageFile.exists()) continue

      val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())

      val errorType = ErrorType.fromMessage(messageText)
      val stackTraceContent = if (errorType == ErrorType.FREEZE) {
        val dump = Files.list(errorDir).filter { it.name.contains("dump")}.findFirst().getOrNull()
        if(dump == null || !dump.exists()) continue
        dump.toFile().readText()
      } else {
        val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME).toFile()
        if(!stacktraceFile.exists()) continue
        stacktraceFile.readText().trimIndent().trim()
      }
      errors.add(Error(messageText, stackTraceContent, errorType))
    }

    return errors
  }

  fun reportErrors(runContext: IDERunContext, errors: List<Error>) {
    for (error in errors) {
      val messageText = error.messageText
      val stackTraceContent = error.stackTraceContent

      val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTraceContent).hashCode())

      val testName = if (stackTraceContent.startsWith(messageText)) {
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
        AllureReport.reportFailure(runContext.contextName, messageText,
                                   stackTraceContent,
                                   failureDetailsProvider.getLinkToCIArtifacts(runContext))
      }
    }
  }
}

