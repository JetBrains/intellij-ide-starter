package com.intellij.ide.starter.runner

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.bus.subscribe
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeInstallator
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginInstalledState
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.factory
import java.io.Closeable
import kotlin.io.path.div

/**
 * [ciServer] - use [com.intellij.ide.starter.ci.NoCIServer] for local run. Otherwise - pass implementation of CIServer
 */
interface TestContainer<T> : Closeable {
  val ciServer: CIServer
  var testContext: IDETestContext
  val setupHooks: MutableList<IDETestContext.() -> IDETestContext>

  companion object {
    init {
      StarterListener.subscribe { event: TestContextInitializedEvent ->
        if (event.state == EventState.AFTER) {
          logOutput("Starter configuration storage: ${ConfigurationStorage.instance().getAll()}")
        }
      }
    }
  }

  override fun close() {
    catchAll { testContext.paths.close() }

    logOutput("TestContainer $this disposed")
  }

  /**
   * Allows to apply the common configuration to all created IDETestContext instances
   */
  fun withSetupHook(hook: IDETestContext.() -> IDETestContext): T = apply {
    setupHooks += hook
  } as T

  /**
   * @return <Build Number, InstalledIde>
   */
  fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    return di.direct.factory<IdeInfo, IdeInstallator>().invoke(ideInfo).install(ideInfo)
  }

  fun installPerformanceTestingPluginIfMissing(context: IDETestContext) {
    val performancePluginId = "com.jetbrains.performancePlugin"

    context.pluginConfigurator.apply {
      val pluginState = getPluginInstalledState(performancePluginId)
      if (pluginState != PluginInstalledState.INSTALLED && pluginState != PluginInstalledState.BUNDLED_TO_IDE)
        installPluginFromPluginManager(performancePluginId, ide = context.ide)
    }
  }

  /**
   * Starting point to run your test
   * @param preserveSystemDir Only for local runs when you know that having "dirty" system folder is ok and want to speed up test execution.
   */
  fun initializeTestContext(testName: String, testCase: TestCase<*>, preserveSystemDir: Boolean = false): IDETestContext {
    logOutput("Resolving IDE build for $testName...")
    val (buildNumber, ide) = resolveIDE(testCase.ideInfo)

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code of $ide must be the same as for $testCase" }

    val testDirectory = (GlobalPaths.instance.testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testName

    val paths = IDEDataPaths.createPaths(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for '$testName': $paths")
    logOutput("IDE to run for '$testName': $ide")

    val projectHome = testCase.projectInfo.downloadAndUnpackProject()
    testContext = IDETestContext(paths, ide, testCase, testName, projectHome, preserveSystemDir = preserveSystemDir)
    testContext.wipeSystemDir()

    testContext = updateVMOptions(testContext)

    val contextWithAppliedHooks = setupHooks
      .fold(testContext.updateGeneralSettings()) { acc, hook -> acc.hook() }
      .apply { installPerformanceTestingPluginIfMissing(this) }

    testCase.projectInfo.configureProjectBeforeUse.invoke(contextWithAppliedHooks)

    StarterBus.post(TestContextInitializedEvent(EventState.AFTER, contextWithAppliedHooks))

    return contextWithAppliedHooks
  }

  fun updateVMOptions(context: IDETestContext): IDETestContext {
    return when (context.testCase.ideInfo == IdeProductProvider.AI) {
      true -> context
        .applyVMOptionsPatch {
          overrideDirectories(context.paths)
          withEnv("STUDIO_VM_OPTIONS", context.ide.patchedVMOptionsFile.toString())
        }
      false -> context
        .disableInstantIdeShutdown()
        .disableFusSendingOnIdeClose()
        .disableLinuxNativeMenuForce()
        .withGtk2OnLinux()
        .skipGitLogIndexing()
        .enableSlowOperationsInEdtInTests()
        .enableAsyncProfiler()
        .applyVMOptionsPatch {
          overrideDirectories(context.paths)
          if(isUnderDebug()){
            debug()
          }
        }
        .disableMinimap()
        .addProjectToTrustedLocations()
        .useNewUIInTests()
        .disableReportingStatisticsToProduction()
        .disableMigrationNotification()
    }
  }
}