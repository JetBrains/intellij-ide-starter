package com.intellij.ide.starter.ide

import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.openapi.util.SystemInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path


class IDERemDevTestContext(
  paths: IDEDataPaths,
  ide: InstalledIde,
  testCase: TestCase<*>,
  testName: String,
  _resolvedProjectHome: Path?,
  profilerType: ProfilerType = ProfilerType.NONE,
  publishers: List<ReportPublisher> = di.direct.instance(),
  isReportPublishingEnabled: Boolean = true,
  preserveSystemDir: Boolean = false,
  var frontendIDEContext: IDETestContext,
) : IDETestContext(
  paths = paths,
  ide = ide,
  testCase = testCase,
  testName = testName,
  _resolvedProjectHome = _resolvedProjectHome,
  profilerType = profilerType,
  publishers = publishers,
  isReportPublishingEnabled = isReportPublishingEnabled,
  preserveSystemDir = preserveSystemDir,
) {
  companion object {

    fun from(backendContext: IDETestContext, frontendCtx: IDETestContext): IDERemDevTestContext {
      return IDERemDevTestContext(
        paths = backendContext.paths,
        ide = backendContext.ide,
        testCase = backendContext.testCase,
        testName = backendContext.testName,
        _resolvedProjectHome =  backendContext._resolvedProjectHome,
        profilerType = backendContext.profilerType,
        publishers = backendContext.publishers,
        isReportPublishingEnabled = backendContext.isReportPublishingEnabled,
        preserveSystemDir = backendContext.preserveSystemDir,
        frontendIDEContext = frontendCtx,
      )
    }
  }
}

val IDETestContext.frontendTestCase: TestCase<out ProjectInfoSpec>
  get() {
    val executableFileName = when {
      (SystemInfo.isLinux || SystemInfo.isWindows) && StarterConfigurationStorage.shouldRunOnInstaller() -> "jetbrains_client"
      else -> this.testCase.ideInfo.executableFileName
    }

    return this.testCase.copy(ideInfo = this.testCase.ideInfo.copy(
      platformPrefix = "JetBrainsClient",
      executableFileName = executableFileName
    ))
  }
