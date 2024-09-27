package com.intellij.tools.plugin.checker.marketplace


import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.w3c.dom.Element
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

data class Plugin(
    val id: String,
    val name: String,
    val version: String
)

object MarketplaceClient {
    private const val BASE_URL = "https://plugins.jetbrains.com"
    private val client: CloseableHttpClient = HttpClients.createDefault()

    fun getPluginsForBuild(productCode: String, build: String): List<Plugin> {
        val url = "$BASE_URL/plugins/list/?build=${productCode}-${build}"
        val httpGet = HttpGet(url)


        val response = client.execute(httpGet)
        val entity: HttpEntity = response.entity ?: throw IOException("Empty response body")

        return if (response.statusLine.statusCode == 200) {
            val xmlResponse = EntityUtils.toString(entity)
            parsePluginsFromXml(xmlResponse)
        } else {
            throw IOException("Failed to get plugins: ${response.statusLine.statusCode}")
        }
    }

    fun downloadPlugin(plugin: Plugin, destinationZip: File) {
        val url = "$BASE_URL/plugin/download?pluginId=${URLEncoder.encode(plugin.id)}&version=${plugin.version}"
        val httpGet = HttpGet(url)


        val response = client.execute(httpGet)
        if (response.statusLine.statusCode != 200) {
            throw IOException("Failed to download plugin: ${response.statusLine.statusCode}")
        }

        val entity = response.entity
        entity.content.use { inputStream ->
            FileOutputStream(destinationZip).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    private fun parsePluginsFromXml(xml: String): List<Plugin> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val xmlInput = xml.byteInputStream()
        val document = builder.parse(xmlInput)

        val pluginList = mutableListOf<Plugin>()
        val pluginNodes = document.getElementsByTagName("idea-plugin")

        for (i in 0 until pluginNodes.length) {
            val pluginElement = pluginNodes.item(i) as Element

            val name = pluginElement.getElementsByTagName("name").item(0).textContent
            val id = pluginElement.getElementsByTagName("id").item(0).textContent
            val version = pluginElement.getElementsByTagName("version").item(0).textContent

            val plugin = Plugin(
                id = id,
                name = name,
                version = version
            )

            pluginList.add(plugin)
        }

        return pluginList
    }
}
