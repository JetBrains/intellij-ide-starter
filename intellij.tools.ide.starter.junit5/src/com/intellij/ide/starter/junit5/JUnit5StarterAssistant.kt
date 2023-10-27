package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcesses
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.jvm.javaField

open class JUnit5StarterAssistant : BeforeEachCallback, AfterEachCallback {
  private fun injectTestInfoProperty(context: ExtensionContext) {
    val testInstance = context.testInstance.get()

    val testInfoProperty = TestInstanceReflexer.getProperty(testInstance, TestInfo::class)
    if (testInfoProperty == null) return

    val testInfoInstance = object : TestInfo {
      override fun getDisplayName(): String = context.displayName
      override fun getTags(): MutableSet<String> = context.tags
      override fun getTestClass(): Optional<Class<*>> = context.testClass
      override fun getTestMethod(): Optional<Method> = context.testMethod
    }

    try {
      testInfoProperty.javaField!!.trySetAccessible()

      if (testInfoProperty.javaField!!.get(testInstance) != null) {
        logOutput("Property `${testInfoProperty.name}` already manually initialized in the code")
        return
      }

      testInfoProperty.javaField!!.set(testInstance, testInfoInstance)
    }
    catch (e: Throwable) {
      logError("Unable to inject value for property `${testInfoProperty.name}`")
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testMethod.isPresent) {
      di.direct.instance<CurrentTestMethod>().set(context.testMethod.get())
    }
    else {
      logError("Couldn't acquire test method")
    }

    if (CIServer.instance.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${context.displayName}")
        appendLine(GlobalPaths.instance.getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcesses()
    injectTestInfoProperty(context)
  }

  override fun afterEach(context: ExtensionContext) {
    // TODO: Find a way to wait till all subscribers finished their work
    // https://youtrack.jetbrains.com/issue/AT-18/Simplify-refactor-code-for-starting-IDE-in-IdeRunContext#focus=Comments-27-8300203.0-0
    StarterListener.unsubscribe()
    ConfigurationStorage.instance().resetToDefault()
  }
}



