package com.intellij.ide.starter.models

import com.intellij.ide.starter.ide.InstalledIde
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.utils.FileSystem.cleanPathFromSlashes
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.writeJvmArgsFile
import org.jetbrains.annotations.CheckReturnValue
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

/**
 * allows to combine VMOptions mapping functions easily by calling this function as
 * ```
 *    {}.andThen {} function
 * ```
 */
fun (VMOptions.() -> VMOptions).andThen(right: VMOptions.() -> VMOptions): VMOptions.() -> VMOptions = {
  val left = this@andThen
  this.left().right()
}


/**
 * All methods of this class return a copy with modifications applied, so return value _must_ be used otherwise the
 * whole call is useless.
 */
@CheckReturnValue
data class VMOptions(
  private val ide: InstalledIde,
  private val data: List<String>,
  val env: Map<String, String>
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

  fun addSystemProperty(key: String, value: Boolean): VMOptions = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Int): VMOptions = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Long): VMOptions = addSystemProperty(key, value.toString())

  fun addSystemProperty(key: String, value: Path): VMOptions = addSystemProperty(key, value.toAbsolutePath().toString())

  /*
    This method adds a property to IDE's VMOptions
    If such property already exists in VMOptions
    it will be replaced by the new one
   */
  fun addSystemProperty(key: String, value: String): VMOptions {
    logOutput("Setting system property: [$key=$value]")
    System.setProperty(key, value) // to synchronize behaviour in IDEA and on test runner side
    return addLine(line = "-D$key=$value", filterPrefix = "-D$key=")
  }

  /*
    This method updates a property in IDE's VMOptions
    with a new value (old value + new value separated by a comma)
    If such property does not exist in VMOptions
    the property with a given value will be added to VMOptions
 */
  private fun addSystemPropertyValue(key: String, value: String): VMOptions {
    return if (data.filter { it.contains("-D$key") }.size == 1) {
      val oldLine = data.filter { it.startsWith("-D$key") }[0]
      val oldValue = oldLine.split("=")[1]
      val updatedValue = "$oldValue,$value"
      logOutput("Updating system property: [$key=$updatedValue]")
      this.addSystemProperty(key, updatedValue)
    }
    else {
      this.addSystemProperty(key, value)
    }
  }

  fun removeSystemProperty(key: String, value: String): VMOptions {
    logOutput("Removing system property: [$key=$value]")
    System.clearProperty(key) // to synchronize behaviour in IDEA and on test runner side
    return removeLine(line = "-D$key=$value", filterPrefix = "-D$key=")
  }

  fun addLine(line: String, filterPrefix: String? = null): VMOptions {
    if (data.contains(line)) return this
    val copy = if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }
    return copy(data = copy + line)
  }

  fun removeLine(line: String, filterPrefix: String? = null): VMOptions {
    if (!data.contains(line)) return this
    val copy = if (filterPrefix == null) data else data.filterNot { it.trim().startsWith(filterPrefix) }
    return copy(data = copy - line)
  }

  private fun filterKeys(toRemove: (String) -> Boolean) = copy(data = data.filterNot(toRemove))

  fun withEnv(key: String, value: String) = copy(env = env + (key to value))

  fun writeIntelliJVmOptionFile(path: Path) {
    path.writeLines(data)
    logOutput("Write vmoptions patch to $path")
  }

  fun diffIntelliJVmOptionFile(theFile: Path): VMOptionsDiff {
    val loadedOptions = readIdeVMOptions(this.ide, theFile).data
    return VMOptionsDiff(originalLines = this.data, actualLines = loadedOptions)
  }

  fun writeJavaArgsFile(theFile: Path) {
    writeJvmArgsFile(theFile, this.data)
  }

  fun overrideDirectories(paths: IDEDataPaths) = this
    .addSystemProperty("idea.config.path", paths.configDir)
    .addSystemProperty("idea.system.path", paths.systemDir)
    .addSystemProperty("idea.plugins.path", paths.pluginsDir)
    .addSystemProperty("idea.log.path", paths.logsDir)

  fun enableStartupPerformanceLog(perf: IDEStartupReports): VMOptions {
    return this
      .addSystemProperty("idea.log.perf.stats.file", perf.statsJSON)
  }

  fun enableClassLoadingReport(filePath: Path): VMOptions {
    return this
      .addSystemProperty("idea.log.class.list.file", filePath)
      .addSystemProperty("idea.record.classpath.info", "true")
  }

  fun enableVmtraceClassLoadingReport(filePath: Path): VMOptions {
    if (!VMTrace.isSupported) return this

    val vmTraceFile = VMTrace.vmTraceFile

    return this
      .addSystemProperty("idea.log.vmtrace.file", filePath)
      .addLine("-agentpath:${vmTraceFile.toAbsolutePath()}=${filePath.toAbsolutePath()}")
  }

  fun enableExitMetrics(filePath: Path): VMOptions {
    return this.addSystemProperty("idea.log.exit.metrics.file", filePath)
  }

  fun configureLoggers(
    debugLoggers: List<String> = emptyList(),
    traceLoggers: List<String> = emptyList()
  ): VMOptions {
    val withDebug = if (debugLoggers.isNotEmpty()) {
      this.addSystemPropertyValue("idea.log.debug.categories", debugLoggers.joinToString(separator = ",") { "#" + it.removePrefix("#") })
    }
    else {
      this
    }

    return if (traceLoggers.isNotEmpty()) {
      withDebug.addSystemPropertyValue("idea.log.trace.categories", traceLoggers.joinToString(separator = ",") { "#" + it.removePrefix("#") })
    }
    else {
      withDebug
    }
  }

  fun debug(port: Int = 5005, suspend: Boolean = true): VMOptions {
    val suspendKey = if (suspend) "y" else "n"
    val configLine = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendKey},address=*:${port}"
    return addLine(configLine, filterPrefix = "-agentlib:jdwp")
  }

  fun inHeadlessMode() = this
    .addSystemProperty("java.awt.headless", true)

  fun disableStartupDialogs() = this
    .addSystemProperty("jb.consents.confirmation.enabled", false)
    .addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
    .addSystemProperty("writerside.eula.reviewed.and.accepted", true)

  fun takeScreenshotsPeriodically(logsDir: Path) = this
    .addSystemProperty("ide.performance.screenshot", logsDir.resolve("screenshot.png").toString())

  fun takeScreenshotOnFailure(logsDir: Path) = this
    .addSystemProperty("ide.performance.screenshot.on.failure", logsDir.resolve("screenshot_onFailure.png").toString())

  fun installTestScript(testName: String,
                        paths: IDEDataPaths,
                        commands: Iterable<MarshallableCommand>): VMOptions {
    val scriptText = commands.joinToString(separator = System.lineSeparator()) { it.storeToString() }

    val scriptFileName = testName.cleanPathFromSlashes(replaceWith = "_") + ".text"
    val scriptFile = paths.systemDir.resolve(scriptFileName).apply {
      parent.createDirectories()
    }
    scriptFile.writeText(scriptText)

    return this.addSystemProperty("testscript.filename", scriptFile)
      // Use non-success status code 1 when running IDE as command line tool.
      .addSystemProperty("testscript.must.exist.process.with.non.success.code.on.ide.error", "true")
      // No need to report TeamCity test failure from within test script.
      .addSystemProperty("testscript.must.report.teamcity.test.failure.on.error", "false")
  }

  fun usingStartupFramework() = this
    .addSystemProperty("startup.performance.framework", true)

  fun setFlagIntegrationTests() = this
    .addSystemProperty("idea.is.integration.test", true)

  fun setFatalErrorNotificationEnabled() = this
    .addSystemProperty("idea.fatal.error.notification", true)

  fun withJvmCrashLogDirectory(jvmCrashLogDirectory: Path) = this
    .addLine("-XX:ErrorFile=${jvmCrashLogDirectory.toAbsolutePath()}${File.separator}java_error_in_idea_%p.log", "-XX:ErrorFile=")

  fun withHeapDumpOnOutOfMemoryDirectory(directory: Path) = this
    .addLine("-XX:HeapDumpPath=${directory.toAbsolutePath()}", "-XX:HeapDumpPath=")

  fun withXmx(sizeMb: Int) = this
    .addLine("-Xmx" + sizeMb + "m", "-Xmx")

  fun withActiveProcessorCount(count: Int) = this
    .addLine("-XX:ActiveProcessorCount=$count", "-XX:ActiveProcessorCount")

  fun withClassFileVerification() = this
    .addLine("-XX:+UnlockDiagnosticVMOptions")
    .addLine("-XX:+BytecodeVerificationLocal")

  fun withG1GC() = this
    .filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    .filterKeys { it == "-XX:+UseG1GC" }
    .addLine("-XX:+UseG1GC")

  fun withGCLogs(gcLogFile: Path) = this
    .addLine("-Xlog:gc*:file=${gcLogFile.toAbsolutePath()}")

  /** see [JEP 318](https://openjdk.org/jeps/318) **/
  fun withEpsilonGC() = this
    .filterKeys { it == "-XX:+UseConcMarkSweepGC" }
    .filterKeys { it == "-XX:+UseG1GC" }
    .addLine("-XX:+UnlockExperimentalVMOptions")
    .addLine("-XX:+UseEpsilonGC")
    .addLine("-Xmx16g", "-Xmx")

  // a dummy wrapper to simplify expressions
  fun id() = this
}
