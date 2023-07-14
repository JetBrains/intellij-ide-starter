package examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
  val IntelliJCommunityProject = getTemplate().withProject(
    GitHubProject.fromGithub(
      repoRelativeUrl = "JetBrains/intellij-community",
      branchName = "master")
  )

  val JitPackAndroidExample = getTemplate().withProject(
    GitHubProject.fromGithub(
      repoRelativeUrl = "jitpack/android-example",
      branchName = "master"
    )
  )
}