package com.intellij.ide.starter.ide

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

abstract class IdeDistribution {
  abstract fun installIde(unpackDir: Path, executableFileName: String): InstalledIde

  private fun downloadJbr(jbrFileName: String): Path {
    val downloadUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$jbrFileName"

    val jbrCacheDirectory = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("jbr")
    val localFile = jbrCacheDirectory / jbrFileName
    val localDir = jbrCacheDirectory / jbrFileName.removeSuffix(".tar.gz")

    HttpClient.downloadIfMissing(downloadUrl, localFile, retries = 1, timeout = 5.minutes)

    FileSystem.unpackIfMissing(localFile, localDir)

    return localDir
  }

  protected fun downloadAndUnpackJbrIfNeeded(jbrFullVersion: String): Path {
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

    val os = when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isLinux -> "linux"
      SystemInfo.isMac -> "osx"
      else -> error("Unknown OS")
    }

    val arch = when (SystemInfo.isMac) {
      true -> when (System.getProperty("os.arch")) {
        "x86_64" -> "x64"
        "aarch64" -> "aarch64"
        else -> error("Unsupported architecture of Mac OS")
      }
      false -> "x64"
    }

    val jbrFileName = "jbrsdk-$majorVersion-$os-$arch-b$buildNumber.tar.gz"
    val path = try {
      downloadJbr(jbrFileName)
    }
    catch (e: HttpClient.HttpNotFound) {
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
}