package com.intellij.ide.starter.report

import com.intellij.ide.starter.utils.*
import io.qameta.allure.Allure
import io.qameta.allure.AllureLifecycle
import io.qameta.allure.FileSystemResultsWriter
import io.qameta.allure.model.Status
import io.qameta.allure.model.StatusDetails
import io.qameta.allure.model.TestResult
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*


object AllureReport {

  fun reportFailure(testName: String, launchName: String, message: String, stackTrace: String, link: String? = null) {
    try {
      val uuid = UUID.randomUUID().toString()
      val result = TestResult()
      result.uuid = uuid
      Allure.getLifecycle().scheduleTestCase(result)
      Allure.getLifecycle().startTestCase(uuid)
      if (link != null) {
        Allure.link(link)
      }
      Allure.label("testName", testName)
      Allure.label("launchName", launchName)
      Allure.parameter("hash", generateHash(message))
      val className = testName.substringBeforeLast(".")
      val methodName = testName.substringAfterLast(".") + "()"
      Allure.getLifecycle().updateTestCase {
        it.status = Status.FAILED
        it.fullName = testName
        it.name="Exception in $methodName"
        it.testCaseId = "[engine:junit-jupiter]/[class:$className]/[method:$methodName]"
        it.testCaseName= methodName
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