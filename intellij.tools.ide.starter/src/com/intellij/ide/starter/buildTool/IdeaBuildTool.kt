package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.XmlBuilder
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.io.path.notExists


class IdeaBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.IDEA, testContext) {
  private val ideaDir: Path
    get() = testContext.resolvedProjectHome.resolve(".idea")

  private val compilerXmlPath: Path
    get() = ideaDir.resolve("compiler.xml")

  fun setBuildProcessHeapSize(heapSizeMb: Int): IdeaBuildTool {
    if (compilerXmlPath.notExists()) return this

    val xmlDoc = XmlBuilder.parse(compilerXmlPath)
    xmlDoc.documentElement.normalize()

    val xp: XPath = XPathFactory.newInstance().newXPath()
    val node =  xp.evaluate("//component/option[@name='BUILD_PROCESS_HEAP_SIZE']", xmlDoc, XPathConstants.NODE) as Element
    node.removeAttribute("value")
    node.setAttribute("value", "$heapSizeMb")
    XmlBuilder.writeDocument(xmlDoc, compilerXmlPath)

    return this
  }

  fun addBuildVmOption(key: String, value: String): IdeaBuildTool {
    if (compilerXmlPath.notExists()) return this

    val xmlDoc = XmlBuilder.parse(compilerXmlPath)
    xmlDoc.documentElement.normalize()

    val xp: XPath = XPathFactory.newInstance().newXPath()
    val node =  xp.evaluate("//component/option[@name='BUILD_PROCESS_ADDITIONAL_VM_OPTIONS']", xmlDoc, XPathConstants.NODE) as Element
    val oldValue = node.getAttribute("value")

    if (oldValue.contains(value)) return this

    val newValue = "$oldValue -D$key=$value"
    node.removeAttribute("value")
    node.setAttribute("value", newValue)
    XmlBuilder.writeDocument(xmlDoc, compilerXmlPath)

    return this
  }
}