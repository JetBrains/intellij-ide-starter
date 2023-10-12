package com.intellij.ide.starter.sdk

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.openapi.projectRoots.impl.jdkDownloader.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.common.logOutput
import com.intellij.tools.ide.common.withRetry
import org.apache.commons.io.FileUtils
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object JdkDownloaderFacade {

  val jdk8 get() = jdkDownloader(JdkVersion.JDK_8.toString())
  val jdk11 get() = jdkDownloader(JdkVersion.JDK_11.toString())
  val jdk17 get() = jdkDownloader(JdkVersion.JDK_17.toString())
  val jbrJcef17 get() = jdkDownloader(JdkVersion.JDK_17.toString(), jbr = true)
  val jdk21 get() = jdkDownloader(JdkVersion.JDK_21.toString())
  val jbr21 get() = jdkDownloader(JdkVersion.JDK_21.toString(), jbr = true)

  const val MINIMUM_JDK_FILES_COUNT = 42

  fun jdkDownloader(version: String, jdks: Iterable<JdkDownloadItem> = allJdks, jbr: Boolean = false): JdkDownloadItem {
    val jdkName =
      when (jbr) {
        true -> "jbr"
        else -> "corretto"
      }
    return jdks.single {
      it.jdk.sharedIndexAliases.contains("$jdkName-$version")
    }
  }

  val allJdks by lazy {
    listJDKs(JdkPredicate.forCurrentProcess())
  }

  val allJdksForWSL by lazy {
    listJDKs(JdkPredicate.forWSL(null))
  }

  private fun listJDKs(predicate: JdkPredicate): List<JdkDownloadItem> {
    val allJDKs = JdkListDownloader().downloadModelForJdkInstaller(null, predicate)
    logOutput("Total JDKs: ${allJDKs.map { it.fullPresentationText }}")

    val allVersions = allJDKs.map { it.jdkVersion }.toSortedSet()
    logOutput("JDK versions: $allVersions")

    return allJDKs.map { jdk ->
      JdkDownloadItem(jdk) {
        downloadJdkItem(jdk, predicate)
      }
    }
  }

  private fun downloadJdkItem(jdk: JdkItem, predicate: JdkPredicate): JdkItemPaths {
    val targetJdkHome = determineTargetJdkHome(predicate, jdk)
    val targetHomeMarker = targetJdkHome.resolve("home.link")
    logOutput("Checking JDK at $targetJdkHome")

    if (shouldDownloadJdk(targetJdkHome, targetHomeMarker)) {
      downloadAndInstallJdk(jdk, targetJdkHome, targetHomeMarker)
    }

    val javaHome = File(Files.readString(targetHomeMarker))
    require(javaHome.resolve(getJavaBin(predicate)).isFile) {
      FileUtils.deleteQuietly(targetJdkHome.toFile())
      "corrupted JDK home: $targetJdkHome (now deleted)"
    }

    return JdkItemPaths(homePath = javaHome.toPath(), installPath = targetJdkHome)
  }

  private fun determineTargetJdkHome(predicate: JdkPredicate, jdk: JdkItem): Path =
    if (isWSL(predicate)) {
      Path.of(WslDistributionManager.getInstance().installedDistributions[0].getWindowsPath("/tmp/jdks/${jdk.installFolderName}"))
    }
    else {
      GlobalPaths.instance.getCacheDirectoryFor("jdks").resolve(jdk.installFolderName)
    }

  private fun isWSL(predicate: JdkPredicate): Boolean = predicate == JdkPredicate.forWSL(null)
                                                        && SystemInfo.isWindows
                                                        && WslDistributionManager.getInstance().installedDistributions.isNotEmpty()

  private fun shouldDownloadJdk(targetJdkHome: Path, targetHomeMarker: Path): Boolean =
    !Files.isRegularFile(targetHomeMarker) || FileUtils.listFiles(targetJdkHome.toFile(), null, true).size < MINIMUM_JDK_FILES_COUNT

  private fun downloadAndInstallJdk(jdk: JdkItem, targetJdkHome: Path, targetHomeMarker: Path) {
    withRetry(messageOnFailure = "Failure on downloading/installing JDK", retries = 5) {
      logOutput("Downloading JDK at $targetJdkHome")
      FileUtils.deleteQuietly(targetJdkHome.toFile())

      val jdkInstaller = JdkInstaller()
      val request = jdkInstaller.prepareJdkInstallationDirect(jdk, targetPath = targetJdkHome)
      jdkInstaller.installJdk(request, targetHomeMarker)
    }
  }

  private fun downloadFileFromUrl(urlString: String, destinationPath: Path) {
    FileUtils.createParentDirectories(destinationPath.toFile())
    URL(urlString).openConnection().getInputStream().use { inputStream ->
      Files.copy(inputStream, destinationPath, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun JdkInstaller.installJdk(request: JdkInstallRequest, markerFile: Path) {
    val item = request.item
    val targetDir = request.installDir
    val wslDistribution = wslDistributionFromPath(targetDir)
    if (wslDistribution != null && item.os != "linux") {
      error("Cannot install non-linux JDK into WSL environment to $targetDir from $item")
    }
    val temp = GlobalPaths.instance.testHomePath.resolve("tmp/jdk").toAbsolutePath().toString()
    val downloadFile = Paths.get(temp, "jdk-${System.nanoTime()}-${item.archiveFileName}")
    try {
      try {
        downloadFileFromUrl(item.url, downloadFile)
        if (!downloadFile.toFile().isFile) {
          throw RuntimeException("Downloaded file does not exist: $downloadFile")
        }
      }
      catch (t: Throwable) {
        throw RuntimeException("Failed to download ${item.fullPresentationText} from ${item.url}: ${t.message}", t)
      }

      try {
        if (wslDistribution != null) {
          JdkInstallerWSL.unpackJdkOnWsl(wslDistribution, item.packageType, downloadFile, targetDir, item.packageRootPrefix)
        }
        else {
          item.packageType.openDecompressor(downloadFile).let {
            val fullMatchPath = item.packageRootPrefix.trim('/')
            if (fullMatchPath.isBlank()) it else it.removePrefixPath(fullMatchPath)
          }.extract(targetDir)
        }
      }
      catch (t: Throwable) {
        throw RuntimeException("Failed to extract ${item.fullPresentationText}. ${t.message}", t)
      }
      Files.writeString(markerFile, request.javaHome.toRealPath().toString(), Charsets.UTF_8)
    }
    finally {
      FileUtils.deleteQuietly(downloadFile.toFile())
    }
  }

  private fun getJavaBin(predicate: JdkPredicate): String = "bin/java" + when {
    (SystemInfo.isWindows && predicate != JdkPredicate.forWSL(null)) -> ".exe"
    else -> ""
  }
}
