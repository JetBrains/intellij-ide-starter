package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

class DockerIdeDistribution : IdeDistribution() {
  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    return object : InstalledIde {

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      val appDir = Path("/opt/idea")
      override val vmOptions: VMOptions
        get() = vmOptionsFinal
      override val build: String
        get() = "SNAPSHOT"
      override val os: OS
        get() = OS.Linux
      override val productCode: String
        get() = "IU"
      override val isFromSources: Boolean
        get() = false
      override val installationPath: Path
        get() = appDir
      override val patchedVMOptionsFile = unpackDir.parent.resolve("${appDir.fileName}.vmoptions") //see IDEA-220286

      override fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig {
        return object : InstalledBackedIDEStartConfig(patchedVMOptionsFile, vmOptions) {
          override val workDir = appDir
          override val commandLine = emptyList<String>()
        }
      }

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appDir.resolve("jbr")
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = JvmUtils.callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")
        return jbrHome
      }
    }
  }
}