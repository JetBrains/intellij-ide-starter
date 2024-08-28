package com.intellij.ide.starter.config

import com.intellij.ide.starter.ci.CIServer

open class StarterConfigurationStorage : ConfigurationStorage() {
  companion object {
    const val ENV_ENABLE_CLASS_FILE_VERIFICATION = "ENABLE_CLASS_FILE_VERIFICATION"

    const val ENV_USE_LATEST_DOWNLOADED_IDE_BUILD = "USE_LATEST_DOWNLOADED_IDE_BUILD"

    private const val SPLIT_MODE_ENABLED = "SPLIT_MODE_ENABLED"

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

    const val ENV_LOG_ENVIRONMENT_VARIABLES = "LOG_ENVIRONMENT_VARIABLES"

    /** Log env variables produced by [com.intellij.ide.starter.process.exec.ProcessExecutor] */
    fun shouldLogEnvVariables() = instance().getBoolean(ENV_LOG_ENVIRONMENT_VARIABLES)

    /**
     * This flag is supposed to be used only from the test framework/command handlers, not from tests themselves.
     * Tests should know nothing about the environment they are running in and only contain the test scenario.
     */
    fun isSplitMode(): Boolean = instance().getBoolean(SPLIT_MODE_ENABLED)
    fun splitMode(value: Boolean) = instance().put(SPLIT_MODE_ENABLED, value)
  }

  override fun resetToDefault() {
    put(ENV_ENABLE_CLASS_FILE_VERIFICATION, System.getenv(ENV_ENABLE_CLASS_FILE_VERIFICATION))
    put(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, System.getenv(ENV_USE_LATEST_DOWNLOADED_IDE_BUILD))
    put(ENV_JUNIT_RUNNER_USE_INSTALLER, System.getenv(ENV_JUNIT_RUNNER_USE_INSTALLER))
    put(INSTALLER_INCLUDE_RUNTIME_MODULE_REPOSITORY, false)
    put(ENV_LOG_ENVIRONMENT_VARIABLES, CIServer.instance.isBuildRunningOnCI)
    splitMode(System.getenv().getOrDefault("REMOTE_DEV_RUN", "false").toBoolean())
  }
}