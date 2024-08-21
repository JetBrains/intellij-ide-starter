package com.intellij.ide.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.runner.Starter

fun IDETestContext.disableCLionTestIndexing() =
  applyVMOptionsPatch { this.addSystemProperty("cidr.disable.test.indexing", true) }

// Should be passed manually (through TC or run configuration)
val isRadler by lazy { System.getProperty("intellij.clion.radler.perf.tests", "false").toBoolean() }
val clionPrefix by lazy { if (isRadler) "radler" else "clion" }

fun getCLionContext(testName: String, testCase: TestCase<*>): IDETestContext {
  val context = Starter.newContext("$clionPrefix/$testName", testCase)
  return context
    .setMemorySize(4096)
    .applyVMOptionsPatch {
      if (isRadler) {
        // Enable performance watcher for backend
        addSystemProperty("rider.backend.performance.watcher.isEnabled", "true")

        // Disable log cleanup activity to prevent removal of empty directories
        addSystemProperty("rider.log.cleanup.interval", 0)

        // Enable radler
        addSystemProperty("idea.suppressed.plugins.set.selector", "radler")
      }
      else {
        // Enable Classic
        addSystemProperty("idea.suppressed.plugins.set.selector", "classic")
      }

      addSystemProperty("ide.mac.file.chooser.native", "false")

      addSystemProperty("apple.laf.useScreenMenuBar","false")

      addSystemProperty("jbScreenMenuBar.enabled","false")

      // Disable a/b testing (not necessary, but it is cleaner this way)
      addSystemProperty("clion.ab.test", "false")

      // Disable assertions
      addSystemProperty("actionSystem.update.actions.warn.dataRules.on.edt", "false")
      addSystemProperty("ide.slow.operations.assertion", "false")

      // Disable wizard
      addSystemProperty("clion.skip.open.wizard", "true")

      // Disable "Tip of the day" window
      addSystemProperty("ide.show.tips.on.startup.default.value", "false")

      // Disable new UI
      addSystemProperty("ide.experimental.ui", "false")

      // Set command timeout to 1 minute
      addSystemProperty("actionSystem.commandProcessingTimeout", 60000)

      // Enable verbose logging for scripts
      addSystemProperty("clion.enable.script.verbose", true)

      // Continue script execution when tested IDE lost focus
      addSystemProperty("actionSystem.suspendFocusTransferIfApplicationInactive", false)

      // CRITICAL! Direct script command execution instead of typing shortcuts if they exist. By default, shortcuts are turned on and
      // scripting does not work
      addSystemProperty("performance.plugin.playback.runner.useDirectActionCall", true)

      // Enable logging for `RadClangdCodeCompletionProviderImpl`
      addSystemProperty("clion.clang.clangd.debug", true)
    }
    .disableAIAssistantToolwindowActivationOnStart()
}