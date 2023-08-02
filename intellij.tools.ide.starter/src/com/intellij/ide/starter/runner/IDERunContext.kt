package com.intellij.ide.starter.runner

import com.intellij.driver.model.command.MarshallableCommand
import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.StarterConfigurationStorage
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.*
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.VMOptionsDiff
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.process.collectJavaThreadDump
import com.intellij.ide.starter.process.collectMemoryDump
import com.intellij.ide.starter.process.destroyGradleDaemonProcessIfExists
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ExecTimeoutException
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.process.getJavaProcessIdWithRetry
import com.intellij.ide.starter.profiler.ProfilerInjector
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.report.ErrorReporter.ERRORS_DIR_NAME
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.*
import kotlinx.coroutines.delay
import org.kodein.di.direct
import org.kodein.di.instance
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.listDirectoryEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

interface IDERunCloseContext {
  val wasRunSuccessful: Boolean
}

data class IDERunContext(
  val testContext: IDETestContext,
  val commandLine: IDECommandLine = IDECommandLine.OpenTestCaseProject(testContext),
  val commands: Iterable<MarshallableCommand> = listOf(),
  val codeBuilder: (CodeInjector.() -> Unit)? = null,
  val runTimeout: Duration = 10.minutes,
  val useStartupScript: Boolean = true,
  val closeHandlers: List<IDERunCloseContext.() -> Unit> = listOf(),
  val verboseOutput: Boolean = false,
  val launchName: String = "",
  val expectedKill: Boolean = false,
  val collectNativeThreads: Boolean = false
) {
  val contextName: String
    get() = if (launchName.isNotEmpty()) {
      "${testContext.testName}/$launchName"
    }
    else {
      testContext.testName
    }

  private val jvmCrashLogDirectory by lazy { testContext.paths.logsDir.resolve("jvm-crash").createDirectories() }
  private val heapDumpOnOomDirectory by lazy { testContext.paths.logsDir.resolve("heap-dump").createDirectories() }
  private val patchesForVMOptions: MutableList<VMOptions.() -> Unit> = mutableListOf()

  fun verbose() = copy(verboseOutput = true)

  @Suppress("unused")
  fun withVMOptions(patchVMOptions: VMOptions.() -> Unit) = addVMOptionsPatch(patchVMOptions)

  /**
   * Method applies patch to the current run and the patch will be disregarded for the next run.
   */
  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDERunContext {
    patchesForVMOptions.add(patchVMOptions)
    return this
  }


  fun addCompletionHandler(handler: IDERunCloseContext.() -> Unit) = this.copy(closeHandlers = closeHandlers + handler)

  fun uploadProfilerResultsToCIServer(profilerSnapshotsDir: Path, artifactName: String) =
    this.addCompletionHandler {
      testContext.publishArtifact(source = profilerSnapshotsDir, artifactName = artifactName)
    }

  private fun installProfiler(): IDERunContext {
    return when (val profilerType = testContext.profilerType) {
      ProfilerType.ASYNC, ProfilerType.YOURKIT -> {
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

  private fun calculateVmOptions(): VMOptions {
    return testContext.ide.vmOptions.copy().apply {
      disableStartupDialogs()
      usingStartupFramework()
      setFatalErrorNotificationEnabled()
      setFlagIntegrationTests()
      takeScreenshotsPeriodically()
      withJvmCrashLogDirectory(jvmCrashLogDirectory)
      withHeapDumpOnOutOfMemoryDirectory(heapDumpOnOomDirectory)

      if (ConfigurationStorage.instance().getBoolean(StarterConfigurationStorage.ENV_ENABLE_CLASS_FILE_VERIFICATION))
        withClassFileVerification()
      installProfiler()

      patchesForVMOptions.forEach { patchVMOptions ->
        patchVMOptions()
      }

      if (!useStartupScript) {
        require(commands.count() > 0) { "script builder is not allowed when useStartupScript is disabled" }
      }
      else
        installTestScript(testName = contextName, paths = testContext.paths, commands = commands)
    }
  }

  // TODO: refactor this https://youtrack.jetbrains.com/issue/AT-18/Simplify-refactor-code-for-starting-IDE-in-IdeRunContext
  @OptIn(kotlin.time.ExperimentalTime::class)
  fun runIDE(): IDEStartResult {
    StarterBus.post(IdeLaunchEvent(EventState.BEFORE, IdeLaunchEventData(runContext = this, ideProcess = null)))

    deleteSavedAppStateOnMac()
    val paths = testContext.paths
    val logsDir = paths.logsDir.createDirectories()
    val snapshotsDir = paths.snapshotsDir.createDirectories()

    val stdout = getStdout()
    val stderr = getStderr()
    var ideProcessId = 0L
    var isRunSuccessful = true

    val ideHost = IDEHost(codeBuilder, testContext).also { it.setup() }
    try {
      testContext.setProviderMemoryOnlyOnLinux()
      val jdkHome: Path = resolveAndDownloadSameJDK()

      val vmOptions: VMOptions = calculateVmOptions()
      val startConfig = testContext.ide.startConfig(vmOptions, logsDir)
      if (startConfig is Closeable) {
        addCompletionHandler { startConfig.close() }
      }

      val mergedEnvVariables = (startConfig.environmentVariables + vmOptions.environmentVariables).toMutableMap().apply {
        putIfAbsent("JAVA_HOME", jdkHome.absolutePathString())
      }

      logDisabledPlugins(paths)
      logStartupInfo(vmOptions)

      val finalArgs = startConfig.commandLine + commandLine.args
      File(finalArgs.first()).setExecutable(true)
      val executionTime = measureTime {
        ProcessExecutor(
          presentableName = "run-ide-$contextName",
          workDir = startConfig.workDir,
          environmentVariables = mergedEnvVariables,
          timeout = runTimeout,
          args = finalArgs,
          errorDiagnosticFiles = startConfig.errorDiagnosticFiles,
          stdoutRedirect = stdout,
          stderrRedirect = stderr,
          onProcessCreated = { process, pid ->
            StarterBus.post(IdeLaunchEvent(EventState.IN_TIME, IdeLaunchEventData(runContext = this, ideProcess = process)))
            ideProcessId = getJavaProcessIdWithRetry(jdkHome, startConfig.workDir, pid, process)
            startCollectThreadDumpsLoop(logsDir, process, jdkHome, startConfig, ideProcessId)
          },
          onBeforeKilled = { process, pid ->
            captureDiagnosticOnKill(logsDir, jdkHome, startConfig, pid, process, snapshotsDir)
          }
        ).start()
      }
      logOutput("IDE run $contextName completed in $executionTime")
      validateVMOptionsWereSet(paths)
      logVmOptionDiff(startConfig.vmOptionsDiff())

      return IDEStartResult(runContext = this, executionTime = executionTime, vmOptionsDiff = startConfig.vmOptionsDiff())
    }
    catch (timeoutException: ExecTimeoutException) {
      if (expectedKill) {
        logOutput("IDE run for $contextName has been expected to be killed after $runTimeout")
        return IDEStartResult(runContext = this, executionTime = runTimeout)
      }
      else {
        isRunSuccessful = false
        error("Timeout of IDE run $contextName for $runTimeout")
      }
    }
    catch (exception: Throwable) {
      isRunSuccessful = false
      throw Exception(getErrorMessage(exception), exception)
    }
    finally {
      testContext.collectJBRDiagnosticFiles(ideProcessId)
      try {
        if (SystemInfo.isWindows) {
          destroyGradleDaemonProcessIfExists()
        }
        deleteJVMCrashes()
        ErrorReporter.reportErrorsAsFailedTests(logsDir / ERRORS_DIR_NAME, this, isRunSuccessful)
        publishArtifacts(isRunSuccessful)
        ideHost.tearDown()
        runCloseHandlers(isRunSuccessful)
      }
      finally {
        StarterBus.post(IdeLaunchEvent(EventState.AFTER, IdeLaunchEventData(runContext = this, ideProcess = null)))
        // https://youtrack.jetbrains.com/issue/AT-190/Full-DI-reset-after-IDE-run
        di.direct.instance<IdeInfoConfigurable>().resetDIToDefaultDownloading()
      }
    }
  }

  private fun getStderr() = ExecOutputRedirect.ToStdOut("[ide-${contextName}-err]")

  private fun getStdout() =
    if (verboseOutput) ExecOutputRedirect.ToStdOut("[ide-${contextName}-out]") else ExecOutputRedirect.ToString()

  private fun validateVMOptionsWereSet(paths: IDEDataPaths) {
    require(FileSystem.countFiles(paths.configDir) > 3) {
      "IDE must have created files under config directory at ${paths.configDir}. Were .vmoptions included correctly?"
    }

    require(FileSystem.countFiles(paths.systemDir) > 1) {
      "IDE must have created files under system directory at ${paths.systemDir}. Were .vmoptions included correctly?"
    }
  }

  private fun getErrorMessage(t: Throwable): String? {
    val failureCauseFile = testContext.paths.logsDir.resolve("failure_cause.txt")
    val errorMessage = if (Files.exists(failureCauseFile)) {
      Files.readString(failureCauseFile)
    }
    else {
      t.message ?: t.javaClass.name
    }
    return errorMessage
  }

  private fun deleteJVMCrashes() {
    listOf(heapDumpOnOomDirectory, jvmCrashLogDirectory).filter { dir ->
      dir.listDirectoryEntries().isEmpty()
    }.forEach { it.toFile().deleteRecursively() }
  }

  private fun runCloseHandlers(isRunSuccessful: Boolean) {
    val closeContext = object : IDERunCloseContext {
      override val wasRunSuccessful: Boolean = isRunSuccessful
    }

    closeHandlers.forEach {
      try {
        it.invoke(closeContext)
      }
      catch (t: Throwable) {
        logOutput("Failed to complete close step. ${t.message}.\n" + t)
        t.printStackTrace(System.err)
      }
    }
  }

  private fun logDisabledPlugins(paths: IDEDataPaths) {
    val disabledPlugins = paths.configDir.resolve("disabled_plugins.txt")
    if (disabledPlugins.toFile().exists()) {
      logOutput("The list of disabled plugins: " + disabledPlugins.toFile().readText())
    }
  }

  private suspend fun captureDiagnosticOnKill(logsDir: Path,
                                              jdkHome: Path,
                                              startConfig: IDEStartConfig,
                                              pid: Long,
                                              process: Process,
                                              snapshotsDir: Path) {
    catchAll {
      takeScreenshot(logsDir)
    }
    if (expectedKill) return
    val javaProcessId by lazy { getJavaProcessIdWithRetry(jdkHome, startConfig.workDir, pid, process) }

    if (collectNativeThreads) {
      val fileToStoreNativeThreads = logsDir.resolve("native-thread-dumps.txt")
      startProfileNativeThreads(javaProcessId.toString())
      delay(15.seconds)
      stopProfileNativeThreads(javaProcessId.toString(), fileToStoreNativeThreads.toAbsolutePath().toString())
    }
    val dumpFile = logsDir.resolve("threadDump-before-kill-${System.currentTimeMillis()}.txt")
    val memoryDumpFile = snapshotsDir.resolve("memoryDump-before-kill-${System.currentTimeMillis()}.hprof.gz")
    catchAll {
      collectJavaThreadDump(jdkHome, startConfig.workDir, javaProcessId, dumpFile)
    }
    catchAll {
      collectMemoryDump(jdkHome, startConfig.workDir, javaProcessId, memoryDumpFile)
    }
  }

  private suspend fun startCollectThreadDumpsLoop(logsDir: Path,
                                                  process: Process,
                                                  jdkHome: Path,
                                                  startConfig: IDEStartConfig,
                                                  ideProcessId: Long) {
    val monitoringThreadDumpDir = logsDir.resolve("monitoring-thread-dumps").createDirectories()

    var cnt = 0
    while (process.isAlive) {
      delay(1.minutes)
      if (!process.isAlive) break

      val dumpFile = monitoringThreadDumpDir.resolve("threadDump-${++cnt}-${System.currentTimeMillis()}.txt")
      logOutput("Dumping threads to $dumpFile")
      catchAll { collectJavaThreadDump(jdkHome, startConfig.workDir, ideProcessId, dumpFile) }
    }
  }

  private fun resolveAndDownloadSameJDK() = try {
    testContext.ide.resolveAndDownloadTheSameJDK()
  }
  catch (e: Exception) {
    logError("Failed to download the same JDK as in ${testContext.ide.build}")
    logError(e.stackTraceToString())

    val defaultJavaHome = resolveInstalledJdk11()
    logOutput("JDK is not found in ${testContext.ide.build}. Fallback to default java: $defaultJavaHome")
    defaultJavaHome
  }

  private fun logVmOptionDiff(vmOptionsDiff: VMOptionsDiff?) {
    if (vmOptionsDiff != null && !vmOptionsDiff.isEmpty) {
      logOutput("VMOptions were changed:")
      logOutput("new lines:")
      vmOptionsDiff.newLines.forEach { logOutput("  $it") }
      logOutput("removed lines:")
      vmOptionsDiff.missingLines.forEach { logOutput("  $it") }
      logOutput()
    }
  }

  private fun logStartupInfo(finalOptions: VMOptions) {
    logOutput(buildString {
      appendLine("Starting IDE for ${contextName} with timeout $runTimeout")
      appendLine("  VM Options: [" + finalOptions.toString().lineSequence().map { it.trim() }.joinToString(" ") + "]")
      appendLine("  On Java : [" + System.getProperty("java.home") + "]")
    })
  }

  private fun publishArtifacts(isRunSuccessful: Boolean) {
    val artifactPath = when (isRunSuccessful) {
      true -> contextName
      false -> "_crashes/$contextName"
    }
    testContext.publishArtifact(
      source = testContext.paths.logsDir,
      artifactPath = artifactPath,
      artifactName = formatArtifactName("logs", testContext.testName)
    )
    testContext.publishArtifact(
      source = testContext.paths.systemDir.resolve("event-log-data/logs/FUS"),
      artifactPath = artifactPath,
      artifactName = formatArtifactName("event-log-data", testContext.testName)
    )
    testContext.publishArtifact(
      source = testContext.paths.snapshotsDir,
      artifactPath = artifactPath,
      artifactName = formatArtifactName("snapshots", testContext.testName)
    )
    testContext.publishArtifact(
      source = testContext.paths.reportsDir,
      artifactPath = artifactPath,
      artifactName = formatArtifactName("reports", testContext.testName)
    )
  }

  private fun deleteSavedAppStateOnMac() {
    if (SystemInfo.isMac) {
      val filesToBeDeleted = listOf(
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}-EAP.savedState",
        "com.jetbrains.${testContext.testCase.ideInfo.installerProductName}.savedState"
      )
      val home = System.getProperty("user.home")
      val savedAppStateDir = Paths.get(home).resolve("Library").resolve("Saved Application State")
      savedAppStateDir.toFile()
        .walkTopDown().maxDepth(1)
        .filter { file -> filesToBeDeleted.any { fileToBeDeleted -> file.name == fileToBeDeleted } }
        .forEach { it.deleteRecursively() }
    }
  }
}
