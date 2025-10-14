package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.killOutdatedProcesses
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class KillOutdatedProcesses : BeforeAllCallback, AfterAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    killOutdatedProcesses()
  }
  
  override fun afterAll(context: ExtensionContext) {
    killOutdatedProcesses(reportErrors = true)
  }
}



