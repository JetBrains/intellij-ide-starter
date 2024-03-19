package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.process.collectJavaThreadDump
import com.intellij.ide.starter.process.getProcessesIdByProcessName
import com.intellij.ide.starter.utils.catchAll
import java.nio.file.Path

/**
 * Stuff related to particular build tool
 */
abstract class BuildTool(val type: BuildToolType, val testContext: IDETestContext) {
  fun collectDumpFile(processName: String, logsDir: Path, jdkHome: Path, workDir: Path) {
    catchAll {
      getProcessesIdByProcessName(processName).forEachIndexed { index, processId ->
        val dumpFile = logsDir.resolve("threadDump-before-kill-${System.currentTimeMillis()}-$processName-$index.txt")
        collectJavaThreadDump(jdkHome, workDir, processId, dumpFile)
      }
    }
  }
}