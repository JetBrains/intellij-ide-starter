package com.intellij.ide.starter.examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {

  val GradleQuantumSimple = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "Perfecto-Quantum/Quantum-Starter-Kit.git",
      commitHash = "1dc6128c115cb41fc442c088174e81f63406fad5"
    )
  )
}