package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.coroutine.perClassSupervisorScope
import com.intellij.ide.starter.coroutine.perTestSupervisorScope
import com.intellij.ide.starter.coroutine.testSuiteSupervisorScope
import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
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
    if (testIdentifier?.isContainer == true) {
      @Suppress("SSBasedInspection")
      runBlocking {
        catchAll {
          perClassSupervisorScope.coroutineContext.cancelChildren(CancellationException("Test class `$testIdentifierName` execution is finished"))
          perClassSupervisorScope.coroutineContext.job.children.forEach { it.join() }
        }
      }
    }

    if (testIdentifier?.isTest == true) {
      @Suppress("SSBasedInspection")
      runBlocking {
        catchAll {
          perTestSupervisorScope.coroutineContext.cancelChildren(CancellationException("Test `$testIdentifierName` execution is finished"))
          perTestSupervisorScope.coroutineContext.job.children.forEach { it.join() }
        }
      }
      ConfigurationStorage.instance().resetToDefault()
    }

    super.executionFinished(testIdentifier, testExecutionResult)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan?) {
    catchAll { testSuiteSupervisorScope.coroutineContext.cancelChildren(CancellationException("Test plan execution is finished")) }
    super.testPlanExecutionFinished(testPlan)
  }
}