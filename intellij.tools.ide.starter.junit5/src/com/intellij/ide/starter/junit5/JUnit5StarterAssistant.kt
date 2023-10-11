package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcesses
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContainerImpl
import com.intellij.tools.ide.common.logError
import com.intellij.tools.ide.common.logOutput
import com.intellij.ide.starter.utils.withIndent
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
  open fun injectTestContainerProperty(testInstance: Any) {
    val containerProp = TestInstanceReflexer.getProperty(testInstance, TestContainerImpl::class)

    if (containerProp == null) return
    val containerInstance = TestContainerImpl()

    try {
      containerProp.javaField!!.trySetAccessible()

      if (containerProp.javaField!!.get(testInstance) != null) {
        logOutput("Property `${containerProp.name}` already manually initialized in the code")
        return
      }

      containerProp.javaField!!.set(testInstance, containerInstance)
    }
    catch (e: Throwable) {
      logError("Unable to inject value for property `${containerProp.name}`")
    }
  }

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

    val testInstance = context.testInstance.get()

    injectTestContainerProperty(testInstance)
    injectTestInfoProperty(context)
  }

  protected inline fun <reified T : TestContainer<T>> closeResourcesOfTestContainer(context: ExtensionContext) {
    val testInstance = context.testInstance.get()
    val containerProp = TestInstanceReflexer.getProperty(testInstance, T::class)

    if (containerProp != null) {
      try {
        (containerProp.javaField!!.apply { trySetAccessible() }.get(testInstance) as T).close()
      }
      catch (e: Throwable) {
        logError("Unable close resources of ${containerProp.name}")
      }
    }
  }

  override fun afterEach(context: ExtensionContext) {
    StarterListener.unsubscribe()

    closeResourcesOfTestContainer<TestContainerImpl>(context)

    ConfigurationStorage.instance().resetToDefault()
  }
}



