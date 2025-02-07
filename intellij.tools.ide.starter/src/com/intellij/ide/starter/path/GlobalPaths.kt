package com.intellij.ide.starter.path

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.div

const val TEST_DATA_CACHE_NAME = "test-data-cache"

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

  /** Cache directory on the current machine */
  open val localCacheDirectory: Path = if (CIServer.instance.isBuildRunningOnCI &&
                                           !System.getProperty("agent.persistent.cache").isNullOrEmpty()) {
    (Paths.get(System.getProperty("agent.persistent.cache"), TEST_DATA_CACHE_NAME)).createDirectories()
  }
  else {
    (testHomePath / "cache").createDirectories()
  }

  open fun getCacheDirectoryFor(entity: String): Path = (localCacheDirectory / entity).createDirectories()

  open val cacheDirForProjects: Path get() = getCacheDirectoryFor("projects")

  companion object {
    val instance: GlobalPaths
      get() = di.direct.instance<GlobalPaths>()
  }
}