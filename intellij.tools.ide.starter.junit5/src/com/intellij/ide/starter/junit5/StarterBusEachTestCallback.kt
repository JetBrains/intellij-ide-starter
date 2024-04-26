package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.StarterBusTestPlanListener.Companion.isServerRunning
import com.intellij.ide.starter.runner.SetupException
import com.intellij.tools.ide.starter.bus.EventsBus
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class StarterBusEachTestCallback : BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(context: ExtensionContext?) {
    if (isServerRunning.get()) return
    try {
      EventsBus.startServerProcess()
      isServerRunning.set(true)
    }
    catch (t: Throwable) {
      throw SetupException("Unable to start event bus server", t)
    }
  }

  override fun afterEach(context: ExtensionContext?) {
    EventsBus.unsubscribeAll()
  }
}