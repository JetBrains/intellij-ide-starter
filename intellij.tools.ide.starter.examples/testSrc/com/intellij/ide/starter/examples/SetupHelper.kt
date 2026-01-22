package com.intellij.ide.starter.examples

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetupHelper : BeforeAllCallback {

  override fun beforeAll(context: ExtensionContext?) {
    Setup.setInstallersUsage()
  }
}
