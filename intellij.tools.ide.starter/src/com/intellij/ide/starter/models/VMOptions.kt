package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.FileSystem.cleanPathFromSlashes
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import com.intellij.tools.ide.util.common.logOutput
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.*


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
    logOutput("Setting IDE system property: [$key=$value]")
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

    // FIXME this is a side effect that is not negated by addSystemProperty
    System.clearProperty(key) // to synchronize behaviour in IDEA and on test runner side

    removeLine(line = "-D$key=$value")
  }

  fun clearSystemProperty(key: String) {
    data = data.filterNot {
     it.startsWith("-D$key=")
        .also { match -> if (match) logOutput("Removing system property: ${it.removePrefix("-D")}") }
    }
  }

  fun addLine(line: String, filterPrefix: String? = null) {
    if (data.contains(line)) return
    data = (if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }) + line
  }

  private fun removeLine(line: String) {
    if (!data.contains(line)) return
    data = data - line
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

  fun setJavaHome(sdkObject: SdkObject) = apply {
    withEnv("JAVA_HOME", sdkObject.sdkPath.toString())
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

  fun writeJavaArgsFile(theFile: Path) = JvmUtils.writeJvmArgsFile(theFile, this.data)

  fun overrideDirectories(paths: IDEDataPaths) = run {
    addSystemProperty(PathManager.PROPERTY_CONFIG_PATH, paths.configDir)
    addSystemProperty(PathManager.PROPERTY_SYSTEM_PATH, paths.systemDir)
    addSystemProperty(PathManager.PROPERTY_PLUGINS_PATH, paths.pluginsDir)
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

  fun enableVerboseOpenTelemetry() = addSystemProperty("idea.diagnostic.opentelemetry.verbose", true)


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
    addSystemProperty("jb.privacy.policy.ai.assistant.text", "<!--999.999-->")
    addSystemProperty("writerside.eula.reviewed.and.accepted", true)
  }

  fun disableFreezeReportingProfiling() = run {
    addSystemProperty("freeze.reporter.profiling", false)
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
    }
  }

  fun setFlagIntegrationTests() = addSystemProperty("idea.is.integration.test", true)

  fun setFatalErrorNotificationEnabled() = addSystemProperty("idea.fatal.error.notification", true)

  fun setSnapshotPath(snapshotsDir: Path){
    addSystemProperty("snapshots.path", snapshotsDir)
  }

  fun withJvmCrashLogDirectory(jvmCrashLogDirectory: Path) =
    addLine("-XX:ErrorFile=${jvmCrashLogDirectory.toAbsolutePath()}${File.separator}java_error_in_idea_%p.log", "-XX:ErrorFile=")

  fun withHeapDumpOnOutOfMemoryDirectory(directory: Path) =
    addLine("-XX:HeapDumpPath=${directory.toAbsolutePath()}${File.separator}heap-dump.hprof", "-XX:HeapDumpPath=")

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

  /**
   * 1 file will be produced each minute (depends on the configuration in OpenTelemetry)
   * Thus by default it's better to set it to a high number, so long-running tests will not report invalid metrics
   */
  fun setOpenTelemetryMaxFilesNumber(maxFilesNumber: Int = 120) =
    addSystemProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", maxFilesNumber)

  fun disableAutoImport(disabled: Boolean = true) = addSystemProperty("external.system.auto.import.disabled", disabled)

  fun executeRightAfterIdeOpened(executeRightAfterIdeOpened: Boolean = true) = addSystemProperty("performance.execute.script.right.after.ide.opened", executeRightAfterIdeOpened)

  fun skipIndicesInitialization(value: Boolean = true) = addSystemProperty("idea.skip.indices.initialization", value)

  fun doRefreshAfterJpsLibraryDownloaded(value: Boolean = true) = addSystemProperty("idea.do.refresh.after.jps.library.downloaded", value)

  /**
   * Include [runtime module repository](psi_element://com.intellij.platform.runtime.repository) in the installed IDE.
   * Works only when IDE is built from sources.
   */
  fun setRuntimeModuleRepository(installationDirectory: Path) = addSystemProperty(
    "intellij.platform.runtime.repository.path", installationDirectory.resolve("modules/module-descriptors.jar").pathString
  )

  fun hasOption(option: String): Boolean {
    return data.any { it.contains(option) }
  }

  fun getOptionValue(option: String): String {
   data.forEach { line ->
      if (line.contains(option)) {
        return line.replace("-D$option=", "")
      }
    }
    error("There is no such option")
  }

  fun isUnderDebug(): Boolean = ManagementFactory.getRuntimeMXBean().inputArguments.any { it.startsWith("-agentlib:jdwp") }

  fun enforceSplash() = addLine("-Dsplash=true")

  @Suppress("SpellCheckingInspection")
  fun enforceNoSplash() = addLine("-Dnosplash=true")
}
