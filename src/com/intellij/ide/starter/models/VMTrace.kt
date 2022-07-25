package com.intellij.ide.starter.models

import java.nio.file.Files
import java.nio.file.Path

/**
 * Holds path to libvmtrace.so on disk.
 */
object VMTrace {
  val vmTraceFile: Path = Files.createTempFile("libvmtrace", ".so")

  init {
    val vmTraceSoBytes = VMOptions::class.java.getResourceAsStream("/libvmtrace.so")!!
      .use { it.readAllBytes() }
    Files.write(vmTraceFile, vmTraceSoBytes)
  }
}