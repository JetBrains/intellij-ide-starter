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

    /**
     * Set to `true` if it's needed to include [runtime module repository](psi_element://com.intellij.platform.runtime.repository)
     * in the installed IDE. This is currently required to run JetBrains Client from an IDE installation.
     * Works only when IDE is built from sources.
     */
    const val INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY = "INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY"

    /**
     *  Is it needed to include [runtime module repository](psi_element://com.intellij.platform.runtime.repository) in the installed IDE?
     */
    fun shouldIncludeRuntimeModuleRepositoryInIde(): Boolean = instance().getBoolean(INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY)

    const val LINUX_IGNORE_XVFB_RUN = "LINUX_IGNORE_XVFB_RUN"

    fun shouldIgnoreXvfbRun(): Boolean = instance().getBoolean(LINUX_IGNORE_XVFB_RUN)

    const val REMOTE_DEV_RUN = "REMOTE_DEV_RUN"

    fun isRemoteDevRun(): Boolean = instance().getBoolean(REMOTE_DEV_RUN)
  }

  override fun resetToDefault() {
    put(ENV_ENABLE_CLASS_FILE_VERIFICATION, System.getenv(ENV_ENABLE_CLASS_FILE_VERIFICATION))
    put(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, System.getenv(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD))
    put(ENV_JUNIT_RUNNER_USE_INSTALLER, System.getenv(ENV_JUNIT_RUNNER_USE_INSTALLER))
    put(INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY, false)
    put(LINUX_IGNORE_XVFB_RUN, false)
    put(REMOTE_DEV_RUN, System.getenv(REMOTE_DEV_RUN))
  }
}