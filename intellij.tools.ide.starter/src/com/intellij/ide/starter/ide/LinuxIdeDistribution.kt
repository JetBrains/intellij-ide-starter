package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_DISPLAY_ID = "88"
const val DEFAULT_DISPLAY_RESOLUTION = "1920x1080"

class LinuxIdeDistribution : IdeDistribution() {
  companion object {

    private val xvfbRunTool by lazy {
      val toolName = "xvfb-run"

      val homePath = Path(System.getProperty("user.home")).toAbsolutePath()
      ProcessExecutor("xvfb-run", homePath, timeout = 5.seconds, args = listOf("which", toolName),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-out"),
                      stderrRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-err")
      ).start()
      toolName
    }

    fun linuxCommandLine(xvfbRunLog: Path, commandEnv: Map<String, String> = emptyMap()): List<String> {
      return when {
        (System.getenv("DISPLAY") != null || commandEnv["DISPLAY"] != null) && System.getProperty("follow.display") == null -> listOf()
        else ->
          //hint https://gist.github.com/tullmann/2d8d38444c5e81a41b6d
          listOf(
            xvfbRunTool,
            "--error-file=" + xvfbRunLog.toAbsolutePath().toString(),
            "--server-args=-ac -screen 0 ${DEFAULT_DISPLAY_RESOLUTION}x24",
            "--auto-servernum",
            "--server-num=$DEFAULT_DISPLAY_ID"
          )
      }
    }

    fun createXvfbRunLog(logsDir: Path): Path {
      val logTxt = logsDir.resolve("xvfb-log.txt")
      logTxt.deleteIfExists()

      return Files.createFile(logTxt)
    }
  }

  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    require(SystemInfo.isLinux) { "Can only run on Linux, docker is possible, please PR" }

    val appHome = (unpackDir.toFile().listFiles()?.singleOrNull { it.isDirectory }?.toPath() ?: unpackDir).toAbsolutePath()
    val (productCode, build) = readProductCodeAndBuildNumberFromBuildTxt(appHome.resolve("build.txt"))

    val binDir = appHome / "bin"
    val allBinFiles = binDir.listDirectoryEntries()
    val executablePath = allBinFiles.singleOrNull { file ->
      file.fileName.toString() == "$executableFileName.sh"
    } ?: error("Failed to detect IDE executable .sh in:\n${allBinFiles.joinToString("\n")}")

    return object : InstalledIde {
      override val bundledPluginsDir = appHome.resolve("plugins")

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      override val vmOptions: VMOptions
        get() = vmOptionsFinal

      override val patchedVMOptionsFile = appHome.parent.resolve("${appHome.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) =
        object : InstalledBackedIDEStartConfig(patchedVMOptionsFile, vmOptions) {

          override val environmentVariables: Map<String, String>
            get() = super.environmentVariables.filterKeys {
              when {
                it.startsWith("DESKTOP") -> false
                it.startsWith("DBUS") -> false
                it.startsWith("APPIMAGE") -> false
                it.startsWith("DEFAULTS_PATH") -> false
                it.startsWith("GDM") -> false
                it.startsWith("GNOME") -> false
                it.startsWith("GTK") -> false
                it.startsWith("MANDATORY_PATH") -> false
                it.startsWith("QT") -> false
                it.startsWith("SESSION") -> false
                it.startsWith("TOOLBOX_VERSION") -> false
                it.startsWith("XAUTHORITY") -> false
                it.startsWith("XDG") -> false
                it.startsWith("XMODIFIERS") -> false
                it.startsWith("GPG_") -> false
                it.startsWith("CLUTTER_IM_MODULE") -> false
                it.startsWith("APPDIR") -> false
                it.startsWith("LC") -> false
                it.startsWith("SSH") -> false
                else -> true
              }
            } + ("LC_ALL" to "en_US.UTF-8")

          val xvfbRunLog = createXvfbRunLog(logsDir)

          override val errorDiagnosticFiles = listOf(xvfbRunLog)
          override val workDir = appHome
          override val commandLine: List<String> = linuxCommandLine(xvfbRunLog, vmOptions.environmentVariables) + executablePath.toAbsolutePath().toString()
        }

      override val build = build
      override val os = OS.Linux
      override val productCode = productCode
      override val isFromSources = false
      override val installationPath: Path = appHome

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome.resolve("jbr")
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