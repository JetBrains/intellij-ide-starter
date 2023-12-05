package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.destroyProcessIfExists
import com.intellij.ide.starter.runner.ValidateVMOptionsWereSetEvent
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter

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
  }

  init {
    StarterBus.subscribeOnlyOnce(MavenBuildTool::javaClass) { event: ValidateVMOptionsWereSetEvent ->
      if (event.data.testContext === testContext) destroyMavenIndexerProcessIfExists()
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

  fun setPropertyInPomXml(propertyName: String,
                          propertyValue: String,
                          modulePath: Path = testContext.resolvedProjectHome): MavenBuildTool {
    val pomXml = modulePath.resolve("pom.xml")
    val propertiesTag = "<properties>"
    val closePropertiesTag = "</properties>"
    val newProperty = "<$propertyName>$propertyValue</$propertyName>"
    val text = pomXml.bufferedReader().use { it.readText() }
      .run {
        if (contains(propertiesTag)) {
          if (contains(propertyName)) {
            replace("(?<=<$propertyName>)(.*)(?=</$propertyName>)".toRegex(), propertyValue)
          }
          else {
            replace(propertiesTag, "$propertiesTag\n$newProperty")
          }
        }
        else {
          val closeModelVersionTag = "</modelVersion>"
          replace(closeModelVersionTag, "$closeModelVersionTag\n$propertiesTag\n$newProperty$closePropertiesTag")
        }
      }
    pomXml.bufferedWriter().use { it.write(text) }
    return this
  }
}