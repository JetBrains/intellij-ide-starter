package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.destroyProcessIfExists
import com.intellij.ide.starter.runner.IdeLaunchEvent
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.Path

open class MavenBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.MAVEN, testContext) {
  companion object {
    /**
     * ~/.m2
     */
    val DEFAULT_MAVEN_M2_REPO_PATH: Path
      get() {
        val userHome = System.getProperty("user.home", null)
        val path = if (userHome != null) Path(userHome, ".m2/repository")
        else Path(".m2/repository")

        return path.toAbsolutePath()
      }

    private fun destroyMavenIndexerProcessIfExists() {
      val mavenDaemonName = "MavenServerIndexerMain"
      destroyProcessIfExists(mavenDaemonName)
    }

    init {
      StarterListener.subscribe { event: IdeLaunchEvent ->
        if (event.state == EventState.AFTER) {
          destroyMavenIndexerProcessIfExists()
        }
      }
    }
  }

  private val temporaryMavenM3CachePath: Path
    get() = testContext.paths.tempDir.resolve(".m3")

  val temporaryMavenM3UserSettingsPath: Path
    get() = temporaryMavenM3CachePath.resolve("settings.xml")

  val temporaryMavenM3RepoPath: Path
    get() = temporaryMavenM3CachePath.resolve("repository")


  fun useNewMavenLocalRepository(): MavenBuildTool {
    temporaryMavenM3RepoPath.toFile().mkdirs()
    testContext.applyVMOptionsPatch { addSystemProperty("idea.force.m2.home", temporaryMavenM3RepoPath.toString()) }
    return this
  }

  fun removeMavenConfigFiles(): MavenBuildTool {
    logOutput("Removing Maven config files in ${testContext.resolvedProjectHome} ...")

    testContext.resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && it.name == "pom.xml") {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun setLogLevel(logLevel: LogLevel) {
    testContext.applyVMOptionsPatch {
      configureLoggers(logLevel, "org.jetbrains.idea.maven")
    }
  }
}