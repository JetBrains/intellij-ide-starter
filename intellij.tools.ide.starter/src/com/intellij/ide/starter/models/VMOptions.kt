package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.FileSystem.cleanPathFromSlashes
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.writeJvmArgsFile
import com.intellij.openapi.diagnostic.LogLevel
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText


data class VMOptions(
  private val ide: InstalledIde,
  private var data: List<String>,
  private var env: Map<String, String>
) {
  companion object {
    fun readIdeVMOptions(ide: InstalledIde, file: Path): VMOptions {
      return VMOptions(
        ide = ide,
        data = file
          .readLines()
          .map { it.trim() }
          .filter { it.isNotBlank() },
        env = emptyMap()
      )
    }
  }

  override fun toString() = buildString {
    appendLine("VMOptions{")
    appendLine("  env=$env")
    for (line in data) {
      appendLine("  $line")
    }
    appendLine("} // VMOptions")
  }

  val environmentVariables: Map<String, String>
    get() = env

  fun addSystemProperty(key: String, value: Boolean) = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Int) = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Long) = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Path) = addSystemProperty(key, value.toAbsolutePath().toString())

  /*
    This method adds a property to IDE's VMOptions
    If such property already exists in VMOptions
    it will be replaced by the new one
   */
  fun addSystemProperty(key: String, value: String) {
    logOutput("Setting system property: [$key=$value]")
    System.setProperty(key, value) // to synchronize behaviour in IDEA and on test runner side
    addLine(line = "-D$key=$value", filterPrefix = "-D$key=")
  }

  /*
    This method updates a property in IDE's VMOptions
    with a new value (old value + new value separated by a comma)
    If such property does not exist in VMOptions
    the property with a given value will be added to VMOptions
 */
  private fun addSystemPropertyValue(key: String, value: String) {
    if (data.filter { it.contains("-D$key") }.size == 1) {
      val oldLine = data.filter { it.startsWith("-D$key") }[0]
      val oldValue = oldLine.split("=")[1]
      val updatedValue = "$oldValue,$value"
      logOutput("Updating system property: [$key=$updatedValue]")
      addSystemProperty(key, updatedValue)
    }
    else {
      addSystemProperty(key, value)
    }
  }

  fun removeSystemProperty(key: String, value: Boolean) {
    logOutput("Removing system property: [$key=$value]")
    System.clearProperty(key) // to synchronize behaviour in IDEA and on test runner side
    removeLine(line = "-D$key=$value")
  }

  fun addLine(line: String, filterPrefix: String? = null) {
    if (data.contains(line)) return
    data = (if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }) + line
  }

  fun removeLine(line: String) {
    if (!data.contains(line)) return
    data =  data - line
  }

  fun removeProfilerAgents() {
    removeAsyncAgent()
    removeYourkitAgent()
  }

  fun removeAsyncAgent() {
    data = data.filterNot { it.contains("-agentpath:") && it.contains("async/libasyncProfiler") }
  }

  fun removeYourkitAgent() {
    data = data.filterNot { it.contains("-agentpath:") && it.contains("yourkit/bin/libyjpagent") }
  }

  private fun filterKeys(toRemove: (String) -> Boolean) {
    data = data.filterNot(toRemove)
  }

  fun withEnv(key: String, value: String) {
    env = env + (key to value)
  }


  fun writeIntelliJVmOptionFile(path: Path) {
    path.writeLines(data)
    logOutput("Write vmoptions patch to $path")
  }

  fun diffIntelliJVmOptionFile(theFile: Path): VMOptionsDiff {
    val loadedOptions = readIdeVMOptions(this.ide, theFile).data
    return VMOptionsDiff(originalLines = this.data, actualLines = loadedOptions)
  }

  fun writeJavaArgsFile(theFile: Path) = writeJvmArgsFile(theFile, this.data)

  fun overrideDirectories(paths: IDEDataPaths) = run {
    addSystemProperty("idea.config.path", paths.configDir)
    addSystemProperty("idea.system.path", paths.systemDir)
    addSystemProperty("idea.plugins.path", paths.pluginsDir)
    addSystemProperty("idea.log.path", paths.logsDir)
  }


  fun enableStartupPerformanceLog(perf: IDEStartupReports) = addSystemProperty("idea.log.perf.stats.file", perf.statsJSON)

  fun enableClassLoadingReport(filePath: Path) = run {
    addSystemProperty("idea.log.class.list.file", filePath)
    addSystemProperty("idea.record.classpath.info", "true")
  }


  fun enableVmtraceClassLoadingReport(filePath: Path) {
    if (!VMTrace.isSupported) return

    val vmTraceFile = VMTrace.vmTraceFile

    run {
      addSystemProperty("idea.log.vmtrace.file", filePath)
      addLine("-agentpath:${vmTraceFile.toAbsolutePath()}=${filePath.toAbsolutePath()}")
    }
  }

  fun enableExitMetrics(filePath: Path) = addSystemProperty("idea.log.exit.metrics.file", filePath)

  /**
   * [categories] - Could be packages, classes ...
   */
  fun configureLoggers(logLevel: LogLevel, vararg categories: String) {
    val logLevelName = logLevel.name.lowercase()

    if (categories.isNotEmpty()) {
      addSystemPropertyValue("idea.log.${logLevelName}.categories", categories.joinToString(separator = ",") {
        "#" + it.removePrefix("#")
      })
    }
  }

  fun debug(port: Int = 5005, suspend: Boolean = true) {
    val suspendKey = if (suspend) "y" else "n"
    val configLine = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendKey},address=*:${port}"
    addLine(configLine, filterPrefix = "-agentlib:jdwp")
  }

  fun inHeadlessMode() = addSystemProperty("java.awt.headless", true)

  fun disableStartupDialogs() = run {
    addSystemProperty("jb.consents.confirmation.enabled", false)
    addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
    addSystemProperty("writerside.eula.reviewed.and.accepted", true)
  }


  fun takeScreenshotsPeriodically() =
    addSystemProperty("ide.performance.screenshot", "heartbeat")

  fun installTestScript(testName: String,
                        paths: IDEDataPaths,
                        commands: Iterable<MarshallableCommand>) {
    val scriptText = commands.joinToString(separator = System.lineSeparator()) { it.storeToString() }

    val scriptFileName = testName.cleanPathFromSlashes(replaceWith = "_") + ".text"
    val scriptFile = paths.systemDir.resolve(scriptFileName).apply {
      parent.createDirectories()
    }
    scriptFile.writeText(scriptText)

    run {
      addSystemProperty("testscript.filename", scriptFile)
      // Use non-success status code 1 when running IDE as command line tool.
      addSystemProperty("testscript.must.exist.process.with.non.success.code.on.ide.error", "true")
      // No need to report TeamCity test failure from within test script.
      addSystemProperty("testscript.must.report.teamcity.test.failure.on.error", "false")
    }
  }

  fun usingStartupFramework() = addSystemProperty("startup.performance.framework", true)

  fun setFlagIntegrationTests() = addSystemProperty("idea.is.integration.test", true)

  fun setFatalErrorNotificationEnabled() = addSystemProperty("idea.fatal.error.notification", true)

  fun withJvmCrashLogDirectory(jvmCrashLogDirectory: Path) =
    addLine("-XX:ErrorFile=${jvmCrashLogDirectory.toAbsolutePath()}${File.separator}java_error_in_idea_%p.log", "-XX:ErrorFile=")

  fun withHeapDumpOnOutOfMemoryDirectory(directory: Path) = addLine("-XX:HeapDumpPath=${directory.toAbsolutePath()}", "-XX:HeapDumpPath=")

  fun withXmx(sizeMb: Int) = addLine("-Xmx" + sizeMb + "m", "-Xmx")

  fun withActiveProcessorCount(count: Int) = addLine("-XX:ActiveProcessorCount=$count", "-XX:ActiveProcessorCount")

  fun withClassFileVerification() = run {
    addLine("-XX:+UnlockDiagnosticVMOptions")
    addLine("-XX:+BytecodeVerificationLocal")
  }

  fun withG1GC() = run {
    filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    filterKeys { it == "-XX:+UseG1GC" }
    addLine("-XX:+UseG1GC")
  }

  fun withGCLogs(gcLogFile: Path) = addLine("-Xlog:gc*:file=${gcLogFile.toAbsolutePath()}")

  /** see [JEP 318](https://openjdk.org/jeps/318) **/
  fun withEpsilonGC() = run {
    filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    filterKeys { it == "-XX:+UseG1GC" }
    addLine("-XX:+UnlockExperimentalVMOptions")
    addLine("-XX:+UseEpsilonGC")
    addLine("-Xmx16g", "-Xmx")
  }
}
