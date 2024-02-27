package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestMethod
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * The listener do the following:
 * * Cancels [perTestSupervisorScope] and [testSuiteSupervisorScope]
 * * Provide [CurrentTestMethod]
 *
 */
open class JUnit5Listener : TestExecutionListener {

  override fun testPlanExecutionStarted(testPlan: TestPlan?) {
    logOutput("${this::class.simpleName} triggered on test plan started")

    super.testPlanExecutionStarted(testPlan)
  }

  override fun executionStarted(testIdentifier: TestIdentifier?) {
    super.executionStarted(testIdentifier)
    if (testIdentifier?.isTest != true) {
      return
    }

    if (CIServer.instance.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testIdentifier.displayName}")
        appendLine(GlobalPaths.instance.getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    val methodSource = testIdentifier.source.get() as MethodSource
    di.direct.instance<CurrentTestMethod>().set(TestMethod(
      name = methodSource.methodName,
      declaringClass = methodSource.javaClass.simpleName,
      displayName = testIdentifier.displayName)
    )
  }

  override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
    val testIdentifierName = testIdentifier?.displayName ?: ""
    logOutput("${this::class.simpleName} triggered on execution finished for `$testIdentifierName`")

    if (testIdentifier?.isTest == true) {
      catchAll {
        perTestSupervisorScope.coroutineContext.cancelChildren(CancellationException("Test `$testIdentifierName` execution is finished"))
      }
      StarterBus.LISTENER.unsubscribe()
      ConfigurationStorage.instance().resetToDefault()
    }

    super.executionFinished(testIdentifier, testExecutionResult)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan?) {
    logOutput("${this::class.simpleName} triggered on test plan finished")
    catchAll { testSuiteSupervisorScope.coroutineContext.cancelChildren(CancellationException("Test plan execution is finished")) }
    super.testPlanExecutionFinished(testPlan)
  }
}