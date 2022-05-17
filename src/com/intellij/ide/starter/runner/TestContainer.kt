package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import java.io.Closeable
import kotlin.io.path.div

/**
 * [ciServer] - use [NoCIServer] for only local run. Otherwise - pass implementation of CIServer
 */
interface TestContainer<T> : Closeable {
  val ciServer: CIServer
  var useLatestDownloadedIdeBuild: Boolean
  val allContexts: MutableList<IDETestContext>
  val setupHooks: MutableList<IDETestContext.() -> IDETestContext>

  override fun close() {
    for (context in allContexts) {
      catchAll { context.paths.close() }
    }
  }

  /**
   * Allows to apply the common configuration to all created IDETestContext instances
   */
  fun withSetupHook(hook: IDETestContext.() -> IDETestContext): T = apply {
    setupHooks += hook
  } as T

  /**
   * Makes the test use the latest available locally IDE build for testing.
   */
  fun useLatestDownloadedIdeBuild(): T = apply {
    assert(!ciServer.isBuildRunningOnCI)
    useLatestDownloadedIdeBuild = true
  } as T

  fun resolveIDE(ideInfo: IdeInfo): Pair<String, InstalledIDE> {
    return di.direct.factory<IdeInfo, IdeInstallator>().invoke(ideInfo).install(ideInfo)
  }

  /** Starting point to run your test */
  fun initializeTestRunner(testName: String, testCase: TestCase): IDETestContext {
    check(allContexts.none { it.testName == testName }) { "Test $testName is already initialized. Use another name." }
    logOutput("Resolving IDE build for $testName...")

    val (buildNumber, ide) = resolveIDE(testCase.ideInfo)

    require(ide.productCode == testCase.ideInfo.productCode) { "Product code of $ide must be the same as for $testCase" }

    val testDirectory = (di.direct.instance<GlobalPaths>().testsDirectory / "${testCase.ideInfo.productCode}-$buildNumber") / testName

    val paths = IDEDataPaths.createPaths(testName, testDirectory, testCase.useInMemoryFileSystem)
    logOutput("Using IDE paths for $testName: $paths")
    logOutput("IDE to run for $testName: $ide")

    val projectHome = testCase.projectInfo?.resolveProjectHome()
    val context = IDETestContext(paths, ide, testCase, testName, projectHome, patchVMOptions = { this }, ciServer = ciServer)
    allContexts += context

    val baseContext = when (testCase.ideInfo == IdeProduct.AI.ideInfo) {
      true -> context
        .addVMOptionsPatch {
          overrideDirectories(paths)
            .withEnv("STUDIO_VM_OPTIONS", ide.patchedVMOptionsFile.toString())
        }
      false -> context
        .disableInstantIdeShutdown()
        .disableFusSendingOnIdeClose()
        .disableJcef()
        .disableLinuxNativeMenuForce()
        .withGtk2OnLinux()
        .disableGitLogIndexing()
        .enableSlowOperationsInEdtInTests()
        .addVMOptionsPatch {
          overrideDirectories(paths)
        }
    }
    return setupHooks.fold(baseContext.updateGeneralSettings()) { acc, hook -> acc.hook() }
  }
}