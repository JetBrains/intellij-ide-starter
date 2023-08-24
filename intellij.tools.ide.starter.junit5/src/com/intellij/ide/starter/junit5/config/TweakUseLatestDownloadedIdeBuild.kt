package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.direct
import org.kodein.di.instance

/**
 * Signals to use locally available build, instead of downloading one.
 */
open class TweakUseLatestDownloadedIdeBuild : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    require(!CIServer.instance.isBuildRunningOnCI) {
      "${StarterConfigurationStorage.ENV_USE_LATEST_DOWNLOADED_IDE_BUILD} should not be used on CI. Downloaded build may not correspond with the changes"
    }
    ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}

