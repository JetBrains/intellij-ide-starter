package com.intellij.ide.starter.path

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.utils.FileSystem.getDirectoryTreePresentableSizes
import com.intellij.ide.starter.utils.getDiskInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.div

abstract class GlobalPaths(val checkoutDir: Path) {
  val intelliJOutDirectory: Path = checkoutDir.toAbsolutePath() / "out"
  val artifactsDirectory: Path = intelliJOutDirectory / "artifacts"

  /**
   * Local => out
   * CI => out/tests
   */
  val compiledRootDirectory: Path = when (CIServer.instance.isBuildRunningOnCI) {
    true -> intelliJOutDirectory / "tests"
    false -> intelliJOutDirectory // Local run
  }

  open val testHomePath: Path = intelliJOutDirectory.resolve("ide-tests").createDirectories()

  open val devServerDirectory: Path = intelliJOutDirectory.resolve("dev-run").createDirectories()

  val installersDirectory = (testHomePath / "installers").createDirectories()

  val testsDirectory = (testHomePath / "tests").createDirectories()

  private val cacheDirectory: Path = if (CIServer.instance.isBuildRunningOnCI &&
                                         !System.getProperty("agent.persistent.cache").isNullOrEmpty()
  ) {
    (Paths.get(System.getProperty("agent.persistent.cache"), "perf-tests-cache")).createDirectories()
  }
  else {
    (testHomePath / "cache").createDirectories()
  }

  fun getCacheDirectoryFor(entity: String): Path = (cacheDirectory / entity).createDirectories()

  //TODO: Move it to [FileSystem] class
  fun getDiskUsageDiagnostics(): String {
    return buildString {
      appendLine("Disk usage by integration tests (home $testHomePath)")
      appendLine(Files.getFileStore(testHomePath).getDiskInfo())
      appendLine()
      appendLine(testHomePath.getDirectoryTreePresentableSizes(3))
      if (cacheDirectory != testHomePath / "cache") {
        appendLine("Agent persistent cache directory disk usage $cacheDirectory")
        appendLine(cacheDirectory.getDirectoryTreePresentableSizes(2))
      }
      appendLine()
      appendLine("Directories' size from $devServerDirectory")
      appendLine(devServerDirectory.getDirectoryTreePresentableSizes())
    }
  }

  companion object {
    val instance: GlobalPaths
      get() = di.direct.instance<GlobalPaths>()
  }
}