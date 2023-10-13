package com.intellij.ide.starter.path

import com.intellij.ide.starter.utils.createInMemoryDirectory
import com.intellij.tools.ide.util.common.logOutput
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class IDEDataPaths(
  val testHome: Path,
  private val inMemoryRoot: Path?
) : Closeable {

  companion object {
    fun createPaths(testName: String, testHome: Path, useInMemoryFs: Boolean): IDEDataPaths {
      testHome.toFile().walkBottomUp().fold(true) { res, it ->
        (it.absolutePath.startsWith((testHome / "system").toFile().absolutePath) || it.delete() || !it.exists()) && res
      }
      testHome.createDirectories()
      val inMemoryRoot = if (useInMemoryFs) {
        createInMemoryDirectory("ide-integration-test-$testName")
      }
      else {
        null
      }
      return IDEDataPaths(testHome = testHome, inMemoryRoot = inMemoryRoot)
    }
  }

  val tempDir = (testHome / "temp").createDirectories()

  val configDir = ((inMemoryRoot ?: testHome) / "config").createDirectories()
  val systemDir = ((inMemoryRoot ?: testHome) / "system").createDirectories()
  val pluginsDir = (testHome / "plugins").createDirectories()
  val jbrDiagnostic = (testHome / "jbrDiagnostic").createDirectories()

  override fun close() {
    if (inMemoryRoot != null) {
      try {
        inMemoryRoot.toFile().deleteRecursively()
      }
      catch (e: Exception) {
        logOutput("! Failed to unmount in-memory FS at $inMemoryRoot")
        e.stackTraceToString().lines().forEach { logOutput("    $it") }
      }
    }
  }

  override fun toString(): String = "IDE Test Paths at $testHome"
}