package com.intellij.ide.starter.community

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.ide.starter.community.model.ReleaseInfo
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.tools.ide.util.common.logOutput
import org.apache.http.client.methods.HttpGet


object JetBrainsDataServiceClient {
  private const val DATA_SERVICE_URL = "https://data.services.jetbrains.com"
  private const val RELEASES_REPO_URL = "https://www.jetbrains.com/intellij-repository/releases/"

  fun getIdeaIUAnnotations(libVersion: String): List<String> {
    val getUrlToJbDataServices = RELEASES_REPO_URL
    val annotationRegex = "idea/ideaIU/${libVersion}.*?/ideaIU-${libVersion}.*?-annotations.zip".toRegex()
    val versionRegex = "\\d{4}\\.\\d+(\\.\\d+)?".toRegex()
    return HttpClient.sendRequest(HttpGet(getUrlToJbDataServices)) {
      val result = annotationRegex.findAll(it.entity.content.bufferedReader().readText()).map { match -> versionRegex.find(match.value)!!.value }.toList()
      return@sendRequest result
    }
  }


  fun getReleases(request: ProductInfoRequestParameters): Map<String, List<ReleaseInfo>> {
    val getUrlToJbDataServices = "$DATA_SERVICE_URL/products/releases${request.toUriQuery()}"
    logOutput("Requesting JetBrains products by url: $getUrlToJbDataServices")

    return HttpClient.sendRequest(
      HttpGet(getUrlToJbDataServices).apply {
        addHeader("Content-Type", "application/json")
        addHeader("Accept", "application/json")
      }) {
      jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())
        .readValue(it.entity.content, object : TypeReference<Map<String, List<ReleaseInfo>>>() {})
    }
  }

  fun getLatestPublicReleases(productType: String,
                              numberOfReleases: Int = Int.MAX_VALUE,
                              maxReleaseBuild: String? = null): List<ReleaseInfo> {
    return getReleases(ProductInfoRequestParameters(productType))
      .values
      .first()
      .let {
        if (maxReleaseBuild != null) it.filter { release -> release.build.substringBefore(".").toInt() <= maxReleaseBuild.toInt() } else it
      }
      .take(numberOfReleases)
  }

  /**
   * @param productType What product releases do you need. UI/GO/AI etc.
   * @param numberOfReleases How many latest releases do you need. 1/5/10 etc.
   * @param maxReleaseBuild What is the newest release you need. 223/222/213 etc
   *
   * @return List of releases sorted from newest to oldest. 2023.1/2022.3 etc
   * */
  fun getLatestPublicReleaseVersions(productType: String,
                                     numberOfReleases: Int = Int.MAX_VALUE,
                                     maxReleaseBuild: String? = null): List<String> {
    // because there might be multiple releases with the same major version we need to filter it as an additional step
    return getLatestPublicReleases(productType, numberOfReleases = Int.MAX_VALUE, maxReleaseBuild)
      .map { it.majorVersion }
      .toSet()
      .take(numberOfReleases)
  }
}
