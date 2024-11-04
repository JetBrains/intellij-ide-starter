package com.intellij.ide.starter.models

import com.intellij.ide.starter.community.model.BuildType
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.installer.IdeInstallerFactory
import com.intellij.openapi.util.SystemInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.net.URI

data class IdeInfo(
  /**
   * Eg: IU, PY, GO ...
   */
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

  val downloadURI: URI? = null,

  /**QDJVM, QDGO, QDJVMC ... */
  val qodanaProductCode: String? = null,

  val fullName: String,

  val getInstaller: (IdeInfo) -> IdeInstaller = { di.direct.instance<IdeInstallerFactory>().createInstaller(it) }
) {
  companion object

  val installerFilePrefix
    get() =
      when (productCode) {
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
        "QA" -> "aqua"
        "RR" -> "RustRover"
        "AI" -> "AndroidStudio"
        "JBC" -> "JetBrainsClient"
        "RD" -> "rider"
        "WRS" -> "writerside"
        else -> error("Unknown product code: $productCode")
      }

  val installerProductName
    get() = when (productCode) {
      "IU" -> "intellij"
      "IC" -> "intellij.ce"
      "RM" -> "rubymine"
      "PY" -> "pycharm"
      "RR" -> "RustRover"
      else -> installerFilePrefix
    }

  val installerFileExt
    get() = when {
      SystemInfo.isWindows -> ".exe"
      SystemInfo.isLinux -> ".tar.gz"
      SystemInfo.isMac -> when (System.getProperty("os.arch")) {
        "x86_64" -> ".dmg"
        "aarch64" -> "-aarch64.dmg"
        else -> error("Unknown architecture of Mac OS")
      }
      else -> error("Unknown OS")
    }

  val identity: String
    get() {
      val suffix = when {
        version.contains("-EAP") -> ""
        buildType == BuildType.EAP.type -> "-EAP"
        else -> ""
      }
      return "$productCode-$platformPrefix-$version$suffix"
    }

}
