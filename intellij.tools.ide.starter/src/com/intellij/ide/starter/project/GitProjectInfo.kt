package com.intellij.ide.starter.project

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.utils.Git
import com.intellij.ide.starter.utils.logError
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

  val commitHash: String = "",

  /**
   * Branch name is a must to specify because there is no easy way to decipher it from git.
   * And if you're switching between branches in tests you may encounter problem,
   * that tests can execute on the branch, that you're not expected.
   */
  val branchName: String,

  override val isReusable: Boolean = true,

  /**
   * Relative path inside Image file, where project home is located
   */
  override val testProjectImageRelPath: (Path) -> Path = { it }
) : ProjectInfoSpec {

  private fun cloneRepo(projectHome: Path) {
    Git.clone(repoUrl = repositoryUrl, destinationDir = projectHome, branchName = branchName)
  }

  private fun setupRepositoryState(projectHome: Path) {
    Git.reset(repositoryDirectory = projectHome)
    Git.clean(projectHome)

    if (branchName.isNotEmpty()) Git.checkout(repositoryDirectory = projectHome, branchName = branchName)
    Git.pull(projectHome)
    if (commitHash.isNotEmpty()) Git.reset(repositoryDirectory = projectHome, commitHash = commitHash)
  }

  override fun downloadAndUnpackProject(): Path {
    val globalPaths by di.instance<GlobalPaths>()

    val projectsUnpacked = globalPaths.getCacheDirectoryFor("projects").resolve("unpacked").createDirectories()
    val projectHome = projectsUnpacked.resolve(repositoryUrl.split("/").last().split(".git").first())

    if (!projectHome.exists()) cloneRepo(projectHome)

    try {
      setupRepositoryState(projectHome)
    }
    catch (_: Exception) {
      logError("Failed to setup the test project git repository state as: $this")
      logError("Trying one more time from clean checkout")

      projectHome.apply {
        toFile().deleteRecursively()
        createDirectories()
      }

      cloneRepo(projectHome)
      setupRepositoryState(projectHome)
    }

    val imagePath = projectHome.let(testProjectImageRelPath)
    return imagePath
  }

  fun onCommit(commitHash: String): GitProjectInfo = copy(commitHash = commitHash)

  fun onBranch(branchName: String): GitProjectInfo = copy(branchName = branchName)
}