package com.intellij.ide.starter.project

import com.intellij.ide.starter.config.Const
import java.net.URI
import java.nio.file.Path

object GitHubProject {
  fun fromGithub(
    branchName: String = "main",
    commitHash: String = "",
    repoRelativePath: String,
    projectDirRelativePath: (Path) -> Path = { it }
  ): GitProjectInfo = GitProjectInfo(
    branchName = branchName,
    commitHash = commitHash,
    repositoryUrl = URI(Const.GITHUB_HTTP_BASE_URL).resolve(repoRelativePath).toString(),
    projectHomeRelativePath = projectDirRelativePath,
  )
}