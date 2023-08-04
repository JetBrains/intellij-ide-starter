package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.util.SystemProperties
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object ErrorReporter {
  private const val MAX_TEST_NAME_LENGTH = 250
  const val MESSAGE_FILENAME = "message.txt"
  const val STACKTRACE_FILENAME = "stacktrace.txt"
  const val ERRORS_DIR_NAME = "script-errors"

  /**
   * Read files from errors directories, written by performance testing plugin.
   * Report them as an individual failures on CI
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  fun reportErrorsAsFailedTests(rootErrorsDir: Path, runContext: IDERunContext, isRunSuccessful: Boolean) {
    if (!rootErrorsDir.isDirectory()) return
    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return
    val errorsDirectories = rootErrorsDir.listDirectoryEntries()

    for (errorDir in errorsDirectories) {
      val messageFile = errorDir.resolve(MESSAGE_FILENAME).toFile()
      val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME).toFile()

      if (!(messageFile.exists() && stacktraceFile.exists())) continue

      val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())
      val stackTraceContent = stacktraceFile.readText().trimIndent().trim()

      val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTraceContent).hashCode())

      val testName = if (stackTraceContent.startsWith(messageText)) {
        val maxLength = (MAX_TEST_NAME_LENGTH - onlyLettersHash.length).coerceAtMost(stackTraceContent.length)
        val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
        "($onlyLettersHash $extractedTestName)"
      }
      else {
        "($onlyLettersHash ${messageText.substring(0, MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()})"
      }

      val failureDetails = di.direct.instance<FailureDetailsOnCI>()
      val failureDetailsMessage = if (isRunSuccessful) failureDetails.getFailureDetails(runContext)
      else failureDetails.getFailureDetailsForCrash(runContext)

      if (di.direct.instance<CIServer>().isTestFailureShouldBeIgnored(messageText)) {
        di.direct.instance<CIServer>().ignoreTestFailure(testName = generifyErrorMessage(testName),
                                                         message = failureDetailsMessage,
                                                         details = stackTraceContent)
      }
      else {
        di.direct.instance<CIServer>().reportTestFailure(testName = generifyErrorMessage(testName),
                                                         message = failureDetailsMessage,
                                                         details = stackTraceContent)
        AllureReport.reportFailure(messageText,
                                   stackTraceContent,
                                   failureDetails.getLinkToCIArtifacts(runContext, isRunSuccessful))
      }
    }
  }

  fun getLinkToCIArtifacts(runContext: IDERunContext, forCrash: Boolean) : String? {
    val failureDetails = di.direct.instance<FailureDetailsOnCI>()
    return failureDetails.getLinkToCIArtifacts(runContext, forCrash)
  }
}
