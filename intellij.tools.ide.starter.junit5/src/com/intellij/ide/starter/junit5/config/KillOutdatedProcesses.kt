package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.process.killOutdatedProcesses
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class KillOutdatedProcesses : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    killOutdatedProcesses()
  }
}



