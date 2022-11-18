package com.intellij.ide.starter.community

import com.intellij.ide.starter.ide.IdeDownloader
import com.intellij.ide.starter.ide.installer.IdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Path
import kotlin.io.path.exists

object IdeByLinkDownloader : IdeDownloader {
  override fun downloadIdeInstaller(ideInfo: IdeInfo, installerDirectory: Path): IdeInstaller {
    requireNotNull(ideInfo.downloadURI) { "Download URI should not be null for $ideInfo" }

    val installerFile = installerDirectory.resolve(
      "${ideInfo.installerFilePrefix}-" + ideInfo.buildNumber.replace(".", "") + ideInfo.installerFileExt
    )

    if (!installerFile.exists()) {
      logOutput("Downloading $ideInfo ...")
      HttpClient.download(ideInfo.downloadURI.toString(), installerFile)
    }
    else logOutput("Installer file $installerFile already exists. Skipping download.")

    return IdeInstaller(installerFile, ideInfo.buildNumber)
  }
}