package com.intellij.ide.starter.driver.engine.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.PolymorphRef
import com.intellij.driver.client.PolymorphRefRegistry
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ProjectManager


class PolymorphRegistryImpl(private val driver: Driver) : PolymorphRefRegistry {
  override fun convert(ref: PolymorphRef, target: RdTarget): PolymorphRef {
    val currentRdTarget = (ref as RefWrapper).getRef().rdTarget()
    if (currentRdTarget == target) return ref

    return when (ref) {
      is Project -> convertProject(ref, target)
      else -> throw IllegalStateException("Object $ref was marked as polymorph, but there no convert method for it")
    }
  }

  private fun convertProject(project: Project, target: RdTarget): Project {
    val targetService = driver.service(ProjectManager::class, target)
    return targetService.getOpenProjects().filter { it.getProjectFilePath() == project.getProjectFilePath() }.firstOrNull()
           ?: throw IllegalStateException("Can not find project ${project.getName()} for target $target")
  }
}
