package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.Git
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/**
 * Project, hosted as a Git repository
 */
data class GitProjectInfo(
  /**
   * SSH or HTTPS url.
   * Bear in mind, that it should be either available without authentication or you should have an ssh keys on the machine.
   */
  val repositoryUrl: String,
  override val isReusable: Boolean = true,

  /**
   * Relative path inside Image file, where project home is located
   */
  override val testProjectImageRelPath: (Path) -> Path = { it }
) : ProjectInfoSpec {

  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked").createDirectories()
    val projectHome = projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())

    if (projectHome.exists()) {
      try {
        Git.reset(projectHome)
        Git.clean(projectHome)
        Git.pull(projectHome)
      }
      catch (_: Exception) {
        projectHome.apply {
          toFile().deleteRecursively()
          createDirectories()
        }
      }
    }
    else {
      Git.clone(repoUrl = repositoryUrl, destinationDir = projectHome)
    }

    val imagePath = projectHome.let(testProjectImageRelPath)
    return imagePath
  }
}