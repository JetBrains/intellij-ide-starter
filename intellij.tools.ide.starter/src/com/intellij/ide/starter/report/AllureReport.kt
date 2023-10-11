package com.intellij.ide.starter.report

import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer.Companion.processStringForTC
import com.intellij.ide.starter.utils.convertToHashCodeWithOnlyLetters
import com.intellij.ide.starter.utils.generifyErrorMessage
import com.intellij.tools.ide.common.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.Label
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.util.*


object AllureReport {

  fun reportFailure(message: String, stackTrace: String, link: String? = null) {
    try {
      val uuid = UUID.randomUUID().toString()
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
      labels.forEach {
        Allure.label(it.name, it.value)
      }
      if (link != null) {
        Allure.link(link)
      }
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.name = "Exception in $testName"
        it.statusDetails = StatusDetails().setMessage(message).setTrace(stackTrace)
        it.fullName = fullName
        it.testCaseName = testCaseName
        it.historyId = convertToHashCodeWithOnlyLetters(generifyErrorMessage(stackTrace.processStringForTC()).hashCode())
      }
      Allure.getLifecycle().stopTestCase(uuid)
      Allure.getLifecycle().writeTestCase(uuid)
    } catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }
}