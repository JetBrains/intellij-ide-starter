package com.intellij.ide.starter.models

import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.system.SystemInfo
import java.net.URI

data class IdeInfo(
  val productCode: String,

  /**
   * Eg: idea, Idea, GoLand, WebStorm ...
   */
  val platformPrefix: String,

  val executableFileName: String,
  val buildType: String = BuildType.EAP.type,
  val additionalModules: List<String> = emptyList(),

  /** E.g: "222.3244.1" */
  val buildNumber: String = "",

  /** E.g: "2022.1.2" */
  val version: String = "",

  val tag: String? = null,

  val downloadURI: URI? = null
) {
  companion object

  val installerFilePrefix
    get() = when (productCode) {
      "IU" -> "ideaIU"
      "IC" -> "ideaIC"
      "WS" -> "WebStorm"
      "PS" -> "PhpStorm"
      "DB" -> "datagrip"
      "GO" -> "goland"
      "RM" -> "RubyMine"
      "PY" -> "pycharmPY"
      "CL" -> "CLion"
      "DS" -> "dataspell"
      "PC" -> "pycharmPC"
      else -> error("Unknown product code: $productCode")
    }

  val installerProductName
    get() = when (productCode) {
      "IU" -> "intellij"
      "IC" -> "intellij.ce"
      "RM" -> "rubymine"
      "PY" -> "pycharm"
      else -> installerFilePrefix
    }

  val installerFileExt
    get() = when {
      SystemInfo.isWindows -> ".win.zip"
      SystemInfo.isLinux -> ".tar.gz"
      SystemInfo.isMac -> when (System.getProperty("os.arch")) {
        "x86_64" -> ".dmg"
        "aarch64" -> "-aarch64.dmg"
        else -> error("Unknown architecture of Mac OS")
      }
      else -> error("Unknown OS")
    }
}
