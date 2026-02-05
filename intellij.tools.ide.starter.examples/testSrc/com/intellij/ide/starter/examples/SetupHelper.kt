package com.intellij.ide.starter.examples

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useInstaller
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SetupHelper : BeforeEachCallback {

  override fun beforeEach(p0: ExtensionContext?) {
    ConfigurationStorage.useInstaller(true)
  }
}
