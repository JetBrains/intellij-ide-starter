package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcesses
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.withIndent
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.kodein.di.direct
import org.kodein.di.instance

open class JUnit5StarterAssistant : BeforeEachCallback, AfterEachCallback, AfterAllCallback {
  override fun beforeEach(context: ExtensionContext) {
    if (context.testMethod.isPresent) {
      di.direct.instance<CurrentTestMethod>().set(context.testMethod.get())
    }
    else {
      logError("Couldn't acquire test method")
    }

    if (CIServer.instance.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${context.displayName}")
        appendLine(GlobalPaths.instance.getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcesses()
  }

  override fun afterEach(context: ExtensionContext) {
    ConfigurationStorage.instance().resetToDefault()

    // TODO: Find a way to wait till all subscribers finished their work
    // https://youtrack.jetbrains.com/issue/AT-18/Simplify-refactor-code-for-starting-IDE-in-IdeRunContext#focus=Comments-27-8300203.0-0
    StarterBus.LISTENER.unsubscribe()
  }

  override fun afterAll(p: ExtensionContext) {
    di.direct.instance<CurrentTestMethod>().set(null)
  }
}



