package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class WindowsIdeDistribution : IdeDistribution() {
  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    val (productCode, build) = readProductCodeAndBuildNumberFromBuildTxt(unpackDir.resolve("build.txt"))

    val binDir = unpackDir / "bin"

    val allBinFiles = binDir.listDirectoryEntries()

    val executablePath = allBinFiles.singleOrNull { file ->
      file.fileName.toString() == "${executableFileName}64.exe"
    } ?: error("Failed to detect executable ${executableFileName}64.exe:\n${allBinFiles.joinToString("\n")}")

    return object : InstalledIde {
      override val bundledPluginsDir = unpackDir.resolve("plugins")

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      override val vmOptions: VMOptions
        get() = vmOptionsFinal

      override val patchedVMOptionsFile = unpackDir.parent.resolve("${unpackDir.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) = object : InstalledBackedIDEStartConfig(patchedVMOptionsFile,
                                                                                                             vmOptions) {
        override val workDir = unpackDir
        override val commandLine = listOf(executablePath.toAbsolutePath().toString())
      }

      override val build = build
      override val os = OS.Windows
      override val productCode = productCode
      override val isFromSources = false
      override val installationPath: Path = unpackDir.toAbsolutePath()

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = unpackDir / "jbr"
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = JvmUtils.callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")

        // in Android Studio bundled only JRE
        if (productCode == IdeProductProvider.AI.productCode) return jbrHome
        return JBRResolver.downloadAndUnpackJbrFromBuildIfNeeded(jbrFullVersion)
      }
    }
  }
}