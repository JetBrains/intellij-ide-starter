package com.intellij.ide.starter.sdk

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withRetry
import com.intellij.ide.starter.wsl.WslDistributionNotFoundException
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerBase
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPredicate
import org.apache.commons.io.FileUtils
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object JdkDownloaderFacade {

  val jdk8 get() = jdkDownloader(JdkVersion.JDK_8.toString())
  val jdk11 get() = jdkDownloader(JdkVersion.JDK_11.toString())
  val jdk15 get() = jdkDownloader(JdkVersion.JDK_15.toString())
  val jdk16 get() = jdkDownloader(JdkVersion.JDK_16.toString())
  val jdk17 get() = jdkDownloader(JdkVersion.JDK_17.toString())

  fun jdkDownloader(version: String, jdks: Iterable<JdkDownloadItem> = allJdks): JdkDownloadItem {
    val jdkName = when (SystemInfo.OS_ARCH) {
      "aarch64" -> "azul"
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
    if (isWSLWindows(predicate) && WslDistributionManager.getInstance().installedDistributions.isNotEmpty()) {
      try {
        val wslDistribution = WslDistributionManager.getInstance().installedDistributions[0]
        Path.of(wslDistribution.getWindowsPath("/tmp/jdks/${jdk.installFolderName}"))
      }
      catch (_: Exception) {
        throw WslDistributionNotFoundException(predicate)
      }
    }
    else {
      di.direct.instance<GlobalPaths>().getCacheDirectoryFor("jdks").resolve(jdk.installFolderName)
    }

  private fun isWSLWindows(predicate: JdkPredicate): Boolean =
    predicate == JdkPredicate.forWSL(null) && SystemInfo.isWindows

  private fun shouldDownloadJdk(targetJdkHome: Path, targetHomeMarker: Path): Boolean =
    !Files.isRegularFile(targetHomeMarker) || !targetJdkHome.toFile().isDirectory || (runCatching {
      Files.walk(targetJdkHome).use { it.count() }
    }.getOrNull() ?: 0) < 42

  private fun downloadAndInstallJdk(jdk: JdkItem, targetJdkHome: Path, targetHomeMarker: Path) {
    withRetry(retries = 5) {
      logOutput("Downloading JDK at $targetJdkHome")
      FileUtils.deleteQuietly(targetJdkHome.toFile())

      val jdkInstaller = createJdkInstaller()
      val request = jdkInstaller.prepareJdkInstallationDirect(jdk, targetPath = targetJdkHome)
      callWithModifiedSystemPath { jdkInstaller.installJdk(request, null, null) }
      FileUtils.createParentDirectories(targetHomeMarker.toFile())
      Files.writeString(targetHomeMarker, request.javaHome.toRealPath().toString(), Charsets.UTF_8)
    }
  }

  //we need to override system path to download jdk since it's used to calculate temp folder
  private fun callWithModifiedSystemPath(action: () -> Unit) {
    val systemPathProperty = "idea.system.path"
    val storedSystemPath = System.getProperty(systemPathProperty)
    System.setProperty(systemPathProperty, di.direct.instance<GlobalPaths>().testHomePath.resolve("tmp/jdk").toAbsolutePath().toString())
    action()
    System.setProperty(systemPathProperty, storedSystemPath)
  }

  private fun createJdkInstaller(): JdkInstallerBase = object : JdkInstallerBase() {
    override fun defaultInstallDir(): Path {
      val explicitHome = System.getProperty("jdk.downloader.home")
      if (explicitHome != null) {
        return Paths.get(explicitHome)
      }

      val home = Paths.get(File(System.getProperty("user.home") ?: ".").canonicalPath)
      return when {
        SystemInfo.isLinux -> home.resolve(".jdks")
        SystemInfo.isMac -> home.resolve("Library/Java/JavaVirtualMachines")
        SystemInfo.isWindows -> home.resolve(".jdks")
        else -> error("Unsupported OS: ${SystemInfo.getOsNameAndVersion()}")
      }
    }
  }

  private fun getJavaBin(predicate: JdkPredicate): String = "bin/java" + when {
    (SystemInfo.isWindows && predicate != JdkPredicate.forWSL(null)) -> ".exe"
    else -> ""
  }
}
