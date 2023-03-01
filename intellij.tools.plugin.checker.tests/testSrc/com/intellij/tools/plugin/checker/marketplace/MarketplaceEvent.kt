package com.intellij.tools.plugin.checker.marketplace

/**
 * {
 *   "id": 1213365,
 *   "verificationType": "IDE_PERFORMANCE",
 *   "file": "https://plugins.jetbrains.com/files/21109/300711/mybatis-code-generator-1.0.5.zip",
 *   "productCode": "IE",
 *   "productVersion": "IE-222.4345.35",
 *   "productLink": "https://download.jetbrains.com/idea/ideaIE-2022.2.2.tar.gz",
 *   "productType": "release",
 *   "pluginId": 21109,
 *   "pricingModel": "FREE"
 * }
 * }
 */
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

  /** Eg: release, rc, eap ... */
  val productType: String?,

  /** Eg: plugin id in marketplace*/
  val pluginId: Int,

  /** Eg: FREE*/
  val pricingModel: String
) {
  /** Removes product code from product version */
  fun getNumericProductVersion() = productVersion.split("-").last()
}
