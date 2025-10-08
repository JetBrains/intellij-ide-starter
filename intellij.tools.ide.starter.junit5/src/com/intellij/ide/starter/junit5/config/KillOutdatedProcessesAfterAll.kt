package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.killOutdatedProcesses
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class KillOutdatedProcessesAfterAll : AfterAllCallback {
  override fun afterAll(context: ExtensionContext) {
    killOutdatedProcesses()
  }
}



