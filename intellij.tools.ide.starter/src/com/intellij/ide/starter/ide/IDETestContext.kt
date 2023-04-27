package com.intellij.ide.starter.ide

import com.intellij.ide.starter.buildTool.BuildToolProvider
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.command.CommandChain
import com.intellij.ide.starter.ide.command.MarshallableCommand
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.models.andThen
import com.intellij.ide.starter.path.IDEDataPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.profiler.ProfilerType
import com.intellij.ide.starter.report.publisher.ReportPublisher
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.logOutput
import com.intellij.openapi.diagnostic.LogLevel
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
  var patchVMOptions: VMOptions.() -> VMOptions,
  val ciServer: CIServer,
  var profilerType: ProfilerType = ProfilerType.NONE,
  val publishers: List<ReportPublisher> = di.direct.instance(),
  var isReportPublishingEnabled: Boolean = true
) {
  companion object {
    const val OPENTELEMETRY_FILE = "opentelemetry.json"
  }

  val resolvedProjectHome: Path
    get() = checkNotNull(_resolvedProjectHome) { "Project is not found for the test $testName" }

  val pluginConfigurator: PluginConfigurator by di.newInstance { factory<IDETestContext, PluginConfigurator>().invoke(this@IDETestContext) }

  val buildTools: BuildToolProvider by di.newInstance { factory<IDETestContext, BuildToolProvider>().invoke(this@IDETestContext) }

  fun addVMOptionsPatch(patchVMOptions: VMOptions.() -> VMOptions): IDETestContext {
    this.patchVMOptions = this.patchVMOptions.andThen(patchVMOptions)
    return this
  }

  fun addLockFileForUITest(fileName: String): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("uiLockTempFile", paths.tempDir / fileName)
    }

  fun disableLinuxNativeMenuForce(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("linux.native.menu.force.disable", true)
    }

  fun setMemorySize(sizeMb: Int): IDETestContext =
    addVMOptionsPatch {
      this
        .withXmx(sizeMb)
    }

  fun setActiveProcessorCount(count: Int): IDETestContext =
    addVMOptionsPatch {
      this
        .withActiveProcessorCount(count)
    }

  fun withGCLogs(): IDETestContext =
    addVMOptionsPatch { withGCLogs(paths.reportsDir / "gcLog.log") }

  fun disableGitLogIndexing(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("vcs.log.index.git", false)
    }

  fun executeRightAfterIdeOpened(executeRightAfterIdeOpened: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("performance.execute.script.right.after.ide.opened", executeRightAfterIdeOpened)
  }

  fun executeDuringIndexing(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("performance.execute.script.after.scanning", true)
    }

  fun withGtk2OnLinux(): IDETestContext =
    addVMOptionsPatch {
      this
        .addSystemProperty("jdk.gtk.verbose", true)
        .let {
          // Desperate attempt to fix JBR-2783
          if (SystemInfo.isLinux) {
            it.addSystemProperty("jdk.gtk.version", 2)
          }
          else {
            it
          }
        }
    }

  fun disableInstantIdeShutdown(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.instant.shutdown", false)
    }

  fun useNewUIInTests(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.experimental.ui", true)
    }

  fun useOldUIInTests(): IDETestContext =
    addVMOptionsPatch {
      removeSystemProperty("ide.experimental.ui", true.toString())
    }

  fun enableSlowOperationsInEdtInTests(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.slow.operations.assertion", false)
    }

  /**
   * Does not allow IDE to fork a process that sends FUS statistics on IDE shutdown.
   * On Windows that forked process may prevent some files from removing.
   * See [com.intellij.internal.statistic.EventLogApplicationLifecycleListener]
   */
  fun disableFusSendingOnIdeClose(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("feature.usage.event.log.send.on.ide.close", false)
    }

  fun withVerboseIndexingDiagnostics(dumpPaths: Boolean = false): IDETestContext =
    addVMOptionsPatch {
      this
        .addSystemProperty("intellij.indexes.diagnostics.should.dump.for.interrupted.index.updaters", true)
        .addSystemProperty("intellij.indexes.diagnostics.limit.of.files", 10000)
        .addSystemProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", dumpPaths)
        // Dumping of lists of indexed file paths may require a lot of memory.
        .withXmx(4 * 1024)
    }

  fun setPathForMemorySnapshot(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("memory.snapshots.path", paths.logsDir)
    }

  fun setPathForSnapshots(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("snapshots.path", paths.snapshotsDir)
    }

  @Suppress("unused")
  fun collectMemorySnapshotOnFailedPluginUnload(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.snapshot.on.unload.fail", true)
    }

  @Suppress("unused")
  fun setPerProjectDynamicPluginsFlag(): IDETestContext =
    addVMOptionsPatch {
      addSystemProperty("ide.plugins.per.project", true)
    }

  // seems, doesn't work for Maven
  fun disableAutoImport(disabled: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("external.system.auto.import.disabled", disabled)
  }

  fun disableOrdinaryIndexes() = addVMOptionsPatch {
    addSystemProperty("idea.use.only.index.infrastructure.extension", true)
  }

  fun setSharedIndexesDownload(enable: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("shared.indexes.bundled", enable)
      .addSystemProperty("shared.indexes.download", enable)
      .addSystemProperty("shared.indexes.download.auto.consent", enable)
  }

  fun skipIndicesInitialization(value: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("idea.skip.indices.initialization", value)
  }

  fun enableAsyncProfiler() = addVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "async")
  }

  @Suppress("unused")
  fun enableYourKitProfiler() = addVMOptionsPatch {
    addSystemProperty("integrationTests.profiler", "yourkit")
  }

  fun doRefreshAfterJpsLibraryDownloaded(value: Boolean = true) = addVMOptionsPatch {
    addSystemProperty("idea.do.refresh.after.jps.library.downloaded", value)
  }

  fun collectImportProjectPerfMetrics() = addVMOptionsPatch {
    addSystemProperty("idea.collect.project.import.performance", true)
  }

  fun collectOpenTelemetry() = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.file", paths.logsDir.resolve(OPENTELEMETRY_FILE))
  }

  fun enableVerboseOpenTelemetry() = addVMOptionsPatch {
    addSystemProperty("idea.diagnostic.opentelemetry.verbose", true)
  }

  fun enableWorkspaceModelVerboseLogs() = addVMOptionsPatch {
    configureLoggers(LogLevel.TRACE, "com.intellij.workspaceModel")
  }

  fun wipeSystemDir() = apply {
    //TODO: it would be better to allocate a new context instead of wiping the folder
    logOutput("Cleaning system dir for $this at $paths")
    paths.systemDir.toFile().deleteRecursively()
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

  fun runContext(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commandLine: IDECommandLine? = null,
    commands: Iterable<MarshallableCommand> = CommandChain(),
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    collectNativeThreads: Boolean = false
  ): IDERunContext {
    return IDERunContext(testContext = this)
      .copy(
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
  }

  /**
   * Setup profiler injection
   */
  fun setProfiler(profilerType: ProfilerType): IDETestContext {
    this.profilerType = profilerType
    return this
  }

  fun internalMode(value: Boolean = true) = addVMOptionsPatch { addSystemProperty("idea.is.internal", value) }

  /**
   * Cleans .idea and removes all the .iml files for project
   */
  fun prepareProjectCleanImport(): IDETestContext {
    return removeIdeaProjectDirectory().removeAllImlFilesInProject()
  }

  fun disableAutoSetupJavaProject() = addVMOptionsPatch {
    addSystemProperty("idea.java.project.setup.disabled", true)
  }

  fun disablePackageSearchBuildFiles() = addVMOptionsPatch {
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

  fun runIDE(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commandLine: IDECommandLine? = null,
    commands: Iterable<MarshallableCommand> = CommandChain(),
    codeBuilder: (CodeInjector.() -> Unit)? = null,
    runTimeout: Duration = 10.minutes,
    useStartupScript: Boolean = true,
    launchName: String = "",
    expectedKill: Boolean = false,
    collectNativeThreads: Boolean = false
  ): IDEStartResult {
    val context = runContext(commandLine = commandLine, commands = commands, codeBuilder = codeBuilder, runTimeout = runTimeout,
                             useStartupScript = useStartupScript, launchName = launchName, expectedKill = expectedKill,
                             collectNativeThreads = collectNativeThreads, patchVMOptions = patchVMOptions)
    try {
      val ideRunResult = context.runIDE()
      if (isReportPublishingEnabled) publishers.forEach {
        it.publishResult(ideRunResult)
      }
      if (ideRunResult.failureError != null) throw ideRunResult.failureError
      return ideRunResult
    }
    finally {
      if (isReportPublishingEnabled) publishers.forEach {
        it.publishAfterRun(context.testContext)
      }
    }
  }

  fun warmUp(
    patchVMOptions: VMOptions.() -> VMOptions = { this },
    commands: Iterable<MarshallableCommand>,
    runTimeout: Duration = 10.minutes,
    storeClassReport: Boolean = false
  ): IDEStartResult {
    val updatedContext = this.copy(testName = "${this.testName}/warmup")
    val result = updatedContext.runIDE(
      patchVMOptions = {
        this.run {
          if (storeClassReport) {
            this.enableClassLoadingReport(paths.reportsDir / "class-report.txt")
          }
          else {
            this
          }
        }.patchVMOptions()
      },
      commands = testCase.commands.plus(commands),
      runTimeout = runTimeout
    )
    updatedContext.publishArtifact(this.paths.reportsDir)
    return result
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
                      artifactName: String = source.fileName.toString()) = ciServer.publishArtifact(source, artifactPath, artifactName)

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
}

