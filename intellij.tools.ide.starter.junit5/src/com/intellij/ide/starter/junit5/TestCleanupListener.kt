package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

/**
 * The listener do the following:
 * * Cancels [perTestSupervisorScope] and [testSuiteSupervisorScope]
 * * Drops all subscriptions to StarterBus
 * * Resets configuration storage
 *
 */
open class TestCleanupListener : TestExecutionListener {

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