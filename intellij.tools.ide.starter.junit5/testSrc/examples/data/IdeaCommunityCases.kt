package examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import kotlin.io.path.div

object IdeaCommunityCases : TestCaseTemplate(IdeProductProvider.IC) {

  val GradleJitPackSimple = getTemplate().withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "/jitpack/gradle-simple"
    )
  )

  val MavenSimpleApp = getTemplate().withProject(
    RemoteArchiveProjectInfo(
      projectURL = "https://github.com/jenkins-docs/simple-java-maven-app/archive/refs/heads/master.zip",
      projectHomeRelativePath = { it / "simple-java-maven-app-master" }
    )
  )
}