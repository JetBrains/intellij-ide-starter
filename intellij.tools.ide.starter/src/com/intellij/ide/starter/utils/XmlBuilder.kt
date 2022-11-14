package com.intellij.ide.starter.utils

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.*
import java.util.stream.IntStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object XmlBuilder {
  private val documentBuilder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder()

  fun parse(fileInputStream: FileInputStream): Document {
    val xmlDoc = documentBuilder.parse(fileInputStream)
    xmlDoc.documentElement.normalize()

    requireNotNull(xmlDoc) { "Parsed xml document is null" }

    return xmlDoc
  }

  fun parse(path: Path): Document {
    if (path.notExists()) throw FileNotFoundException(path.toString())

    val xmlDoc = documentBuilder.parse(path.toFile())
    xmlDoc.documentElement.normalize()

    requireNotNull(xmlDoc) { "Parsed xml document at $path is null" }

    return xmlDoc
  }

  fun createDocument(): Document {
    return documentBuilder.newDocument()
  }

  fun writeDocument(xmlDoc: Document, outputPath: Path) {
    val source = DOMSource(xmlDoc)

    val transformerFactory = TransformerFactory.newInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    outputPath.outputStream().use {
      transformer.transform(source, StreamResult(it))
    }
  }

  fun findNode(nodes: NodeList, filter: (Element) -> Boolean): Optional<Element> {
    return IntStream
      .range(0, nodes.length)
      .mapToObj { i -> nodes.item(i) as Element }
      .filter(filter)
      .findAny()
  }
}