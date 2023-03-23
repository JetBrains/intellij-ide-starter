package com.intellij.ide.starter.project

import com.intellij.ide.starter.config.Const
import java.net.URI
import java.nio.file.Path

object GitHubProject {
  /**
   * [repoRelativeUrl] - Relative path to GitHub repo. Eg: "alexholmes/avro-maven"
   * [projectDirRelativePath] - Relative path from repo root. Eg: { it.resolve("path/to/deeply/hidden/project") }
   */
  fun fromGithub(
    branchName: String = "main",
    commitHash: String = "",
    repoRelativeUrl: String,
    projectDirRelativePath: (Path) -> Path = { it }
  ): GitProjectInfo {
    val repoRelativeUrlWithGitSuffix = if (repoRelativeUrl.endsWith(".git")) repoRelativeUrl
    else "$repoRelativeUrl.git"

    return GitProjectInfo(
      branchName = branchName,
      commitHash = commitHash,
      repositoryUrl = URI(Const.GITHUB_HTTP_BASE_URL).resolve(repoRelativeUrlWithGitSuffix).toString(),
      projectHomeRelativePath = projectDirRelativePath,
    )
  }
}