package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * If used, installed IDE will include [runtime module repository](psi_element://com.intellij.platform.runtime.repository);
 * note that production builds always include it. Works only when IDE is built from sources.
 */
open class IncludeRuntimeModuleRepositoryInInstaller : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    ConfigurationStorage.instance().put(StarterConfigurationStorage.INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY, true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}

