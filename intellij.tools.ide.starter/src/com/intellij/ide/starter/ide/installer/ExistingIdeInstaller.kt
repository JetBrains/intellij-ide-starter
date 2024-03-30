package com.intellij.ide.starter.ide.installer

import com.intellij.ide.starter.ide.IdeDistributionFactory
import com.intellij.ide.starter.ide.IdeInstaller
import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.openapi.util.SystemInfo
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

/**
 * Use existing installed IDE instead of downloading one.
 * Set it as:
 * `bindSingleton<IdeInstallerFactory>(overrides = true) {
 *    object : IdeInstallerFactory() {
 *       override fun createInstaller(ideInfo: IdeInfo, downloader: IdeDownloader): IdeInstaller {
 *         return ExistingIdeInstaller(Paths.get(pathToInstalledIDE))
 *       }
 *     }
 * }`
 */
class ExistingIdeInstaller(private val installedIdePath: Path) : IdeInstaller {
  override suspend fun install(ideInfo: IdeInfo): Pair<String, InstalledIde> {
    val ideInstaller = IdeInstallerFile(installedIdePath, "locally-installed-ide")
    val installDir = GlobalPaths.instance
                       .getCacheDirectoryFor("builds") / "${ideInfo.productCode}-${ideInstaller.buildNumber}"
    installDir.toFile().deleteRecursively()
    val installedIde = installedIdePath.toFile()
    val destDir = installDir.resolve(installedIdePath.name).toFile()
    if (SystemInfo.isMac) {
      ProcessExecutor("copy app", null, 5.minutes, emptyMap(), listOf("ditto", installedIde.absolutePath, destDir.absolutePath)).start()
    }
    else {
      FileUtils.copyDirectory(installedIde, destDir)
    }
    return Pair(
      ideInstaller.buildNumber,
      IdeDistributionFactory.installIDE(installDir.toFile(), ideInfo.executableFileName)
    )
  }
}