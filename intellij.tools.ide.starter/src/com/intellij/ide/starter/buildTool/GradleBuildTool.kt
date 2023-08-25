package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.XmlBuilder
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.util.io.readText
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class GradleBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.GRADLE, testContext) {
  private val localGradleRepoPath: Path
    get() = testContext.paths.tempDir.resolve("gradle")

  private val gradleXmlPath: Path
    get() = testContext.resolvedProjectHome.resolve(".idea").resolve("gradle.xml")

  private fun parseGradleXmlConfig(): Document = XmlBuilder.parse(gradleXmlPath)

  private fun getGradleVersionFromWrapperProperties(): String {
    val propFile = testContext.resolvedProjectHome.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties")
    if (propFile.notExists()) return ""
    val distributionUrl = propFile.readLines().first() { it.startsWith("distributionUrl") }
    val version = "\\d.{1,4}\\d".toRegex().find(distributionUrl)?.value ?: ""
    return version
  }

  fun getGradleDaemonLog(): Path {
    return localGradleRepoPath.resolve("daemon").resolve(getGradleVersionFromWrapperProperties())
      .listDirectoryEntries()
      .first { it.last().extension == "log" }
  }

  fun useNewGradleLocalCache(): GradleBuildTool {
    localGradleRepoPath.toFile().mkdirs()
    testContext.applyVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepoPath.toString()) }
    return this
  }

  fun removeGradleConfigFiles(): GradleBuildTool {
    logOutput("Removing Gradle config files in ${testContext.resolvedProjectHome} ...")

    testContext.resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && (it.extension in listOf("gradle", "kts") || (it.name in listOf("gradlew", "gradlew.bat", "gradle.properties")))) {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  fun addPropertyToGradleProperties(property: String, value: String): GradleBuildTool {
    val projectDir = testContext.resolvedProjectHome
    val gradleProperties = projectDir.resolve("gradle.properties")
    val lineWithTheSameProperty = gradleProperties.readLines().singleOrNull { it.contains(property) }

    if (lineWithTheSameProperty != null) {
      if (lineWithTheSameProperty.contains(value)) {
        return this
      }

      val newValue = lineWithTheSameProperty.substringAfter("$property=") + " $value"
      val tempFile = File.createTempFile("newContent", ".txt").toPath()
      gradleProperties.forEachLine { line ->
        tempFile.appendText(when {
                              line.contains(property) -> "$property=$newValue" + System.getProperty("line.separator")
                              else -> line + System.getProperty("line.separator")
                            })
      }
      gradleProperties.writeText(tempFile.readText())
    }
    else {
      gradleProperties.appendLines(listOf("$property=$value"))
    }

    return this
  }

  fun setGradleJvmInProject(useJavaHomeAsGradleJvm: Boolean = true): GradleBuildTool {
    try {
      if (gradleXmlPath.notExists()) return this
      val xmlDoc = parseGradleXmlConfig()

      val gradleSettings = xmlDoc.getElementsByTagName("GradleProjectSettings")
      if (gradleSettings.length != 1) return this

      val options = (gradleSettings.item(0) as Element).getElementsByTagName("option")

      XmlBuilder.findNode(options) { it.getAttribute("name") == "gradleJvm" }
        .ifPresent { node -> gradleSettings.item(0).removeChild(node) }

      if (useJavaHomeAsGradleJvm) {
        val option = xmlDoc.createElement("option")
        option.setAttribute("name", "gradleJvm")
        option.setAttribute("value", "#JAVA_HOME")
        gradleSettings.item(0).appendChild(option)
      }

      XmlBuilder.writeDocument(xmlDoc, gradleXmlPath)
    }
    catch (e: Exception) {
      logError(e)
    }

    return this
  }

  fun runBuildBy(useGradleBuildSystem: Boolean): GradleBuildTool {
    if (gradleXmlPath.notExists()) return this
    if (gradleXmlPath.toFile().readText().contains("<option name=\"delegatedBuild\" value=\"$useGradleBuildSystem\"/>")) return this

    val xmlDoc = parseGradleXmlConfig()

    val gradleProjectSettingsElements: NodeList = xmlDoc.getElementsByTagName("GradleProjectSettings")
    if (gradleProjectSettingsElements.length != 1) return this

    val options = (gradleProjectSettingsElements.item(0) as Element).getElementsByTagName("option")

    XmlBuilder.findNode(options) { it.getAttribute("name") == "delegatedBuild" }
      .ifPresent { node -> gradleProjectSettingsElements.item(0).removeChild(node) }

    for (i in 0 until gradleProjectSettingsElements.length) {
      val component: Node = gradleProjectSettingsElements.item(i)

      if (component.nodeType == Node.ELEMENT_NODE) {
        val optionElement = xmlDoc.createElement("option")
        optionElement.setAttribute("name", "delegatedBuild")
        optionElement.setAttribute("value", "$useGradleBuildSystem")
        component.appendChild(optionElement)
      }
    }

    XmlBuilder.writeDocument(xmlDoc, gradleXmlPath)

    return this
  }

  fun setLogLevel(logLevel: LogLevel): GradleBuildTool {
    testContext.applyVMOptionsPatch {
      configureLoggers(logLevel, "org.jetbrains.plugins.gradle")
    }
    return this
  }

  /*
    This method enables/disables Gradle Build Cache and
    returns the previous state of org.gradle.caching variable
    By default, the build cache is not enabled.
    So if gradle.properties does not contain org.gradle.caching
    the method returns false
   */
  fun toggleGradleBuildCaching(value: Boolean): Boolean {
    val userHome = System.getProperty("user.home", null)
    if (userHome == null) return false

    val gradleProperties = Paths.get(userHome).resolve(".gradle").resolve("gradle.properties")
    if (gradleProperties.notExists()) return false

    val text = gradleProperties.readText()
    if (!text.contains("org.gradle.caching") && value) {
      gradleProperties.toFile().appendText("\norg.gradle.caching=true")
      return false
    }
    else if (!text.contains("org.gradle.caching") && !value) {
      return false
    }
    else if (text.contains("org.gradle.caching=true") && !value) {
      val newText = text.replace("org.gradle.caching=true", "")
      gradleProperties.toFile().writeText(newText)
      return true
    }
    else if (text.contains("org.gradle.caching=true") && value) {
      return true
    }
    else if (text.contains("org.gradle.caching=false") && !value) {
      return false
    }
    else if (text.contains("org.gradle.caching=false") && value) {
      val newText = text.replace("org.gradle.caching=false", "org.gradle.caching=true")
      gradleProperties.toFile().writeText(newText)
      return false
    }

    return false
  }

  fun execGradlew(args: List<String>, timeout: Duration = 1.minutes): GradleBuildTool {
    val stdout = ExecOutputRedirect.ToString()
    val stderr = ExecOutputRedirect.ToString()

    val command = when (SystemInfo.isWindows) {
      true -> (testContext.resolvedProjectHome / "gradlew.bat").toString()
      false -> "./gradlew"
    }

    if (!SystemInfo.isWindows) {
      ProcessExecutor(
        presentableName = "chmod gradlew",
        workDir = testContext.resolvedProjectHome,
        timeout = 1.minutes,
        args = listOf("chmod", "+x", "gradlew"),
        stdoutRedirect = stdout,
        stderrRedirect = stderr
      ).start()
    }

    ProcessExecutor(
      presentableName = "Calling gradlew with parameters: $args",
      workDir = testContext.resolvedProjectHome,
      timeout = timeout,
      args = listOf(command) + args,
      stdoutRedirect = stdout,
      stderrRedirect = stderr
    ).start()
    return this
  }

  fun setGradleVersionInWrapperProperties(newVersion: String): GradleBuildTool {
    val propFile = testContext.resolvedProjectHome.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties")
    if (propFile.exists()) {
      val lineToReplace = propFile.readLines().filter { it.startsWith("distributionUrl") }[0]
      val newLine = lineToReplace.replace("\\d\\.\\d".toRegex(), newVersion)
      propFile.writeText(propFile.readText().replace(lineToReplace, newLine))
    }
    return this
  }
}

