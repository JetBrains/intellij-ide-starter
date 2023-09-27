package com.intellij.ide.starter.ide

import java.nio.file.Path


data class JBRVersion(val majorVersion: String, val buildNumber: String)

abstract class IdeDistribution {
  abstract fun installIde(unpackDir: Path, executableFileName: String): InstalledIde
}