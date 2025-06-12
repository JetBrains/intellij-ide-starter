package com.intellij.ide.starter.utils

import com.intellij.util.createDocumentBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import java.util.stream.IntStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object XmlBuilder {

  fun parse(inputStream: InputStream): Document {
    val documentBuilder = createDocumentBuilder()
    val xmlDoc = documentBuilder.parse(inputStream)
    xmlDoc.documentElement.normalize()
    return xmlDoc
  }

  fun parse(path: Path): Document {
    val documentBuilder = createDocumentBuilder()
    if (path.notExists()) throw FileNotFoundException(path.toString())

    val xmlDoc = documentBuilder.parse(path.toFile())
    xmlDoc.documentElement.normalize()
    return xmlDoc
  }

  fun writeDocument(xmlDoc: Document, outputPath: Path) {
    val source = DOMSource(xmlDoc)

    val transformerFactory = TransformerFactory.newDefaultInstance()
    val transformer = transformerFactory.newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

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