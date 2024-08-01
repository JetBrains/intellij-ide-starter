package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.div

interface IDETestContextFactory {
  fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false, projectHome: Path?, setupHooks: MutableList<IDETestContext.() -> IDETestContext>): IDETestContext
}

open class LocalIDETestContextFactoryImpl : IDETestContextFactory {

  override fun newContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean, projectHome: Path?, setupHooks: MutableList<IDETestContext.() -> IDETestContext>): IDETestContext {
    logOutput("Resolving IDE build for $testName...")
    val (buildNumber, ide) = @Suppress("SSBasedInspection")
    (runBlocking(Dispatchers.Default) {
      computeWithSpan("resolving IDE") {
        TestContainer.resolveIDE(testCase.ideInfo)
      }
    })

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code of $ide must be the same as for $testCase" }

    val testDirectory = run {
      val commonPath = (GlobalPaths.instance.testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testName
      if (testCase.ideInfo.platformPrefix == "JetBrainsClient") {
        commonPath / "embedded-client"
      }
      else {
        commonPath
      }
    }

    val paths = IDEDataPaths.createPaths(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for '$testName': $paths")
    logOutput("IDE to run for '$testName': $ide")

    var testContext = IDETestContext(paths, ide, testCase, testName, projectHome, preserveSystemDir = preserveSystemDir)
    testContext.wipeSystemDir()

    testContext = TestContainer.applyDefaultVMOptions(testContext)

    val contextWithAppliedHooks = setupHooks
      .fold(testContext.updateGeneralSettings()) { acc, hook -> acc.hook() }
      .apply { TestContainer.installPerformanceTestingPluginIfMissing(this) }

    testCase.projectInfo.configureProjectBeforeUse.invoke(contextWithAppliedHooks)

    EventsBus.postAndWaitProcessing(TestContextInitializedEvent(contextWithAppliedHooks))

    return contextWithAppliedHooks
  }
}


