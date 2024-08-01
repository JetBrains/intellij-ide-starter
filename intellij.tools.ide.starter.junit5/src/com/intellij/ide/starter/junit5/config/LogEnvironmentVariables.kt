package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class LogEnvironmentVariables : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    if (CIServer.instance.isBuildRunningOnCI) return

    ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_LOG_ENVIRONMENT_VARIABLES, true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}