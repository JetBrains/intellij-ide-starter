package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.direct
import org.kodein.di.instance

open class TweakEnableClassFileVerification : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_ENABLE_CLASS_FILE_VERIFICATION, true)
  }
}

