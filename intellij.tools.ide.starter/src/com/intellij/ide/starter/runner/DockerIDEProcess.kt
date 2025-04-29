package com.intellij.ide.starter.runner

import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.report.ErrorReporter
import com.intellij.ide.starter.runner.events.IdeAfterLaunchEvent
import com.intellij.ide.starter.runner.events.IdeBeforeLaunchEvent
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.util.io.delete
import kotlinx.coroutines.delay
import java.io.Closeable
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeLines
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class DockerIDEProcess : IDEProcess {
  private val containerWorkDir = "/home/developer/workingDir"
  private val testProjectMapping = "/test-project"
  override suspend fun run(runContext: IDERunContext): IDEStartResult {
    var isRunSuccessful = true
    try {
      runContext.testContext.addProjectToTrustedLocations(Paths.get(testProjectMapping))
      with(runContext) {
        EventsBus.postAndWaitProcessing(IdeBeforeLaunchEvent(runContext))
        addVMOptionsPatch {
          addSystemProperty("com.sun.management.jmxremote.port", "7777")
          addSystemProperty("com.sun.management.jmxremote.rmi.port", "7778")
          addSystemProperty("com.sun.management.jmxremote.authenticate", "false")
          addSystemProperty("com.sun.management.jmxremote.ssl", "false")
          addSystemProperty("java.rmi.server.hostname", "0.0.0.0")
          addSystemProperty("com.sun.management.jmxremote.host", "0.0.0.0")
          addSystemProperty("com.sun.management.jmxremote.serial.filter.pattern", "'java.**;javax.**;com.intellij.driver.model.**'")
          addSystemProperty("idea.use.dev.build.server", "false")
        }
        val repoDir = testContext.paths.testHome.toAbsolutePath().toString().substringBefore("/out/ide-tests")

        val copyVm = testContext.ide.vmOptions.copy()
        copyVm.data().forEach {
          testContext.ide.vmOptions.removeLine(it)
          testContext.ide.vmOptions.addLine(it.replace(repoDir, containerWorkDir))
        }
        val vmOptions: VMOptions = calculateVmOptions()
        val scriptText = commands.joinToString(separator = System.lineSeparator()) { it.storeToString() }.replace(repoDir, containerWorkDir)
        testContext.ide.vmOptions.installTestScript(testName = contextName, paths = testContext.paths, scriptText = scriptText)
        val startConfig = testContext.ide.startConfig(vmOptions, logsDir)
        if (startConfig is Closeable) {
          EventsBus.subscribe(this) { event: IdeAfterLaunchEvent ->
            if (event.runContext === this) {
              startConfig.close()
            }
          }
        }
        val finalArgs: List<String> = (startConfig.commandLine + commandLine(this).args).map { it.replace(repoDir, containerWorkDir) }
        val vmFile = testContext.paths.testHome.resolve("app.vmotions")
        vmFile.delete()
        if (!vmFile.exists()) vmFile.createFile()
        vmOptions.environmentVariables
        val map: List<String> = vmOptions.data().map { it.replace(repoDir, containerWorkDir) }
        vmFile.writeLines(map)
        val config = ContainerConfig(arguments = finalArgs, env = vmOptions.environmentVariables, workingDir = repoDir, vmOptions = vmFile.toString(), testProject = this.testContext.resolvedProjectHome.toString())
        val executionTime = measureTime {
          val container = IdeContainer(config)
          Path(this.testContext.paths.systemDir.toString(), ".port").delete()
          Path(this.testContext.paths.systemDir.toString(), ".lock").delete()
          Path(this.testContext.paths.configDir.toString(), ".port").delete()
          Path(this.testContext.paths.configDir.toString(), ".lock").delete()
          container.container.start()
          EventsBus.postAndWaitProcessing(IdeLaunchEvent(runContext = this, ideProcess = DockerIDEHandle(container)))
          while (container.container.isRunning && container.container.execInContainer("ps").stdout.contains("idea")) {
            delay(2.seconds)
          }
          container.container.stop()
        }
        return IDEStartResult(runContext = this, executionTime = executionTime, vmOptionsDiff = startConfig.vmOptionsDiff())
      }
    }
    catch (e: Exception) {
      isRunSuccessful = false
      throw e
    }
    finally {
      EventsBus.postAndWaitProcessing(IdeAfterLaunchEvent(runContext = runContext, isRunSuccessful = isRunSuccessful))
      ErrorReporter.instance.reportErrorsAsFailedTests(runContext)
    }
  }
}