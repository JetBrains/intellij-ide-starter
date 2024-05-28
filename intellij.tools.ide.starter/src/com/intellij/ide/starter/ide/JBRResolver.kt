package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path

object JBRResolver {
  data class JBRVersion(val majorVersion: String, val buildNumber: String)
  class JBRDownloadException(jbrFullVersion: String) : SetupException("$jbrFullVersion can't be downloaded/unpacked")

  private fun getJBRVersionFromBuild(jbrFullVersion: String): JBRVersion {
    val jbrVersion = jbrFullVersion.split(".")[0].toInt()
    var majorVersion = jbrFullVersion.split("+").firstOrNull()
    if (jbrVersion < 17) {
      majorVersion = majorVersion?.replace(".", "_")
    }
    requireNotNull(majorVersion) {
      { "majorVersion is: $majorVersion" }
    }
    val buildNumber = jbrFullVersion.split("-b").drop(1).singleOrNull()
    requireNotNull(buildNumber) {
      { "buildNumber is: $buildNumber" }
    }
    logOutput("Detected JBR version $jbrFullVersion with parts: $majorVersion and build $buildNumber")
    return JBRVersion(majorVersion, buildNumber)
  }

  private fun getJBRVersionFromSources(jbrFullVersion: String): JBRVersion {
    val jbrVersion = jbrFullVersion.split(".")[0].toInt()
    var majorVersion = jbrFullVersion.split("b").firstOrNull()
    if (jbrVersion < 17) {
      majorVersion = majorVersion?.replace(".", "_")
    }
    requireNotNull(majorVersion) {
      { "majorVersion is: $majorVersion" }
    }
    val buildNumber = jbrFullVersion.split("b").drop(1).singleOrNull()
    requireNotNull(buildNumber) {
      { "buildNumber is: $buildNumber" }
    }
    logOutput("Detected JBR version $jbrFullVersion with parts: $majorVersion and build $buildNumber")
    return JBRVersion(majorVersion, buildNumber)
  }

  suspend fun downloadAndUnpackJbrFromBuildIfNeeded(jbrFullVersion: String): Path {
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromBuild(jbrFullVersion)) } ?: throw JBRDownloadException(jbrFullVersion)
  }

  suspend fun downloadAndUnpackJbrFromSourcesIfNeeded(jbrFullVersion: String): Path {
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromSources(jbrFullVersion)) } ?: throw JBRDownloadException(jbrFullVersion)
  }

  private suspend fun downloadAndUnpackJbrIfNeeded(jbrVersion: JBRVersion): Path {
    val (majorVersion, buildNumber) = listOf(jbrVersion.majorVersion, jbrVersion.buildNumber)

    val os = when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isLinux -> "linux"
      SystemInfo.isMac -> "osx"
      else -> error("Unknown OS")
    }

    val arch = when (SystemInfo.isAarch64) {
      true -> "aarch64"
      false -> "x64"
    }

    val jbrFileName = "jbrsdk_jcef-$majorVersion-$os-$arch-b$buildNumber.tar.gz"
    val appHome = withContext(Dispatchers.IO) {
      di.direct.instance<JBRDownloader>().downloadJbr(jbrFileName)
    }
    return if (SystemInfo.isMac) appHome.resolve("Contents/Home") else appHome
  }
}