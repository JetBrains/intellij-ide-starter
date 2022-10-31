package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.ide.starter.utils.FileSystem.isDirUpToDate
import com.intellij.ide.starter.utils.HttpClient
import com.intellij.ide.starter.utils.logOutput
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Project stored on remote server as an archive
 */
data class RemoteArchiveProjectInfo(
  val testProjectURL: String,
  override val isReusable: Boolean = true,

  /**
   * Relative path inside Image file, where project home is located
   */
  override val testProjectImageRelPath: (Path) -> Path = { it / testProjectURL.split("/").last().split(".zip").first() }
) : ProjectInfoSpec {

  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked").createDirectories()
    val projectHome = projectsUnpacked.let(testProjectImageRelPath)

    if (!isReusable) {
      projectHome.toFile().deleteRecursively()
    }

    if (projectHome.isDirUpToDate()) {
      logOutput("Already unpacked project $projectHome will be used in the test")
      return projectHome
    }
    else {
      projectHome.toFile().deleteRecursively()
    }

    val zipFile = when (testProjectURL.contains("https://github.com")) {
      true -> globalPaths.getCacheDirectoryFor("projects").resolve("zip").resolve("${projectHome.toString().split("/").last()}.zip")
      false -> globalPaths.getCacheDirectoryFor("projects").resolve("zip").resolve(testProjectURL.toString().split("/").last())
    }

    HttpClient.downloadIfMissing(testProjectURL, zipFile)
    val imagePath: Path = zipFile

    when {
      imagePath.isRegularFile() -> FileSystem.unpack(imagePath, projectsUnpacked)
      imagePath.isDirectory() -> imagePath.toFile().copyRecursively(projectsUnpacked.toFile(), overwrite = true)

      else -> error("$imagePath does not exist!")
    }
    return projectHome
  }
}