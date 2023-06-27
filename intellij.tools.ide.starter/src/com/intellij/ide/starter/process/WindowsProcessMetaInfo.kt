package com.intellij.ide.starter.process

data class WindowsProcessMetaInfo(
  override val pid: Int,
  override val command: String,
  val description: String
) : ProcessMetaInfo(pid, command) {
  override fun toString() = "$pid $command $description"
}