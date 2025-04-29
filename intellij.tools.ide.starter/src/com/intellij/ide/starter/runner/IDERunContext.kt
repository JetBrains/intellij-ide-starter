package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.classFileVerification
import com.intellij.ide.starter.config.includeRuntimeModuleRepositoryInIde
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDERemDevTestContext
import com.intellij.ide.starter.ide.IDEStartConfig
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptions.Companion.ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.process.collectJavaThreadDump
import com.intellij.ide.starter.process.collectMemoryDump
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getJavaProcessIdWithRetry
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.AllureHelper
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.report.TimeoutAnalyzer
import com.intellij.ide.starter.runner.events.*
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.ide.starter.utils.*
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.tools.ide.performanceTesting.commands.MarshallableCommand
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.io.createDirectories
import io.qameta.allure.Allure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

data class IDERunContext(
  val testContext: IDETestContext,
  val commandLine: (IDERunContext) -> IDECommandLine = ::openTestCaseProject,
  val commands: Iterable<MarshallableCommand> = listOf(),
  val runTimeout: Duration = 10.minutes,
  val useStartupScript: Boolean = true,
  val verboseOutput: Boolean = false,
  val launchName: String = "",
  val expectedKill: Boolean = false,
  val expectedExitCode: Int = 0,
  val collectNativeThreads: Boolean = false,
  private val stdOut: ExecOutputRedirect? = null
) {
  val contextName: String
    get() = (if (launchName.isNotEmpty()) "${testContext.testName}/${launchName}" else testContext.testName)

  private val jvmCrashLogDirectory by lazy { logsDir.resolve("jvm-crash").createDirectories() }
  private val heapDumpOnOomDirectory by lazy { logsDir.resolve("heap-dump").createDirectories() }
  val reportsDir: Path = testContext.paths.testHome.resolve(launchName).resolve("reports").createDirectoriesIfNotExist()
  val snapshotsDir: Path = testContext.paths.testHome.resolve(launchName).resolve("snapshots").createDirectoriesIfNotExist()
  val launchDir: Path = testContext.paths.testHome.resolve(launchName).createDirectoriesIfNotExist()
  val logsDir: Path = testContext.paths.testHome.resolve(launchName).resolve("log").createDirectoriesIfNotExist()

  private val patchesForVMOptions: MutableList<VMOptions.() -> Unit> = mutableListOf()

  private fun Path.createDirectoriesIfNotExist(): Path {
    if (exists()) {
      logOutput("Reports dir '${this.fileName}' is already created")
      return this
    }
    logOutput("Creating reports dir '${this.fileName}'")
    return createDirectories()
  }

  internal fun deleteJVMCrashes() {
    listOf(heapDumpOnOomDirectory, jvmCrashLogDirectory)
      .filter { dir -> dir.exists() && dir.listDirectoryEntries().isNotEmpty() }
      .forEach { NioFiles.deleteRecursively(it) }
  }

  internal fun publishArtifacts() {
    testContext.publishArtifact(
      source = logsDir,
      artifactPath = contextName,
      artifactName = formatArtifactName("logs", testContext.testName)
    )
    testContext.publishArtifact(
      source = testContext.paths.eventLogDataDir,
      artifactPath = contextName,
      artifactName = formatArtifactName("event-log-data", testContext.testName)
    )
    testContext.publishArtifact(
      source = snapshotsDir,
      artifactPath = contextName,
      artifactName = formatArtifactName("snapshots", testContext.testName)
    )
    testContext.publishArtifact(
      source = reportsDir,
      artifactPath = contextName,
      artifactName = formatArtifactName("reports", testContext.testName)
    )
  }

  fun verbose(): IDERunContext = copy(verboseOutput = true)

  @Suppress("unused")
  fun withVMOptions(patchVMOptions: VMOptions.() -> Unit): IDERunContext = addVMOptionsPatch(patchVMOptions)

  /**
   * Method applies a patch to the current run, and the patch will be disregarded for the next run.
   */
  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDERunContext {
    patchesForVMOptions.add(patchVMOptions)
    return this
  }

  private fun installProfiler(): IDERunContext {
    @Suppress("DEPRECATION")
    return when (val profilerType = testContext.profilerType) {
      ProfilerType.ASYNC_ON_START, ProfilerType.YOURKIT, ProfilerType.ASYNC -> {
        val profiler = di.direct.instance<ProfilerInjector>(tag = profilerType)
        logOutput("Injecting profiler ${profiler.type.kind}")
        profiler.injectProfiler(this)
      }
      ProfilerType.NONE -> {
        this.addVMOptionsPatch { removeProfilerAgents() }
        logOutput("No profiler is specified.")
        return this
      }
    }
  }

  fun calculateVmOptions(): VMOptions {
    return testContext.ide.vmOptions.copy().apply {
      disableStartupDialogs()
      disableNewUsersOnboardingDialogue()
      disableFreezeReportingProfiling()
      setFatalErrorNotificationEnabled()
      setFlagIntegrationTests()
      takeScreenshotsPeriodically()
      withJvmCrashLogDirectory(jvmCrashLogDirectory)
      withHeapDumpOnOutOfMemoryDirectory(heapDumpOnOomDirectory)
      withGCLogs(reportsDir.resolve("gcLog.log"))
      setOpenTelemetryMaxFilesNumber()
      if (!hasOption(ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION)) {
        addSystemProperty(ALLOW_SKIPPING_FULL_SCANNING_ON_STARTUP_OPTION, false)
      }

      if (ConfigurationStorage.classFileVerification()) {
        withClassFileVerification()
      }

      if (ConfigurationStorage.includeRuntimeModuleRepositoryInIde()) {
        setRuntimeModuleRepository(testContext.ide.installationPath)
      }

      installProfiler()
      setSnapshotPath(snapshotsDir)
      setPathForMemorySnapshot()
      collectOpenTelemetry()
      setupLogDir()

      patchesForVMOptions.forEach { patchVMOptions -> patchVMOptions() }

      if (!useStartupScript) {
        require(commands.count() > 0) { "script builder is not allowed when useStartupScript is disabled" }
      }
      else
        installTestScript(testName = contextName, paths = testContext.paths, commands = commands)
    }
  }

  fun runIDE(): IDEStartResult {
    return runBlocking { runIdeSuspending() }
  }

  suspend fun runIdeSuspending(): IDEStartResult {
    return if (ConfigurationStorage.useDockerContainer())
      DockerIDEProcess().run(this)
    else LocalIDEProcess().run(this)
  }

  internal fun getStderr() = ExecOutputRedirect.ToStdOut("[ide-${contextName}-err]")

  internal fun getStdout(): ExecOutputRedirect {
    if (stdOut != null) {
      return stdOut
    }
    return if (verboseOutput) ExecOutputRedirect.ToStdOut("[ide-${contextName}-out]") else ExecOutputRedirect.ToString()
  }


  internal fun getErrorMessage(t: Throwable, ciFailureDetails: String?): String? {
    val failureCauseFile = logsDir.resolve("failure_cause.txt")
    val errorMessage = if (Files.exists(failureCauseFile)) {
      Files.readString(failureCauseFile)
    }
    else {
      t.message ?: t.javaClass.name
    }
    return when {
      ciFailureDetails == null -> errorMessage
      errorMessage == null -> ciFailureDetails
      else -> "$ciFailureDetails\n$errorMessage"
    }
  }

  internal fun logDisabledPlugins(paths: IDEDataPaths) {
    val disabledPlugins = paths.configDir.resolve("disabled_plugins.txt")
    if (disabledPlugins.exists()) {
      logOutput("The list of disabled plugins: " + disabledPlugins.readText())
    }
  }

  internal suspend fun captureDiagnosticOnKill(
    logsDir: Path,
    jdkHome: Path,
    startConfig: IDEStartConfig,
    pid: Long,
    process: Process,
    snapshotsDir: Path,
  ) {
    catchAll {
      takeScreenshot(logsDir)
    }
    if (expectedKill) return

    var javaProcessId: Long? = null
    suspend fun getOrComputeJavaProcessId(): Long {
      if (javaProcessId == null) {
        javaProcessId = getJavaProcessIdWithRetry(
          javaHome = jdkHome,
          workDir = startConfig.workDir,
          originalProcessId = pid,
          originalProcess = process,
        )
      }
      return javaProcessId
    }

    if (collectNativeThreads) {
      val fileToStoreNativeThreads = logsDir.resolve("native-thread-dumps.txt")
      startProfileNativeThreads(getOrComputeJavaProcessId().toString())
      delay(15.seconds)
      stopProfileNativeThreads(getOrComputeJavaProcessId().toString(), fileToStoreNativeThreads.toAbsolutePath().toString())
    }
    val dumpFile = logsDir.resolve("threadDump-before-kill-${System.currentTimeMillis()}.txt")
    val memoryDumpFile = snapshotsDir.resolve("memoryDump-before-kill-${System.currentTimeMillis()}.hprof.gz")
    catchAll {
      collectJavaThreadDump(jdkHome, startConfig.workDir, getOrComputeJavaProcessId(), dumpFile)
    }
    catchAll {
      if (isLowMemorySignalPresent(logsDir)) {
        collectMemoryDump(jdkHome, startConfig.workDir, getOrComputeJavaProcessId(), memoryDumpFile)
      }
    }
  }

  private fun isLowMemorySignalPresent(logsDir: Path): Boolean {
    return logsDir.resolve("idea.log").bufferedReader().useLines { lines ->
      lines.any { line ->
        line.contains("Low memory signal received: afterGc=true")
      }
    }
  }

  suspend fun startCollectThreadDumpsLoop(
    logsDir: Path,
    process: IDEHandle,
    jdkHome: Path,
    workDir: Path,
    collectingProcessId: Long,
    processName: String,
  ) {
    val monitoringThreadDumpDir = logsDir.resolve("monitoring-thread-dumps-${processName}").createDirectoriesIfNotExist()

    var cnt = 0
    while (process.isAlive) {
      delay(1.minutes)
      if (!process.isAlive) break

      val dumpFile = monitoringThreadDumpDir.resolve("threadDump-${++cnt}-${getCurrentTimestamp()}.txt")
      logOutput("Dumping threads to $dumpFile")
      catchAll { collectJavaThreadDump(jdkHome, workDir, collectingProcessId, dumpFile) }
    }
  }

  private fun getCurrentTimestamp(): String {
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
    return current.format(formatter)
  }

  internal suspend fun resolveAndDownloadSameJDK(): Path {
    try {
      return testContext.ide.resolveAndDownloadTheSameJDK()
    }
    catch (e: Exception) {
      logError("Failed to download the same JDK as in ${testContext.ide.build}")
      logError(e.stackTraceToString())

      val defaultJavaHome = JvmUtils.resolveInstalledJdk()
      logOutput("JDK is not found in ${testContext.ide.build}. Fallback to default java: $defaultJavaHome")
      return defaultJavaHome
    }
  }

  internal fun logStartupInfo(finalOptions: VMOptions) {
    logOutput(buildString {
      appendLine("Starting IDE for $contextName with timeout $runTimeout")
      appendLine("  VM Options: [" + finalOptions.toString().lineSequence().map { it.trim() }.joinToString(" ") + "]")
      appendLine("  On Java : [" + System.getProperty("java.home") + "]")
    })
  }

  internal fun deleteSavedAppStateOnMac() {
    if (SystemInfoRt.isMac) {
      val filesToBeDeleted = listOf(
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}-EAP.savedState",
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}.savedState"
      )
      val home = System.getProperty("user.home")
      val savedAppStateDir = Path.of(home).resolve("Library/Saved Application State")
      savedAppStateDir.toFile()
        .walkTopDown().maxDepth(1)
        .filter { file -> filesToBeDeleted.any { fileToBeDeleted -> file.name == fileToBeDeleted } }
        .forEach { it.deleteRecursively() }
    }
  }

  fun setPathForMemorySnapshot() {
    addVMOptionsPatch {
      addSystemProperty("memory.snapshots.path", logsDir)
    }
  }

  fun collectOpenTelemetry(): IDERunContext = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.file", logsDir.resolve(IDETestContext.OPENTELEMETRY_FILE))
  }

  fun setupLogDir(): IDERunContext = addVMOptionsPatch {
    addSystemProperty("idea.log.path", logsDir)
  }

  fun withScreenRecording() {
    if (testContext is IDERemDevTestContext && testContext != testContext.frontendIDEContext) {
      logOutput("Will not record screen for a backend of remote dev")
      return
    }
    val screenRecorder = IDEScreenRecorder(this)
    EventsBus.subscribe(IDERunContext::javaClass) { _: IdeLaunchEvent ->
      screenRecorder.start()
    }
    EventsBus.subscribe(IDERunContext::javaClass) { _: IdeAfterLaunchEvent ->
      screenRecorder.stop()
    }
  }
}
