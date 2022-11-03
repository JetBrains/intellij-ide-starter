package com.intellij.tools.plugin.checker.marketplace

// https://jetbrains.team/p/mp/documents/General/a/External-Services-Protocol
data class MarketplaceEvent(
  val id: Int,
  /** Eg: https://master.dev.marketplace.intellij.net/files/master/10080/122595/intellij-rainbow-brackets-6.18.zip */
  val file: String,

  /** Eg: GO */
  val productCode: String,

  /** Eg: GO-222.4345.24 */
  val productVersion: String,

  /** Eg: https://download.jetbrains.com/go/goland-2022.2.4.tar.gz */
  val productLink: String,

  /** Eg: release */
  val productType: String?,

  /** Eg: /files/master/10080/122595/intellij-rainbow-brackets-6.18.zip */
  val s3Path: String,
  val forced: Boolean?
) {
  /** Removes product code from product version */
  fun getNumericProductVersion() = productVersion.split("-").last()
}
