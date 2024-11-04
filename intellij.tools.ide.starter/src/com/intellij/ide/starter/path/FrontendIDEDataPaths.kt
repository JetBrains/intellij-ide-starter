package com.intellij.ide.starter.path

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div

class FrontendIDEDataPaths(
  testHome: Path,
  inMemoryRoot: Path?,
  private val isFromSources: Boolean,
) : IDEDataPaths(testHome, inMemoryRoot, isFromSources) {

  override val eventLogMetadataDir: Path
    get() = System.getProperty("intellij.fus.custom.schema.dir")?.let { Path(it) }
            ?: (systemDir / (if (isFromSources) "tmp" else "frontend") / "per_process_config_0" / "event-log-metadata")

  override val eventLogDataDir: Path
    get() = systemDir / (if (isFromSources) "tmp" else "frontend") / "per_process_system_0" / "event-log-data"

}
