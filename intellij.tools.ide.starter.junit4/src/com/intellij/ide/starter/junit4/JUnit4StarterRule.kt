package com.intellij.ide.starter.junit4

import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcesses
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.ide.starter.utils.withIndent
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.kodein.di.direct
import org.kodein.di.instance

fun initJUnit4StarterRule(): JUnit4StarterRule = JUnit4StarterRule()

fun <T : JUnit4StarterRule> T.useInstaller(): T {
  ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_JUNIT_RUNNER_USE_INSTALLER, true)

  return this
}

open class JUnit4StarterRule(
  override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf(),
  override val ciServer: CIServer = di.direct.instance()
) : ExternalResource(), TestContainer<JUnit4StarterRule> {

  override lateinit var testContext: IDETestContext

  private lateinit var testDescription: Description

  override fun apply(base: Statement, description: Description): Statement {
    testDescription = description

    try {
      val testMethod = testDescription.testClass.getMethod(testDescription.methodName)
      di.direct.instance<CurrentTestMethod>().set(testMethod)
    }
    catch (_: Exception) {
      logError("Couldn't acquire test method")
    }

    return super.apply(base, description)
  }

  /**
   * Before each
   */
  override fun before() {
    if (ciServer.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testDescription.displayName}")
        appendLine(GlobalPaths.instance.getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcesses()

    super.before()
  }

  override fun close() {
    catchAll { testContext.paths.close() }
  }

  /**
   * After each
   */
  override fun after() {
    StarterListener.unsubscribe()
    close()
    ConfigurationStorage.instance().resetToDefault()
    super.after()
  }
}

/**
 * Makes the test use the latest available locally IDE build for testing.
 */
fun <T : JUnit4StarterRule> T.useLatestDownloadedIdeBuild(): T = apply {
  assert(!ciServer.isBuildRunningOnCI)
  ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, true)
}
