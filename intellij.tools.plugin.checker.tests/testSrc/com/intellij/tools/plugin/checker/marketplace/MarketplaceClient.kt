package com.intellij.tools.plugin.checker.marketplace

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class Plugin(
    val id: String,
    val name: String,
    val updateId: String
)

object MarketplaceClient {
    private const val BASE_URL = "https://plugins.jetbrains.com"
    private val client: CloseableHttpClient = HttpClients.createDefault()
    private val objectMapper = ObjectMapper()

    fun getPluginsForBuild(productCode: String, build: String): List<Plugin> {
        val pluginsUrl = "$BASE_URL/api/search/plugins?build=${productCode}-${build}&pricingModels=FREE&max=10000"

        val pluginsResponse = client.execute(HttpGet(pluginsUrl))
        val pluginsEntity: HttpEntity = pluginsResponse.entity ?: throw IOException("Empty response body")

        if (pluginsResponse.statusLine.statusCode != 200) {
            throw IOException("Failed to get plugins: ${pluginsResponse.statusLine.statusCode}")
        }
        val allPlugins = parsePluginsFromJson(EntityUtils.toString(pluginsEntity))

        val themesUrl = "$pluginsUrl&tags=THEME"
        val themesResponse = client.execute(HttpGet(themesUrl))
        val themesEntity: HttpEntity = themesResponse.entity ?: throw IOException("Empty response body")
        if (themesResponse.statusLine.statusCode != 200) {
            throw IOException("Failed to get themes: ${themesResponse.statusLine.statusCode}")
        }
        val themePlugins = parsePluginsFromJson(EntityUtils.toString(themesEntity))
        val themeIds = themePlugins.map { it.id }.toHashSet()

        return allPlugins.filter { it.id !in themeIds }
    }

    fun downloadPlugin(plugin: Plugin, destinationZip: File) {
        val url = "$BASE_URL/plugin/download?updateId=${plugin.updateId}&noStatistic=true"
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

    private fun parsePluginsFromJson(json: String): List<Plugin> {
        val root: JsonNode = objectMapper.readTree(json)
        val pluginList = mutableListOf<Plugin>()
        if (root.isArray) {
            for (node in root) {
                val id = node.path("id")!!.asText()
                val xmlId = node.path("xmlId")!!.asText()
                val updateId = node.path("updateId")!!.asText()
              pluginList.add(
                Plugin(
                  id = id,
                  name = xmlId,
                  updateId = updateId
                )
              )
            }
        }
        return pluginList
    }
}
