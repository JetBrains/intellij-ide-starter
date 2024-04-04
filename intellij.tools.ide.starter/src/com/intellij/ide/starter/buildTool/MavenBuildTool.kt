package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.destroyProcessIfExists
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeKillEvent
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.tools.ide.performanceTesting.commands.dto.MavenArchetypeInfo
import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.tools.ide.util.common.logOutput
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createFile

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

    private const val MAVEN_DAEMON_NAME = "MavenServerIndexerMain"
    private fun destroyMavenIndexerProcessIfExists() {
      destroyProcessIfExists(MAVEN_DAEMON_NAME)
    }
  }

  init {
    StarterBus.subscribe(GradleBuildTool::javaClass) { event: IdeAfterLaunchEvent ->
      if (event.runContext.testContext === testContext) {
        collectDumpFile(MAVEN_DAEMON_NAME,
                        event.runContext.logsDir,
                        testContext.ide.resolveAndDownloadTheSameJDK(),
                        testContext.ide.installationPath)
        destroyMavenIndexerProcessIfExists()
      }
    }

    StarterBus.subscribe(MavenBuildTool::javaClass) { event: IdeBeforeKillEvent ->
      testContext.ide.resolveAndDownloadTheSameJDK()
      if (event.runContext.testContext === testContext) {
        collectDumpFile(MAVEN_DAEMON_NAME,
                        event.runContext.logsDir,
                        testContext.ide.resolveAndDownloadTheSameJDK(),
                        testContext.ide.installationPath)
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

  fun downloadArtifactFromMavenCentral(data: MavenArchetypeInfo, repoPath: Path) {
    try {
      listOf("pom", "jar").forEach {
        val fileName = "${data.artefactId}-${data.version}.$it"
        val filePath = "${data.groupId.replace('.', '/')}/${data.artefactId}/${data.version}"
        val file = repoPath.resolve(filePath).findOrCreateDirectory().resolve(fileName).createFile()
        val url = "https://repo1.maven.org/maven2/$filePath/$fileName"
        BufferedInputStream(URL(url).openStream()).use { inputStream ->
          FileOutputStream(file.toString()).use { outputStream ->
            inputStream.copyTo(outputStream)
          }
        }
      }
    }
    catch (e: Exception) {
      throw IllegalStateException("Error downloading artifact: ${e.message}")
    }
  }
}