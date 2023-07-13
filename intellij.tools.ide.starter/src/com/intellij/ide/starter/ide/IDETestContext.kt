package com.intellij.ide.starter.ide

import com.intellij.driver.model.SdkObject
import com.intellij.driver.model.command.CommandChain
import com.intellij.driver.model.command.MarshallableCommand
import com.intellij.ide.starter.buildTool.BuildTool
import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.bus.subscribe
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.coroutine.supervisorScope
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.frameworks.Framework
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.IdeLaunchEvent
import com.intellij.ide.starter.screenRecorder.IDEScreenRecorder
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.replaceSpecialCharacters
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.ui.NewUiValue
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.apache.commons.io.FileUtils
import org.kodein.di.direct
import org.kodein.di.factory
import org.kodein.di.instance
import org.kodein.di.newInstance
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class IDETestContext(
  val paths: IDEDataPaths,
  val ide: InstalledIde,
  val testCase: TestCase,
  val testName: String,
  private val _resolvedProjectHome: Path?,
  val ciServer: CIServer,
  var profilerType: ProfilerType = ProfilerType.NONE,
  val publishers: List<ReportPublisher> = di.direct.instance(),
  var isReportPublishingEnabled: Boolean = true,
  private var preserveSystemDir: Boolean = false
) {
  companion object {
    const val OPENTELEMETRY_FILE = "opentelemetry.json"
  }

  val resolvedProjectHome: Path
    get() = checkNotNull(_resolvedProjectHome) { "Project directory is not specified for the test '$testName'" }

  val pluginConfigurator: PluginConfigurator by di.newInstance { factory<IDETestContext, PluginConfigurator>().invoke(this@IDETestContext) }

  inline fun <reified T, reified M : T> getInstanceFromBindSet(): M {
    val bindings: Set<T> by di.instance(arg = this@IDETestContext)
    return bindings.filterIsInstance<M>().single()
  }

  inline fun <reified M : BuildTool> withBuildTool(): M = getInstanceFromBindSet<BuildTool, M>()

  inline fun <reified M : Framework> withFramework(): M = getInstanceFromBindSet<Framework, M>()

  /**
   * Make sure that tests are run with: `-Djava.awt.headless=false` option
   */
  fun withScreenRecording(): IDETestContext {
    val screenRecorder = IDEScreenRecorder(this)
    StarterListener.subscribe { event: IdeLaunchEvent ->
      if (event.state == EventState.BEFORE) {
        screenRecorder.start()
      }
      else if (event.state == EventState.AFTER) {
        screenRecorder.stop()
      }
    }
    return this
  }

  /**
   * Method applies patch immediately to the whole context.
   * If you want apply VMOptions just for a single run, use [IDERunContext.addVMOptionsPatch].
   */
  fun applyVMOptionsPatch(patchVMOptions: VMOptions.() -> Unit): IDETestContext {
    ide.vmOptions.patchVMOptions()
    return this
  }

  fun addIndexOperationFUSReportDetailed(enabled: Boolean): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("IndexOperationFUS.REPORT_DETAILED_STATS_TO_OPEN_TELEMETRY", enabled)
    }

  fun addLockFileForUITest(fileName: String): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("uiLockTempFile", paths.tempDir / fileName)
    }

  fun disableLinuxNativeMenuForce(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("linux.native.menu.force.disable", true)
    }

  fun setMemorySize(sizeMb: Int): IDETestContext =
    applyVMOptionsPatch {
      withXmx(sizeMb)
    }

  fun setActiveProcessorCount(count: Int): IDETestContext =
    applyVMOptionsPatch {
      withActiveProcessorCount(count)
    }

  fun withGCLogs(): IDETestContext =
    applyVMOptionsPatch { withGCLogs(paths.reportsDir / "gcLog.log") }

  fun toggleGitLogIndexing(isEnabled: Boolean = false): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("vcs.log.index.git", isEnabled)
    }

  fun executeRightAfterIdeOpened(executeRightAfterIdeOpened: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("performance.execute.script.right.after.ide.opened", executeRightAfterIdeOpened)
  }

  fun executeDuringIndexing(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("performance.execute.script.after.scanning", true)
    }

  fun withGtk2OnLinux(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("jdk.gtk.verbose", true)
      if (SystemInfo.isLinux) {
        addSystemProperty("jdk.gtk.version", 2)
      }
    }

  fun disableInstantIdeShutdown(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.instant.shutdown", false)
    }

  fun useNewUIInTests(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty(NewUiValue.KEY, true)
    }

  fun useOldUIInTests(): IDETestContext =
    applyVMOptionsPatch {
      removeSystemProperty(NewUiValue.KEY, true)
    }

  fun enableSlowOperationsInEdtInTests(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.slow.operations.assertion", false)
    }

  /**
   * Does not allow IDE to fork a process that sends FUS statistics on IDE shutdown.
   * On Windows that forked process may prevent some files from removing.
   * See [com.intellij.internal.statistic.EventLogApplicationLifecycleListener]
   */
  fun disableFusSendingOnIdeClose(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("feature.usage.event.log.send.on.ide.close", false)
    }

  fun disableReportingStatisticsToProduction(disabled: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("idea.local.statistics.without.report", disabled)
  }

  fun withVerboseIndexingDiagnostics(dumpPaths: Boolean = false): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", true)
      addSystemProperty("intellij.indexes.diagnostics.limit.of.files", 10000)
      addSystemProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", dumpPaths)
      // Dumping of lists of indexed file paths may require a lot of memory.
      withXmx(4 * 1024)
    }

  fun setPathForMemorySnapshot(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("memory.snapshots.path", paths.logsDir)
    }

  fun setPathForSnapshots(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("snapshots.path", paths.snapshotsDir)
    }

  @Suppress("unused")
  fun collectMemorySnapshotOnFailedPluginUnload(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.plugins.snapshot.on.unload.fail", true)
    }

  @Suppress("unused")
  fun setPerProjectDynamicPluginsFlag(): IDETestContext =
    applyVMOptionsPatch {
      addSystemProperty("ide.plugins.per.project", true)
    }

  // seems, doesn't work for Maven
  fun disableAutoImport(disabled: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("external.system.auto.import.disabled", disabled)
  }

  fun disableOrdinaryIndexes() = applyVMOptionsPatch {
    addSystemProperty("idea.use.only.index.infrastructure.extension", true)
  }

  fun setSharedIndexesDownload(enable: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("shared.indexes.bundled", enable)
    addSystemProperty("shared.indexes.download", enable)
    addSystemProperty("shared.indexes.download.auto.consent", enable)
  }

  fun skipIndicesInitialization(value: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("idea.skip.indices.initialization", value)
  }

  fun enableAsyncProfiler() = applyVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "async")
  }

  fun enableYourKitProfiler() = applyVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "yourkit")
  }

  fun doRefreshAfterJpsLibraryDownloaded(value: Boolean = true) = applyVMOptionsPatch {
    addSystemProperty("idea.do.refresh.after.jps.library.downloaded", value)
  }

  fun collectImportProjectPerfMetrics() = applyVMOptionsPatch {
    addSystemProperty("idea.collect.project.import.performance", true)
  }

  fun collectOpenTelemetry() = applyVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.file", paths.logsDir.resolve(OPENTELEMETRY_FILE))
  }

  fun enableVerboseOpenTelemetry() = applyVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.verbose", true)
  }

  fun enableWorkspaceModelVerboseLogs() = applyVMOptionsPatch {
    configureLoggers(LogLevel.TRACE, "com.intellij.workspaceModel")
  }

  fun wipeSystemDir() = apply {
    if (!preserveSystemDir) {
      //TODO: it would be better to allocate a new context instead of wiping the folder
      logOutput("Cleaning system dir for $this at $paths")
      paths.systemDir.toFile().deleteRecursively()
    }
    else {
      logOutput("Cleaning system dir for $this at $paths is disabled due to preserveSystemDir")
    }
  }

  fun wipeLogsDir() = apply {
    //TODO: it would be better to allocate a new context instead of wiping the folder
    logOutput("Cleaning logs dir for $this at $paths")
    paths.logsDir.toFile().deleteRecursively()
  }

  fun wipeReportDir() = apply {
    logOutput("Cleaning report dir for $this at $paths")
    Files.walk(paths.reportsDir)
      .filter { Files.isRegularFile(it) }
      .map { it.toFile() }
      .forEach { it.delete() }
  }

  fun wipeGcLogs() = apply {
    logOutput("removing gclogs files for $this at $paths")
    Files.walk(paths.reportsDir)
      .filter { Files.isRegularFile(it) && it.name.startsWith("gcLog.log") }
      .map { it.toFile() }
      .forEach { it.delete() }
  }

  fun wipeProjectsDir() = apply {
    val path = paths.systemDir / "projects"
    logOutput("Cleaning project cache dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeEventLogDataDir() = apply {
    val path = paths.systemDir / "event-log-data"
    logOutput("Cleaning event-log-data dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeSnapshotDir() = apply {
    val path = paths.snapshotsDir
    logOutput("Cleaning snapshot dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  fun wipeWorkspaceState() = apply {
    val path = paths.configDir.resolve("workspace")
    logOutput("Cleaning workspace dir in config dir for $this at $path")
    path.toFile().deleteRecursively()
  }

  /**
   * Setup profiler injection
   */
  fun setProfiler(profilerType: ProfilerType): IDETestContext {
    this.profilerType = profilerType
    return this
  }

  fun internalMode(value: Boolean = true) = applyVMOptionsPatch { addSystemProperty("idea.is.internal", value) }

  /**
   * Cleans .idea and removes all the .iml files for project
   */
  fun prepareProjectCleanImport(): IDETestContext {
    return removeIdeaProjectDirectory().removeAllImlFilesInProject()
  }

  fun disableAutoSetupJavaProject() = applyVMOptionsPatch {
    addSystemProperty("idea.java.project.setup.disabled", true)
  }

  fun disablePackageSearchBuildFiles() = applyVMOptionsPatch {
    addSystemProperty("idea.pkgs.disableLoading", true)
  }

  fun removeIdeaProjectDirectory(): IDETestContext {
    val ideaDirPath = resolvedProjectHome.resolve(".idea")

    logOutput("Removing $ideaDirPath ...")

    if (ideaDirPath.notExists()) {
      logOutput("Idea project directory $ideaDirPath doesn't exist. So, it will not be deleted")
      return this
    }

    ideaDirPath.toFile().deleteRecursively()
    return this
  }

  fun removeAllImlFilesInProject(): IDETestContext {
    val projectDir = resolvedProjectHome

    logOutput("Removing all .iml files in $projectDir ...")

    projectDir.toFile().walkTopDown()
      .forEach {
        if (it.isFile && it.extension == "iml") {
          it.delete()
          logOutput("File ${it.path} is deleted")
        }
      }

    return this
  }

  /**
   * Entry point to run IDE.
   * If you want to run IDE without any project on start use [com.intellij.ide.starter.runner.IDECommandLine.StartIdeWithoutProject]
   */
  fun runIDE(
    commandLine: IDECommandLine = IDECommandLine.OpenTestCaseProject(this),
    commands: Iterable<MarshallableCommand> = CommandChain(),
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    collectNativeThreads: Boolean = false,
    patchVMOptions: VMOptions.() -> Unit = { }
  ): IDEStartResult {
    val runContext = IDERunContext(testContext = this,
                                   commandLine = commandLine,
                                   commands = commands,
                                   codeBuilder = codeBuilder,
                                   runTimeout = runTimeout,
                                   useStartupScript = useStartupScript,
                                   launchName = launchName,
                                   expectedKill = expectedKill,
                                   collectNativeThreads = collectNativeThreads
    )
      .addVMOptionsPatch(patchVMOptions)

    try {
      val ideRunResult = runContext.runIDE()
      if (isReportPublishingEnabled) publishers.forEach {
        it.publishResultOnSuccess(ideRunResult)
      }
      if (ideRunResult.failureError != null) throw ideRunResult.failureError
      return ideRunResult
    }
    finally {
      if (isReportPublishingEnabled) publishers.forEach {
        it.publishAnywayAfterRun(runContext.testContext)
      }
    }
  }

  /**
   * Run IDE in background.
   * If you want to know, when it will be launched/closed you may rely on event [IdeLaunchEvent] and subscribe on it via [StarterListener.subscribe]
   */
  fun runIdeInBackground(commandLine: IDECommandLine = run {
    if (this.testCase.projectInfo == null) IDECommandLine.StartIdeWithoutProject
    else IDECommandLine.OpenTestCaseProject(this)
  },
                         commands: Iterable<MarshallableCommand> = CommandChain(),
                         codeBuilder: (CodeInjector.() -> Unit)? = null,
                         runTimeout: Duration = 10.minutes,
                         useStartupScript: Boolean = true,
                         launchName: String = "",
                         expectedKill: Boolean = false,
                         collectNativeThreads: Boolean = false,
                         patchVMOptions: VMOptions.() -> Unit = { }): Deferred<IDEStartResult> {

    return supervisorScope.async {
      try {
        runIDE(commandLine,
               commands,
               codeBuilder,
               runTimeout,
               useStartupScript,
               launchName,
               expectedKill,
               collectNativeThreads,
               patchVMOptions)
      }
      catch (e: Throwable) {
        logError("Error during IDE execution", e)
        throw e
      }
    }
  }

  fun removeAndUnpackProject(): IDETestContext {
    testCase.markNotReusable().projectInfo?.downloadAndUnpackProject()
    return this
  }

  fun setProviderMemoryOnlyOnLinux(): IDETestContext {
    if (!SystemInfo.isLinux) return this

    val optionsConfig = paths.configDir.resolve("options")
    optionsConfig.toFile().mkdirs()
    val securityXml = optionsConfig.resolve("security.xml")
    securityXml.toFile().createNewFile()
    securityXml.toFile().writeText("""<application>
  <component name="PasswordSafe">
    <option name="PROVIDER" value="MEMORY_ONLY" />
  </component>
</application>""")

    return this
  }

  fun updateGeneralSettings(): IDETestContext {
    val patchedIdeGeneralXml = this::class.java.classLoader.getResourceAsStream("ide.general.xml")
    val pathToGeneralXml = paths.configDir.toAbsolutePath().resolve("options/ide.general.xml")

    if (!pathToGeneralXml.exists()) {
      pathToGeneralXml.parent.createDirectories()
      patchedIdeGeneralXml.use {
        if (it != null) {
          pathToGeneralXml.writeBytes(it.readAllBytes())
        }
      }
    }
    return this
  }

  fun disableMinimap(): IDETestContext {
    val miniMapConfig = paths.configDir.toAbsolutePath().resolve("options/Minimap.xml")
    if (!miniMapConfig.exists()) {
      miniMapConfig.parent.createDirectories()
      miniMapConfig.writeText("""
        <application>
          <component name="Minimap">
            <option name="enabled" value="false" />
          </component>
        </application>
      """.trimIndent())
    }
    return this
  }

  @Suppress("unused")
  fun setLicense(pathToFileWithLicense: Path): IDETestContext {
    val supportedProducts = listOf(IdeProductProvider.IU.productCode, IdeProductProvider.RM.productCode, IdeProductProvider.WS.productCode,
                                   IdeProductProvider.PS.productCode, IdeProductProvider.PS.productCode, IdeProductProvider.PS.productCode,
                                   IdeProductProvider.GO.productCode, IdeProductProvider.PY.productCode, IdeProductProvider.DB.productCode,
                                   IdeProductProvider.CL.productCode)
    if (this.ide.productCode !in supportedProducts) {
      error("Setting license to the product ${this.ide.productCode} is not supported")
    }
    return setLicense(String(Base64.getEncoder().encode(pathToFileWithLicense.toFile().readBytes())))
  }

  /**
   * license is Base64 encoded string that contains key
   */
  fun setLicense(license: String?): IDETestContext {
    if (license == null) {
      logOutput("License is not provided")
      return this
    }
    val licenseKeyFileName: String = when (this.ide.productCode) {
      IdeProductProvider.IU.productCode -> "idea.key"
      IdeProductProvider.RM.productCode -> "rubymine.key"
      IdeProductProvider.WS.productCode -> "webstorm.key"
      IdeProductProvider.PS.productCode -> "phpstorm.key"
      IdeProductProvider.GO.productCode -> "goland.key"
      IdeProductProvider.PY.productCode -> "pycharm.key"
      IdeProductProvider.DB.productCode -> "datagrip.key"
      IdeProductProvider.CL.productCode -> "clion.key"
      else -> return this
    }
    val keyFile = paths.configDir.resolve(licenseKeyFileName).toFile()
    keyFile.createNewFile()
    keyFile.writeBytes(Base64.getDecoder().decode(license))
    logOutput("License is set")
    return this
  }

  fun setLightTheme(): IDETestContext {
    val lafXml = paths.configDir.resolve("options").resolve("laf.xml").toFile()
    lafXml.createNewFile()
    lafXml.writeText("""<application>
  <component name="LafManager" autodetect="false">
    <laf class-name="com.intellij.ide.ui.laf.IntelliJLaf" themeId="JetBrainsLightTheme" />
  </component>
</application>""")
    return this
  }

  fun publishArtifact(source: Path,
                      artifactPath: String = testName,
                      artifactName: String = source.fileName.toString()) = ciServer.publishArtifact(source,
                                                                                                    artifactPath.replaceSpecialCharacters(ignoreSlash = true),
                                                                                                    artifactName.replaceSpecialCharacters())

  @Suppress("unused")
  fun withReportPublishing(isEnabled: Boolean): IDETestContext {
    isReportPublishingEnabled = isEnabled
    return this
  }

  fun addProjectToTrustedLocations(addParentDir: Boolean = false): IDETestContext {
    if (this.testCase.projectInfo != null) {
      val projectPath = this.resolvedProjectHome.normalize()
      val trustedXml = paths.configDir.toAbsolutePath().resolve("options/trusted-paths.xml")

      trustedXml.parent.createDirectories()
      if (addParentDir) {
        val text = this::class.java.classLoader.getResource("trusted-paths-settings.xml")!!.readText()
        trustedXml.writeText(
          text.replace("""<entry key="" value="true" />""", "<entry key=\"$projectPath\" value=\"true\" />")
            .replace("""<option value="" />""", "<option value=\"${projectPath.parent}\" />")
        )
      }
      else {
        val text = this::class.java.classLoader.getResource("trusted-paths.xml")!!.readText()
        trustedXml.writeText(
          text.replace("""<entry key="" value="true" />""", "<entry key=\"$projectPath\" value=\"true\" />")
        )
      }
    }
    return this
  }

  @Suppress("unused")
  fun copyExistingConfig(configPath: Path): IDETestContext {
    FileUtils.copyDirectory(configPath.toFile(), paths.configDir.toFile())
    return this
  }

  @Suppress("unused")
  fun copyExistingPlugins(pluginPath: Path): IDETestContext {
    FileUtils.copyDirectory(pluginPath.toFile(), paths.pluginsDir.toFile())
    return this
  }

  fun setupSdk(sdkObjects: SdkObject?, cleanDirs: Boolean = true): IDETestContext {
    if (sdkObjects == null) return this

    disableAutoImport(true)
      .executeRightAfterIdeOpened(true)
      .runIDE(
        commands = CommandChain()
          // TODO: hack to remove direct dependency on [intellij.tools.ide.performanceTesting.commands] module
          // It looks like actual shortcut from test code, so a proper solution for this should be implemented
          .addCommand("%setupSDK \"${sdkObjects.sdkName}\" \"${sdkObjects.sdkType}\" \"${sdkObjects.sdkPath}\"")
          .addCommand("%exitApp true"),
        launchName = "setupSdk",
        runTimeout = 3.minutes
      )

    if (cleanDirs)
      this
        //some caches from IDE warmup may stay
        .wipeSystemDir()
        //some logs and perf snapshots may stay
        .wipeLogsDir()


    return this
      // rollback changes, that were made only to setup sdk
      .disableAutoImport(false)
      .executeRightAfterIdeOpened(false)
  }
}

