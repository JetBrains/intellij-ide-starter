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
  private fun getWriterField(allureLifecycle: AllureLifecycle): FileSystemResultsWriter? {
    val field: Field?
    var writer: FileSystemResultsWriter? = null
    try {
      val classType: Class<*> = allureLifecycle::class.java
      field = classType.getDeclaredField("writer")
      field.isAccessible = true
      writer = field.get(allureLifecycle) as? FileSystemResultsWriter
    } catch (ex: NoSuchFieldException) {
      println("No such field: " + ex.localizedMessage)
    } catch (ex: IllegalAccessException) {
      println("Cannot access field: " + ex.localizedMessage)
    }
    return writer
  }

  private fun setOutputDirectoryField(fileSystemResultsWriter: FileSystemResultsWriter, newPath: Path) {
    try {
      val classType: Class<*> = fileSystemResultsWriter::class.java
      val field = classType.getDeclaredField("outputDirectory")
      field.isAccessible = true
      field.set(fileSystemResultsWriter, newPath)
    } catch (ex: NoSuchFieldException) {
      println("No such field: " + ex.localizedMessage)
    } catch (ex: IllegalAccessException) {
      println("Cannot access field: " + ex.localizedMessage)
    }
  }

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

  fun setResultDir(path: Path) {
    //Allure does writer initialization too early and there is no way to configure it without setting system properties
    //but we need to configure output directory per test
    getWriterField(Allure.getLifecycle())?.let {
      setOutputDirectoryField(it, path)
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