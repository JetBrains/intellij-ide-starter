package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.XmlBuilder
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.writeText

class IdeaBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.IDEA, testContext) {
  private val ideaDir: Path
    get() = testContext.resolvedProjectHome.resolve(".idea")

  private val compilerXmlPath: Path
    get() = ideaDir.resolve("compiler.xml")

  fun setBuildProcessHeapSize(heapSizeMb: Int = 2000): IdeaBuildTool {
    if (compilerXmlPath.notExists()) return this

    val newContent = StringBuilder()
    val readText = compilerXmlPath.toFile().readText()
    if (!readText.contains("BUILD_PROCESS_HEAP_SIZE")) {
      compilerXmlPath.toFile().readLines().forEach {
        if (it.contains("<component name=\"CompilerConfiguration\">")) {
          val newLine = "<component name=\"CompilerConfiguration\">\n<option name=\"BUILD_PROCESS_HEAP_SIZE\" value=\"$heapSizeMb\" />"
          newContent.appendLine(newLine)
        }
        else {
          newContent.appendLine(it)
        }
      }
      compilerXmlPath.writeText(newContent.toString())
    }

    return this
  }

  fun addBuildVMOption(key: String, value: String): IdeaBuildTool {
    val workspace = ideaDir.resolve("workspace.xml")
    if (workspace.notExists()) return this

    val newContent = StringBuilder()
    val readText = workspace.toFile().readText()

    val userLocalBuildProcessVmOptions = when {
      (testContext.testName.contains(
        "intellij_sources")) -> "-D$key=$value -Dgroovyc.in.process=true -Dgroovyc.asm.resolving.only=false"
      else -> "-D$key=$value"
    }

    if (readText.contains("CompilerWorkspaceConfiguration")) {
      workspace.toFile().readLines().forEach {
        if (it.contains("<component name=\"CompilerWorkspaceConfiguration\">")) {
          val newLine = "<component name=\"CompilerWorkspaceConfiguration\">\n<option name=\"COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS\" value=\"$userLocalBuildProcessVmOptions\" />"
          newContent.appendLine(newLine)
        }
        else {
          newContent.appendLine(it)
        }
      }
      workspace.writeText(newContent.toString())
    }
    else {
      val xmlDoc = XmlBuilder.parse(workspace)

      val firstElement = xmlDoc.firstChild
      val componentElement = xmlDoc.createElement("component")
      componentElement.setAttribute("name", "CompilerWorkspaceConfiguration")
      val optionElement = xmlDoc.createElement("option")
      optionElement.setAttribute("name", "COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS")
      optionElement.setAttribute("value", userLocalBuildProcessVmOptions)
      firstElement.appendChild(componentElement).appendChild(optionElement)

      XmlBuilder.writeDocument(xmlDoc, workspace)
    }

    return this
  }
}