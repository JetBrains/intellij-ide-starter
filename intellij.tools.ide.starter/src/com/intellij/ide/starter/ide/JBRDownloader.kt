package com.intellij.ide.starter.ide

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.runner.SetupException
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

object JBRDownloader {

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

  fun downloadAndUnpackJbrFromBuildIfNeeded(jbrFullVersion: String): Path {
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromBuild(jbrFullVersion)) } ?: throw JBRDownloadException(jbrFullVersion)
  }

  fun downloadAndUnpackJbrFromSourcesIfNeeded(jbrFullVersion: String): Path {
    return catchAll { downloadAndUnpackJbrIfNeeded(getJBRVersionFromSources(jbrFullVersion))} ?: throw JBRDownloadException(jbrFullVersion)
  }

  private fun downloadAndUnpackJbrIfNeeded(jbrVersion: JBRVersion): Path {
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
    val path = try {
      downloadJbr(jbrFileName)
    }
    catch (_: HttpClient.HttpNotFound) {
      downloadJbr("jbrsdk_nomod-$majorVersion-$os-$arch-b$buildNumber.tar.gz")
    }

    val appHome = (path.toFile().listFiles() ?: arrayOf()).singleOrNull { it.isDirectory }?.toPath()
    requireNotNull(appHome) {
      "appHome is null: $appHome"
    }
    when {
      SystemInfo.isMac -> return appHome / "Contents" / "Home"
    }
    return appHome
  }

  private fun downloadJbr(jbrFileName: String): Path {
    val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$jbrFileName"

    val jbrCacheDirectory = GlobalPaths.instance.getCacheDirectoryFor("jbr")
    val localFile = jbrCacheDirectory / jbrFileName
    val localDir = jbrCacheDirectory / jbrFileName.removeSuffix(".tar.gz")

    HttpClient.downloadIfMissing(downloadUrl, localFile, retries = 1, timeout = 5.minutes)

    FileSystem.unpackIfMissing(localFile, localDir)

    return localDir
  }
}