package com.intellij.ide.starter.junit5

import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

open class StarterBusCleanUpListener : TestExecutionListener {
  override fun executionStarted(testIdentifier: TestIdentifier?) {
    EventsBus.startServerProcess()
    super.executionStarted(testIdentifier)
  }

  override fun executionFinished(testIdentifier: TestIdentifier?, testExecutionResult: TestExecutionResult?) {
    EventsBus.unsubscribeAll()
    EventsBus.endServerProcess()
    super.executionFinished(testIdentifier, testExecutionResult)
  }
}