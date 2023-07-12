package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.XmlBuilder
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import com.intellij.openapi.diagnostic.LogLevel
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

open class GradleBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.GRADLE, testContext) {
  private val localGradleRepoPath: Path
    get() = testContext.paths.tempDir.resolve("gradle")

  private val gradleXmlPath: Path
    get() = testContext.resolvedProjectHome.resolve(".idea").resolve("gradle.xml")

  private fun parseGradleXmlConfig(): Document = XmlBuilder.parse(gradleXmlPath)

  fun useNewGradleLocalCache(): GradleBuildTool {
    localGradleRepoPath.toFile().mkdirs()
    testContext.applyVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepoPath.toString()) }
    return this
  }

  fun removeGradleConfigFiles(): GradleBuildTool {
    logOutput("Removing Gradle config files in ${testContext.resolvedProjectHome} ...")

    testContext.resolvedProjectHome.toFile().walkTopDown()
      .forEach {
        if (it.isFile && (it.extension == "gradle" || (it.name in listOf("gradlew", "gradlew.bat", "gradle.properties")))) {
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

  fun setLogLevel(logLevel: LogLevel) {
    testContext.applyVMOptionsPatch {
      configureLoggers(logLevel, "org.jetbrains.plugins.gradle")
    }
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
}