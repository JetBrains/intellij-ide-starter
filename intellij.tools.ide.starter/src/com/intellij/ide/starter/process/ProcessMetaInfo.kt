package com.intellij.ide.starter.process

data class ProcessMetaInfo(
  val pid: Int,
  val command: String
) {
  override fun toString() = "$pid $command"
}
