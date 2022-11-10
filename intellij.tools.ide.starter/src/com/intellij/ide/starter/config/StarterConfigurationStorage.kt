package com.intellij.ide.starter.config

open class StarterConfigurationStorage : ConfigurationStorage() {
  companion object {
    const val ENV_ENABLE_CLASS_FILE_VERIFICATION = "ENABLE_CLASS_FILE_VERIFICATION"

    const val ENV_USE_LATEST_DOWNLOADED_IDE_BUILD = "USE_LATEST_DOWNLOADED_IDE_BUILD"

    fun shouldUseLatestDownloadedIdeBuild() = instance().getBoolean(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD)

    const val ENV_JUNIT_RUNNER_USE_INSTALLER = "JUNIT_RUNNER_USE_INSTALLER"

    fun shouldRunOnInstaller(): Boolean = instance().getBoolean(ENV_JUNIT_RUNNER_USE_INSTALLER)

    /**
     * Starts locally build IDE instead of a downloaded installer
     */
    fun useLocalBuild() = instance().put(ENV_JUNIT_RUNNER_USE_INSTALLER, false)
  }

  override fun resetToDefault() {
    put(ENV_ENABLE_CLASS_FILE_VERIFICATION, System.getenv(ENV_ENABLE_CLASS_FILE_VERIFICATION))
    put(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, System.getenv(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD))
    put(ENV_JUNIT_RUNNER_USE_INSTALLER, System.getenv(ENV_JUNIT_RUNNER_USE_INSTALLER))
  }
}