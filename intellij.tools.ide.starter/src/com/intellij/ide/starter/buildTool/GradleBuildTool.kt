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
import kotlin.io.path.*

open class GradleBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.GRADLE, testContext) {
  private val localGradleRepoPath: Path
    get() = testContext.paths.tempDir.resolve("gradle")

  private val gradleXmlPath: Path
    get() = testContext.resolvedProjectHome.resolve(".idea").resolve("gradle.xml")

  private fun parseGradleXmlConfig(): Document = XmlBuilder.parse(gradleXmlPath)

  fun useNewGradleLocalCache(): GradleBuildTool {
    localGradleRepoPath.toFile().mkdirs()
    testContext.addVMOptionsPatch { addSystemProperty("gradle.user.home", localGradleRepoPath.toString()) }
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
    testContext.addVMOptionsPatch {
      configureLoggers(logLevel, "org.jetbrains.plugins.gradle")
    }
  }
}