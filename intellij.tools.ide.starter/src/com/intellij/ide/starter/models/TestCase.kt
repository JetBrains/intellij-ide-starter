package com.intellij.ide.starter.models

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.EapReleaseConfigurable
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import org.kodein.di.instance

data class TestCase(
  val ideInfo: IdeInfo,
  val projectInfo: ProjectInfoSpec? = null,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val vmOptionsFix: VMOptions.() -> VMOptions = { this },
  val useInMemoryFileSystem: Boolean = false
) {
  private val eapReleaseConfigurable: EapReleaseConfigurable by di.instance<EapReleaseConfigurable>()

  fun withProject(projectInfo: ProjectInfoSpec): TestCase = copy(projectInfo = projectInfo)

  fun withCommands(commands: Iterable<MarshallableCommand> = this.commands): TestCase = copy(commands = commands.toList())

  /**
   * You may consider using this method with [IdeProductProvider]
   */
  fun onIDE(ideInfo: IdeInfo): TestCase = copy(ideInfo = ideInfo)

  /**
   * On each test run the project will be unpacked again.
   * This guarantees that there is not side effects from previous test runs
   **/
  fun markNotReusable(): TestCase {
    when (projectInfo) {
      is RemoteArchiveProjectInfo -> {
        return copy(projectInfo = projectInfo.copy(isReusable = false))
      }
      is GitProjectInfo -> {
        return copy(projectInfo = projectInfo.copy(isReusable = false))
      }
      is LocalProjectInfo -> {
        return copy(projectInfo = projectInfo.copy(isReusable = false))
      }
      else -> {
        throw IllegalStateException("Can't mark not reusable for ${projectInfo?.javaClass}")
      }
    }
  }

  fun useEAP(): TestCase = copy(ideInfo = eapReleaseConfigurable.useEAP(ideInfo))

  fun useEAP(buildNumber: String = ""): TestCase = copy(ideInfo = eapReleaseConfigurable.useEAP(ideInfo, buildNumber))

  fun useRelease(): TestCase = copy(ideInfo = eapReleaseConfigurable.useRelease(ideInfo))

  fun useRelease(version: String = ""): TestCase = copy(ideInfo = eapReleaseConfigurable.useRelease(ideInfo, version))

  /** E.g: "222.3244.1" */
  fun withBuildNumber(buildNumber: String): TestCase = copy(ideInfo = eapReleaseConfigurable.withBuildNumber(ideInfo, buildNumber))

  /** E.g: "2022.1.2" */
  fun withVersion(version: String): TestCase = copy(ideInfo = eapReleaseConfigurable.withVersion(ideInfo, version))
}
