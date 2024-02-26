package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class RemoteDevRun : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    ConfigurationStorage.instance().put(StarterConfigurationStorage.REMOTE_DEV_RUN, true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}

