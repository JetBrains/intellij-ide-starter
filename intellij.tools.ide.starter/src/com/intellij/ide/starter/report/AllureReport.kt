package com.intellij.ide.starter.report

import com.intellij.ide.starter.utils.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.nio.file.Path
import java.util.*


object AllureReport {
  fun reportFailure(testName: String, message: String, detail: String) {
    try {
      val uuid = UUID.randomUUID().toString()
      val result = TestResult()
      result.uuid = uuid
      result.name = testName
      result.status = Status.FAILED
      result.statusDetails = StatusDetails().setMessage(message).setTrace(detail)

      // Run the Allure lifecycle
      Allure.getLifecycle().scheduleTestCase(result)
      Allure.getLifecycle().startTestCase(uuid)
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.name = testName
        it.statusDetails = StatusDetails().setMessage(message).setTrace(detail)
      }
      Allure.getLifecycle().stopTestCase(uuid)
      Allure.getLifecycle().writeTestCase(uuid)
    } catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }

  fun setResultDir(path: Path) {
    System.setProperty("allure.results.directory", path.toString())
  }
}

