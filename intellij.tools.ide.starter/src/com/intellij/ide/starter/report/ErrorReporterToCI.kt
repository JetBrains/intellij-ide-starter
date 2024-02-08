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
import kotlin.io.path.*

object ErrorReporterToCI: ErrorReporter {
  /**
   * Read files from errors directories, written by performance testing plugin and report them as errors.
   * Read threadDumps folders and report them as freezes.
   * Take a look at [com.jetbrains.performancePlugin.ProjectLoaded.reportErrorsFromMessagePool]
   */
  override fun reportErrorsAsFailedTests(runContext: IDERunContext, isRunSuccessful: Boolean) {
    reportErrors(runContext, collectErrors(runContext.logsDir))
  }

  fun collectErrors(logsDir: Path): List<Error> {
    val rootErrorsDir = logsDir / ErrorReporter.ERRORS_DIR_NAME

    if (SystemProperties.getBooleanProperty("DO_NOT_REPORT_ERRORS", false)) return listOf()
    val errors = mutableListOf<Error>()

    if(rootErrorsDir.isDirectory()) {
      val errorsDirectories = rootErrorsDir.listDirectoryEntries()
      for (errorDir in errorsDirectories) {
        val messageFile = errorDir.resolve(MESSAGE_FILENAME).toFile()
        if (!messageFile.exists()) continue

        val messageText = generifyErrorMessage(messageFile.readText().trimIndent().trim())

        val errorType = ErrorType.fromMessage(messageText)
        //we process freezes from threadDumps folders
        if (errorType == ErrorType.ERROR) {
          val stacktraceFile = errorDir.resolve(STACKTRACE_FILENAME).toFile()
          if (!stacktraceFile.exists()) continue
          val stackTrace = stacktraceFile.readText().trimIndent().trim()
          errors.add(Error(messageText, stackTrace, "", errorType))
        }
      }
    }
    val freezes = collectFreezes(logsDir)
    errors.addAll(freezes)
    return errors
  }

  private fun collectFreezes(logDir: Path): List<Error> {
    val freezes = mutableListOf<Error>()
    Files.list(logDir).use { paths ->
      paths.filter { path ->
        Files.isDirectory(path) && path.fileName.toString().startsWith("threadDumps-freeze")
      }.forEach { path ->
        Files.list(path).use { files ->
          files.filter { it.name.startsWith("threadDump") }
            .findFirst()
            .ifPresent { threadDump ->
              //threadDumps-freeze-20240206-155640-IU-241.11817 => not matching
              //threadDumps-freeze-20240206-155640-IU-241.11817-JBIterator.peekNext-5sec => matching, fallbackName = JBIterator.peekNext
              val nameParts = path.name.split("-")
              val dumpContent = Files.readString(threadDump)
              val fallbackName = "Not analyzed freeze: " + if(nameParts.size == 8) nameParts[7] else inferFallbackNameFromThreadDump(dumpContent)
              freezes.add(Error(fallbackName, "", dumpContent, ErrorType.FREEZE))
            }
        }
      }
    }
    return freezes
  }

  /**
   * Takes the first line that looks like at com.intellij.util.containers.JBIterator.peekNext(JBIterator.java:132)
   * @return className.methodName (e.g., JBIterator.peekNext)
   */
  private fun inferFallbackNameFromThreadDump(dumpContent: String): String {
    val regex = Regex("at (.*)\\(.*:\\d+\\)")
    dumpContent.lineSequence()
      .mapNotNull { line ->
        regex.find(line.trim())?.let { match ->
          match.groupValues[1].split(".").takeLast(2).joinToString(".")
        }
      }
      .firstOrNull()?.let { return it }

    throw Exception("Thread dump file without methods!")
  }

  fun reportErrors(runContext: IDERunContext, errors: List<Error>) {
    for (error in errors) {
      val messageText = error.messageText

      var testName = ""
      var stackTraceContent = ""
      if(error.type == ErrorType.ERROR) {
        stackTraceContent = error.stackTraceContent
        val onlyLettersHash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTraceContent).hashCode())
        testName = if (stackTraceContent.startsWith(messageText)) {
          val maxLength = (ErrorReporter.MAX_TEST_NAME_LENGTH - onlyLettersHash.length).coerceAtMost(stackTraceContent.length)
          val extractedTestName = stackTraceContent.substring(0, maxLength).trim()
          "($onlyLettersHash $extractedTestName)"
        }
        else {
          "($onlyLettersHash ${messageText.substring(0, ErrorReporter.MAX_TEST_NAME_LENGTH.coerceAtMost(messageText.length)).trim()})"
        }
      }
      if(error.type == ErrorType.FREEZE) {
        testName = messageText
        stackTraceContent = ""
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

