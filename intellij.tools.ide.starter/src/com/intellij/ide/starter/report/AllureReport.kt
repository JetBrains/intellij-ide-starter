package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer.Companion.processStringForTC
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.Label
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.util.*


object AllureReport {

  private val ignoreLabels = setOf("layer", "AS_ID")

  fun reportFailure(contextName: String, message: String, originalStackTrace: String, link: String? = null, suffix: String = "Exception") {
    try {
      val uuid = UUID.randomUUID().toString()
      val stackTrace = "${originalStackTrace}${System.lineSeparator().repeat(2)}ContextName: ${contextName}${System.lineSeparator()}TestName: ${CurrentTestMethod.get()?.fullName()}"
      val result = TestResult()
      result.uuid = uuid
      //inherit labels from the main test case for the exception
      var labels: List<Label> = mutableListOf()

      var testName = ""
      var fullName = ""
      var testCaseName = ""
      Allure.getLifecycle().updateTestCase {
        labels = it?.labels.orEmpty()
        testName = it?.name.orEmpty()
        fullName = it?.fullName.orEmpty()
        testCaseName = it?.testCaseName.orEmpty()
      }
      Allure.getLifecycle().scheduleTestCase(result)
      Allure.getLifecycle().startTestCase(uuid)
      labels.filter { label -> !ignoreLabels.contains(label.name) }.forEach {
        Allure.label(it.name, it.value)
      }
      if (link != null) {
        Allure.link("CI server", link)
      }
      Allure.label("layer", "Exception")
      val hash = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTrace.processStringForTC()).hashCode())
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.name = "$suffix in ${testName.ifBlank { contextName }}"
        it.statusDetails = StatusDetails().setMessage(message).setTrace(stackTrace)
        it.fullName = fullName.ifBlank { contextName } + ".${hash}" + ".${suffix.lowercase()}"
        it.testCaseName = testCaseName
        it.historyId = hash
      }
      Allure.getLifecycle().stopTestCase(uuid)
      Allure.getLifecycle().writeTestCase(uuid)
    }
    catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }
}