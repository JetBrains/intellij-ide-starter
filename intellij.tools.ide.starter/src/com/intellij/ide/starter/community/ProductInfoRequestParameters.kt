package com.intellij.ide.starter.community

import org.apache.http.client.utils.URIBuilder

data class ProductInfoRequestParameters(
  val type: String,
  val snapshot: String = "release",
  // e.g "2022.2"
  val majorVersion: String = "",
  // e.g  "221.5591.52",
  val buildNumber: String = "",
  // e.g "2022.1.1"
  val versionNumber: String = ""
) {
  /**
   * API seems to filter only by code and type. It doesn't respond to majorVersion, build or version params
   */
  fun toUriQuery(): String {
    val builder = URIBuilder()

    if (type.isNotBlank()) builder.addParameter("code", type)
    if (snapshot.isNotBlank()) builder.addParameter("type", snapshot)

    return builder.toString()
  }
}
