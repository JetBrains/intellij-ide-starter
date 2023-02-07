package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstallator
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.openapi.util.io.FileUtil
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Use existing installed IDE instead of downloading one.
 * Set it as:
 * `bindFactory<IdeInfo, IdeInstallator>(overrides = true) { _ -> ExistingIdeInstallator(Paths.get(pathToInstalledIDE)) }`
 */
class ExistingIdeInstallator(private val installedIdePath: Path) : IdeInstallator {
  override fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val ideInstaller = IdeInstaller(installedIdePath, "locally-installed-ide")
    val installDir = di.direct.instance<GlobalPaths>()
                       .getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
    installDir.toFile().deleteRecursively()
    FileUtil.copyDir(installedIdePath.toFile(), installDir.toFile())
    return Pair(
      ideInstaller.buildNumber,
      IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName)
    )
  }
}