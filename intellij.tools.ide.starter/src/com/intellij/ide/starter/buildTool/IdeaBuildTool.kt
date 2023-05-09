package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.XmlBuilder
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.io.path.notExists
import kotlin.io.path.readText


class IdeaBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.IDEA, testContext) {
  private val ideaDir: Path
    get() = testContext.resolvedProjectHome.resolve(".idea")

  private val compilerXmlPath: Path
    get() = ideaDir.resolve("compiler.xml")

  fun setBuildProcessHeapSize(heapSizeMb: Int): IdeaBuildTool {
    if (compilerXmlPath.notExists()) return this
    val content = compilerXmlPath.readText()
    if (content.contains("option name=\"BUILD_PROCESS_HEAP_SIZE\" value=\"$heapSizeMb\"")) return this

    val xmlDoc = XmlBuilder.parse(compilerXmlPath)
    xmlDoc.documentElement.normalize()
    val xp: XPath = XPathFactory.newInstance().newXPath()

    if (content.contains("CompilerConfiguration")) {

      if (compilerXmlPath.readText().contains("BUILD_PROCESS_HEAP_SIZE")) {
        val node = xp.evaluate("//component/option[@name='BUILD_PROCESS_HEAP_SIZE']", xmlDoc, XPathConstants.NODE) as Element
        node.removeAttribute("value")
        node.setAttribute("value", "$heapSizeMb")
      }
      else {
        val componentNode = xp.evaluate("//component[@name='CompilerConfiguration']", xmlDoc, XPathConstants.NODE) as Element
        val optionElement = xmlDoc.createElement("option")
        optionElement.setAttribute("name", "BUILD_PROCESS_HEAP_SIZE")
        optionElement.setAttribute("value", "$heapSizeMb")
        componentNode.appendChild(optionElement)
      }
    }
    else {
      val firstNode = xmlDoc.firstChild
      val componentElement = xmlDoc.createElement("component")
      componentElement.setAttribute("name", "CompilerConfiguration")
      val optionElement = xmlDoc.createElement("option")
      optionElement.setAttribute("name", "BUILD_PROCESS_HEAP_SIZE")
      optionElement.setAttribute("value", "$heapSizeMb")
      firstNode.appendChild(componentElement).appendChild(optionElement)
    }
    XmlBuilder.writeDocument(xmlDoc, compilerXmlPath)

    return this
  }

  fun addBuildVmOption(key: String, value: String): IdeaBuildTool {
    if (compilerXmlPath.notExists()) return this
    val content = compilerXmlPath.readText()
    if (content.contains("-D$key=$value")) return this

    val xmlDoc = XmlBuilder.parse(compilerXmlPath)
    xmlDoc.documentElement.normalize()
    val xp: XPath = XPathFactory.newInstance().newXPath()

    if (content.contains("CompilerConfiguration")) {

      if (compilerXmlPath.readText().contains("BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")) {

        val optionNode = xp.evaluate("//component/option[@name='BUILD_PROCESS_ADDITIONAL_VM_OPTIONS']", xmlDoc,
                                     XPathConstants.NODE) as Element
        val oldValue = optionNode.getAttribute("value")

        if (oldValue.contains(value)) return this

        val newValue = "$oldValue -D$key=$value"
        optionNode.removeAttribute("value")
        optionNode.setAttribute("value", newValue)
      }
      else {
        val componentNode = xp.evaluate("//component[@name='CompilerConfiguration']", xmlDoc, XPathConstants.NODE) as Element
        val optionElement = xmlDoc.createElement("option")
        optionElement.setAttribute("name", "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")
        optionElement.setAttribute("value", "-D$key=$value")
        componentNode.appendChild(optionElement)
      }
    }
    else {
      val firstNode = xmlDoc.firstChild
      val componentElement = xmlDoc.createElement("component")
      componentElement.setAttribute("name", "CompilerConfiguration")
      val optionElement = xmlDoc.createElement("option")
      optionElement.setAttribute("name", "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")
      optionElement.setAttribute("value", "-D$key=$value")
      firstNode.appendChild(componentElement).appendChild(optionElement)
    }
    XmlBuilder.writeDocument(xmlDoc, compilerXmlPath)

    return this
  }
}