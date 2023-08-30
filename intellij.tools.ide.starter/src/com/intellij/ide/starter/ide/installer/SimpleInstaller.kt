package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.ide.IdeArchiveExtractor
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstallator
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.logOutput
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class SimpleInstaller : IdeInstallator {

  override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val installersDirectory = (GlobalPaths.instance.installersDirectory / ideInfo.productCode).createDirectories()

    //Download
    val ideInstaller = ideInfo.download(installersDirectory)
    val installDir = GlobalPaths.instance.getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"

    if (ideInstaller.buildNumber == "SNAPSHOT") {
      logOutput("Cleaning up SNAPSHOT IDE installation $installDir")
      installDir.toFile().deleteRecursively()
    }

    //Unpack
    IdeArchiveExtractor.unpackIdeIfNeeded(ideInstaller.installerFile.toFile(), installDir.toFile())

    //Install
    return Pair(ideInstaller.buildNumber, IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName))
  }
}