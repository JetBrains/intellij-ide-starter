package com.intellij.ide.starter.models

import com.intellij.driver.model.command.MarshallableCommand
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IdeInfoConfigurable
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitProjectInfo
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import org.kodein.di.instance

data class TestCase(
  val ideInfo: IdeInfo,
  val projectInfo: ProjectInfoSpec? = null,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val useInMemoryFileSystem: Boolean = false
) {
  private val ideInfoConfigurable: IdeInfoConfigurable by di.instance<IdeInfoConfigurable>()

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
  fun markNotReusable(): TestCase = when (projectInfo) {
    is RemoteArchiveProjectInfo -> {
      copy(projectInfo = projectInfo.copy(isReusable = false))
    }
    is GitProjectInfo -> {
      copy(projectInfo = projectInfo.copy(isReusable = false))
    }
    is LocalProjectInfo -> {
      copy(projectInfo = projectInfo.copy(isReusable = false))
    }
    else -> {
      throw IllegalStateException("Can't mark not reusable for ${projectInfo?.javaClass}")
    }
  }


  fun useRC(): TestCase = copy(ideInfo = ideInfoConfigurable.useRC(ideInfo))

  fun useEAP(): TestCase = copy(ideInfo = ideInfoConfigurable.useEAP(ideInfo))

  /** E.g: "222.3244.1" */
  fun useEAP(buildNumber: String = ""): TestCase = copy(ideInfo = ideInfoConfigurable.useEAP(ideInfo, buildNumber))

  fun useRelease(): TestCase = copy(ideInfo = ideInfoConfigurable.useRelease(ideInfo))

  /** E.g: "2022.1.2" */
  fun useRelease(version: String = ""): TestCase = copy(ideInfo = ideInfoConfigurable.useRelease(ideInfo, version))

  /** If you are unsure about the need to use this method,
   *  it is recommended to first familiarize yourself with the functionality of
   *  [useEAP] and make sure it does not meet your requirements.
   *  E.g: "222.3244.1" */
  fun withBuildNumber(buildNumber: String): TestCase = copy(ideInfo = ideInfoConfigurable.withBuildNumber(ideInfo, buildNumber))

  /** If you are unsure about the need to use this method,
   *  it is recommended to first familiarize yourself with the functionality of
   *  [useRelease] and make sure it does not meet your requirements
   *  E.g: "2022.1.2" */
  fun withVersion(version: String): TestCase = copy(ideInfo = ideInfoConfigurable.withVersion(ideInfo, version))
}
