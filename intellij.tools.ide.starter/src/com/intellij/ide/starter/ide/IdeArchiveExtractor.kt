package com.intellij.ide.starter.ide

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logOutput
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.nameWithoutExtension
import kotlin.time.Duration.Companion.minutes

object IdeArchiveExtractor {

  fun unpackIdeIfNeeded(ideInstallerFile: File, unpackDir: File) {
    if (unpackDir.isDirectory && unpackDir.listFiles()?.isNotEmpty() == true) {
      logOutput("Build directory $unpackDir already exists for the binary $ideInstallerFile")
      return
    }

    logOutput("Extracting application into $unpackDir")
    when {
      ideInstallerFile.extension == "dmg" -> unpackDmg(ideInstallerFile, unpackDir.toPath())
      ideInstallerFile.extension == "exe" -> unpackWin(ideInstallerFile, unpackDir)
      ideInstallerFile.extension == "zip" -> FileSystem.unpack(ideInstallerFile.toPath(), unpackDir.toPath())
      ideInstallerFile.name.endsWith(".tar.gz") -> FileSystem.unpackTarGz(ideInstallerFile, unpackDir)
      else -> error("Unsupported build file: $ideInstallerFile")
    }
  }

  private fun unpackDmg(dmgFile: File, target: Path): Path {
    target.toFile().deleteRecursively()
    target.createDirectories()

    val mountDir = File(dmgFile.path + "-mount${System.currentTimeMillis()}")
    try {
      ProcessExecutor(presentableName = "hdiutil",
                      workDir = target,
                      timeout = 10.minutes,
                      stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
                      args = listOf("hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse", "-mountpoint", "$mountDir",
                                    "$dmgFile")
      ).start()
    }
    catch (t: Throwable) {
      dmgFile.delete()
      throw Error("Failed to mount $dmgFile. ${t.message}.", t)
    }

    try {
      val appDir = mountDir.listFiles()?.singleOrNull { it.name.endsWith(".app") }
                   ?: error("Failed to find the only one .app folder in $dmgFile")

      val targetAppDir = target / appDir.name
      ProcessExecutor(
        presentableName = "copy-dmg",
        workDir = target,
        timeout = 10.minutes,
        stderrRedirect = ExecOutputRedirect.ToStdOut("cp"),
        args = listOf("cp", "-R", "$appDir", "$targetAppDir")
      ).start()

      return targetAppDir
    }
    finally {
      catchAll {
        ProcessExecutor(
          presentableName = "hdiutil",
          workDir = target,
          timeout = 10.minutes,
          stdoutRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
          stderrRedirect = ExecOutputRedirect.ToStdOut("hdiutil"),
          args = listOf("hdiutil", "detach", "-force", "$mountDir")
        ).start()
      }
    }
  }

  private fun unpackWin(exeFile: File, targetDir: File) {
    targetDir.deleteRecursively()

    //we use 7-Zip to unpack NSIS binaries, same way as in Toolbox App
    val sevenZipToolExe = getSevenZipExe()

    targetDir.mkdirs()
    ProcessExecutor(
      presentableName = "unpack-zip",
      workDir = targetDir.toPath(),
      timeout = 10.minutes,
      args = listOf(sevenZipToolExe.toAbsolutePath().toString(), "x", "-y", "-o$targetDir", exeFile.path)
    ).start()
  }

  private fun getSevenZipExe(): Path {
    val sevenZipCacheDir = GlobalPaths.instance.getCacheDirectoryFor("7zip")

    // First, download an old 7-Zip distribution that is available as ZIP
    val sevenZipNineUrl = "https://www.7-zip.org/a/7za920.zip"
    val sevenZipNineFile = sevenZipCacheDir / sevenZipNineUrl.split("/").last()
    val sevenZipNineTool = sevenZipCacheDir / sevenZipNineFile.fileName.nameWithoutExtension

    HttpClient.downloadIfMissing(sevenZipNineUrl, sevenZipNineFile)
    FileSystem.unpackIfMissing(sevenZipNineFile, sevenZipNineTool)

    val sevenZipNineToolExe = sevenZipNineTool.resolve("7za.exe")

    // Then, download the new 7-Zip and unpack it using the old one
    val sevenZipUrl = "https://www.7-zip.org/a/7z1900-x64.exe"
    val sevenZipFile = sevenZipCacheDir / sevenZipUrl.split("/").last()
    val sevenZipTool = sevenZipCacheDir / sevenZipFile.fileName.nameWithoutExtension

    HttpClient.downloadIfMissing(sevenZipUrl, sevenZipFile)
    ProcessExecutor(
      presentableName = "unpack-7zip",
      workDir = sevenZipCacheDir,
      timeout = 1.minutes,
      args = listOf(sevenZipNineToolExe.toAbsolutePath().toString(), "x", "-y", "-o$sevenZipTool", sevenZipFile.absolutePathString())
    ).start()

    return sevenZipTool.resolve("7z.exe")
  }
}