package com.intellij.ide.starter.models

import com.intellij.driver.model.command.MarshallableCommand
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.EapReleaseConfigurable
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import org.kodein.di.instance

data class TestCase<T : ProjectInfoSpec>(
  val ideInfo: IdeInfo,
  val projectInfo: T,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val useInMemoryFileSystem: Boolean = false
) {
  private val eapReleaseConfigurable: EapReleaseConfigurable by di.instance<EapReleaseConfigurable>()

  fun withProject(projectInfo: T): TestCase<T> = copy(projectInfo = projectInfo)

  fun withCommands(commands: Iterable<MarshallableCommand> = this.commands): TestCase<T> = copy(commands = commands.toList())

  /**
   * You may consider using this method with [IdeProductProvider]
   */
  fun onIDE(ideInfo: IdeInfo): TestCase<T> = copy(ideInfo = ideInfo)

  /**
   * On each test run the project will be unpacked again.
   * This guarantees that there is not side effects from previous test runs
   **/
  fun markNotReusable(): TestCase<T> = when (projectInfo) {
    is RemoteArchiveProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = false) as T)
    is GitProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = false) as T)
    is LocalProjectInfo -> copy(projectInfo = projectInfo.copy(isReusable = false) as T)
    else -> {
      throw IllegalStateException("Can't mark not reusable for ${projectInfo.javaClass}")
    }
  }


  fun useRC(): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.useRC(ideInfo))

  fun useEAP(): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.useEAP(ideInfo))

  fun useEAP(buildNumber: String = ""): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.useEAP(ideInfo, buildNumber))

  fun useRelease(): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.useRelease(ideInfo))

  fun useRelease(version: String = ""): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.useRelease(ideInfo, version))

  /** E.g: "222.3244.1" */
  fun withBuildNumber(buildNumber: String): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.withBuildNumber(ideInfo, buildNumber))

  /** E.g: "2022.1.2" */
  fun withVersion(version: String): TestCase<T> = copy(ideInfo = eapReleaseConfigurable.withVersion(ideInfo, version))
}
