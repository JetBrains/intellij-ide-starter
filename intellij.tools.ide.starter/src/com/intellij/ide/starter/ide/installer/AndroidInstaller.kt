package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeArchiveExtractor
import com.intellij.ide.starter.ide.DefaultIdeDistributionFactory
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.openapi.util.SystemInfo
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.File
import java.nio.file.Path
import kotlin.io.path.div

internal class AndroidInstaller : IdeInstaller {

  /**
   * Resolve platform specific android studio installer and return paths
   * @return Pair<InstallDir / InstallerFile>
   */
  private fun downloadAndroidStudio(buildNumber: String): Pair<Path, File> {
    val ext = when {
      SystemInfo.isWindows -> "-windows.zip"
      SystemInfo.isAarch64 && SystemInfo.isMac -> "-mac_arm.dmg"
      SystemInfo.isMac -> "-mac.dmg"
      SystemInfo.isLinux -> "-linux.tar.gz"
      else -> error("Not supported OS")
    }

    val path = when {
      SystemInfo.isMac -> "install"
      else ->  "ide-zips"
    }

    val downloadUrl = "https://redirector.gvt1.com/edgedl/android/studio/$path/$buildNumber/android-studio-$buildNumber$ext"
    val asFileName = downloadUrl.split("/").last()
    val globalPaths by di.instance<GlobalPaths>()
    val zipFile = globalPaths.getCacheDirectoryFor("android-studio").resolve(asFileName)
    HttpClient.downloadIfMissing(downloadUrl, zipFile)

    val installDir = globalPaths.getCacheDirectoryFor("builds") / "AI-$buildNumber"

    installDir.toFile().deleteRecursively()

    val installerFile = zipFile.toFile()

    return Pair(installDir, installerFile)
  }

  override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val installDir: Path
    val installerFile: File

    if (ideInfo.buildNumber.isBlank()) {
      throw IllegalArgumentException("Build is not specified, please, provide buildNumber as IdeProductProvider.AI.copy(buildNumber = \"2023.1.1.28\")")
    }
    downloadAndroidStudio(ideInfo.buildNumber).also {
      installDir = it.first
      installerFile = it.second
    }
    IdeArchiveExtractor.unpackIdeIfNeeded(installerFile, installDir.toFile())
    val installationPath = when (!SystemInfo.isMac) {
      true -> installDir.resolve("android-studio")
      false -> installDir
    }
    val ide = di.direct.instance<IdeDistributionFactory>().installIDE(installationPath.toFile(), ideInfo.executableFileName)
    return Pair(ide.build, ide)
  }
}