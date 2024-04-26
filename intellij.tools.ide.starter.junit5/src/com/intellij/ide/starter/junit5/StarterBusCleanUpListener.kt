package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.SetupException
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

open class StarterBusCleanUpListener : TestExecutionListener {

  override fun testPlanExecutionStarted(testPlan: TestPlan?) {
    try {
      EventsBus.startServerProcess()
    } catch (t: Throwable) {
      throw SetupException("Unable to start event bus server", t)
    }
    super.testPlanExecutionStarted(testPlan)
  }

  override fun testPlanExecutionFinished(testPlan: TestPlan?) {
    EventsBus.endServerProcess()
    super.testPlanExecutionFinished(testPlan)
  }

  override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
    EventsBus.unsubscribeAll()
    super.executionFinished(testIdentifier, testExecutionResult)
  }
}