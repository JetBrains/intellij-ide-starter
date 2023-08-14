package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.logOutput
import com.intellij.openapi.diagnostic.LogLevel
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
  }

  private val temporaryMavenM3CashPath: Path
    get() = testContext.paths.tempDir.resolve(".m3")

  val temporaryMavenM3UserSettingsPath: Path
    get() = temporaryMavenM3CashPath.resolve("settings.xml")

  val temporaryMavenM3RepoPath: Path
    get() = temporaryMavenM3CashPath.resolve("repository")



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