package com.intellij.ide.starter.frameworks.android

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.gradle.internal.hash.Hashing
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.time.Duration.Companion.minutes

class AndroidFramework(testContext: IDETestContext) : Framework(testContext) {
  companion object {
    fun downloadLatestAndroidSdk(javaHome: Path): Path {
      val packages = listOf(
        "build-tools;31.0.0",
        //"cmake;3.10.2.4988404",
        //"docs",
        //"ndk;20.0.5594570",
        "platforms;android-28",
        "sources;android-28",
        "platform-tools"
      )

      val sdkManager = downloadSdkManager()

      // we use unique home folder per installation to ensure only expected
      // packages are included into the SDK home path
      val packagesHash = Hashing.sha1().hashString(packages.joinToString("$"))
      val home = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("android-sdk") / "sdk-roots" / "sdk-root-$packagesHash"
      if (home.isDirectory() && home.toFile().walk().count() > 10) return home

      val envVariablesWithJavaHome = System.getenv() + ("JAVA_HOME" to javaHome.toAbsolutePath().toString())

      try {
        home.toFile().mkdirs()
        /// https://stackoverflow.com/questions/38096225/automatically-accept-all-sdk-licences
        /// sending "yes" to the process in the STDIN :(
        ProcessExecutor(presentableName = "android-sdk-licenses",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--licenses"),
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        stdInBytes = "yes\n".repeat(10).toByteArray(), // it asks the confirmation at least two times
                        timeout = 15.minutes
        ).start()

        //loading SDK
        ProcessExecutor(presentableName = "android-sdk-loading",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--list"),
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        timeout = 15.minutes
        ).start()

        //loading SDK
        ProcessExecutor(presentableName = "android-sdk-installing",
                        workDir = home,
                        environmentVariables = envVariablesWithJavaHome,
                        args = listOf(sdkManager.toString(), "--sdk_root=$home", "--install", "--verbose") + packages,
                        stderrRedirect = ExecOutputRedirect.ToStdOut("[sdkmanager-err]"),
                        timeout = 15.minutes
        ).start()
        return home
      }
      catch (t: Throwable) {
        home.toFile().deleteRecursively()
        throw Exception("Failed to prepare Android SDK to $home. ${t.message}", t)
      }
    }

    private fun downloadSdkManager(): Path {
      val url = when {
        SystemInfo.isMac -> "https://dl.google.com/android/repository/commandlinetools-mac-6200805_latest.zip"
        SystemInfo.isWindows -> "https://dl.google.com/android/repository/commandlinetools-win-6200805_latest.zip"
        SystemInfo.isLinux -> "https://dl.google.com/android/repository/commandlinetools-linux-6200805_latest.zip"
        else -> error("Unsupported OS: ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}")
      }

      val name = url.split("/").last()
      val androidSdkCache = di.direct.instance<GlobalPaths>().getCacheDirectoryFor("android-sdk")
      val targetArchive = androidSdkCache / "archives" / name
      val targetUnpack = androidSdkCache / "builds" / name
      HttpClient.downloadIfMissing(url, targetArchive)

      FileSystem.unpackIfMissing(targetArchive, targetUnpack)

      val ext = if (SystemInfo.isWindows) ".bat" else ""

      @Suppress("SpellCheckingInspection")
      val sdkManager = targetUnpack.toFile().walk().first { it.endsWith("tools/bin/sdkmanager$ext") }

      if (SystemInfo.isMac || SystemInfo.isLinux) {
        sdkManager.setExecutable(true)
      }

      return sdkManager.toPath()
    }
  }

  fun downloadAndroidPluginProjectForIJCommunity(intellijCommunityVersion: String): Unit {
    val projectHome = testContext.resolvedProjectHome
    if (projectHome.toFile().name.startsWith("intellij-community") && !(projectHome / "android").toFile().exists()) {
      val scriptName = "getPlugins.sh"

      val script = (projectHome / scriptName).toFile()
      assert(script.exists()) { "File $script does not exist" }
      val scriptContent = script.readText().replace("clone", "clone --depth 1")
      val commandLineArgs = scriptContent.split(" ")
      val adjustedCommandLineArgs = when (intellijCommunityVersion) {
        "master" -> commandLineArgs
        else -> {
          val listOfArgs = commandLineArgs.toMutableList()
          listOfArgs.add(2, "-b")
          listOfArgs.add(3, intellijCommunityVersion)
          listOfArgs.add(4, "--single-branch")
          listOfArgs
        }
      }

      val stdout = ExecOutputRedirect.ToString()
      val stderr = ExecOutputRedirect.ToString()

      ProcessExecutor(
        "git-clone-android-plugin",
        workDir = projectHome, timeout = 10.minutes,
        args = adjustedCommandLineArgs,
        stdoutRedirect = stdout,
        stderrRedirect = stderr
      ).start()

      logOutput(stdout.read().trim())
    }
  }
}