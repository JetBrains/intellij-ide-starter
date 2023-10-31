package com.intellij.ide.starter.junit4

import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcesses
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
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
) : ExternalResource(), TestContainer<JUnit4StarterRule> {

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
    if (CIServer.instance.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testDescription.displayName}")
        appendLine(GlobalPaths.instance.getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcesses()

    super.before()
  }

  /**
   * After each
   */
  override fun after() {
    // TODO: Find a way to wait till all subscribers finished their work
    // https://youtrack.jetbrains.com/issue/AT-18/Simplify-refactor-code-for-starting-IDE-in-IdeRunContext#focus=Comments-27-8300203.0-0
    StarterBus.LISTENER.unsubscribe()
    ConfigurationStorage.instance().resetToDefault()
    super.after()
  }
}

/**
 * Makes the test use the latest available locally IDE build for testing.
 */
fun <T : JUnit4StarterRule> T.useLatestDownloadedIdeBuild(): T = apply {
  assert(!CIServer.instance.isBuildRunningOnCI)
  ConfigurationStorage.instance().put(StarterConfigurationStorage.ENV_USE_LATEST_DOWNLOADED_IDE_BUILD, true)
}
