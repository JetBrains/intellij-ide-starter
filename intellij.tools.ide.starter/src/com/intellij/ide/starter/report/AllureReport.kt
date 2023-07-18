package com.intellij.ide.starter.report

import com.intellij.ide.starter.utils.*
import io.qameta.allure.Allure
import io.qameta.allure.model.Label
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*


object AllureReport {

  fun reportFailure(message: String, stackTrace: String, link: String? = null) {
    try {
      val uuid = UUID.randomUUID().toString()
      val result = TestResult()
      result.uuid = uuid
      //inherit labels from the main test case for the exception
      var labels: MutableList<Label> = mutableListOf()
      var testName = ""
      Allure.getLifecycle().updateTestCase {
        labels = it.labels
        testName = it.name
      }

      Allure.getLifecycle().scheduleTestCase(result)
      Allure.getLifecycle().startTestCase(uuid)
      labels.forEach {
        Allure.label(it.name, it.value)
      }
      if (link != null) {
        Allure.link(link)
      }
      Allure.parameter("hash", generateHash(message))
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.name="Exception in $testName"
        it.statusDetails = StatusDetails().setMessage(message).setTrace(stackTrace)
      }
      Allure.getLifecycle().stopTestCase(uuid)
      Allure.getLifecycle().writeTestCase(uuid)
    } catch (e: Exception) {
      logError("Fail to write allure", e)
    }
  }

  private fun generateHash(message: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return Base64.getEncoder().encodeToString(digest.digest(message
      .generifyID()
      .generifyHash()
      .generifyHexCode()
      .generifyNumber()
      .generifyDollarSign()
      .toByteArray(StandardCharsets.UTF_8)))
  }
}