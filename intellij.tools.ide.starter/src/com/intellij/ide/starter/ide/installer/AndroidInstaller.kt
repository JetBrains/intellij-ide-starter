package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeArchiveExtractor
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.openapi.util.SystemInfo
import com.intellij.ide.starter.utils.HttpClient
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

class AndroidInstaller : IdeInstaller {

  /**
   * Resolve platform specific android studio installer and return paths
   * @return Pair<InstallDir / InstallerFile>
   */
  private fun downloadAndroidStudio(): Pair<Path, File> {
    val ext = when {
      SystemInfo.isWindows -> "-windows.zip"
      SystemInfo.isMac -> "-mac.zip"
      SystemInfo.isLinux -> "-linux.tar.gz"
      else -> error("Not supported OS")
    }

    val downloadUrl = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2021.1.1.11/android-studio-2021.1.1.11$ext"
    val asFileName = downloadUrl.split("/").last()
    val globalPaths by di.instance<GlobalPaths>()
    val zipFile = globalPaths.getCacheDirectoryFor("android-studio").resolve(asFileName)
    HttpClient.downloadIfMissing(downloadUrl, zipFile)

    val installDir = globalPaths.getCacheDirectoryFor("builds") / "AI-211"

    installDir.toFile().deleteRecursively()

    val installerFile = zipFile.toFile()

    return Pair(installDir, installerFile)
  }

  override fun install(ideInfo: IdeInfo, includeRuntimeModuleRepository: Boolean): Pair<String, InstalledIde> {

    val installDir: Path
    val installerFile: File

    downloadAndroidStudio().also {
      installDir = it.first
      installerFile = it.second
    }
    IdeArchiveExtractor.unpackIdeIfNeeded(installerFile, installDir.toFile())
    val installationPath = when (!SystemInfo.isMac) {
      true -> installDir.resolve("android-studio")
      false -> installDir
    }
    val ide = IdeDistributionFactory.installIDE(installationPath.toFile(), ideInfo.executableFileName)
    return Pair(ide.build, ide)
  }
}