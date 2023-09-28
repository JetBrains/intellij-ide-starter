package com.intellij.ide.starter.ide

import java.nio.file.Path

abstract class IdeDistribution {
  abstract fun installIde(unpackDir: Path, executableFileName: String): InstalledIde
}