package com.intellij.ide.starter.driver

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.ide.starter.driver.engine.LogColor
import com.intellij.ide.starter.driver.engine.color
import com.intellij.ide.starter.report.AllureHelper
import com.intellij.ide.starter.report.AllureHelper.step
import com.intellij.ide.starter.telemetry.TestTelemetryService
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.util.common.logOutput

fun Driver.execute(commands: CommandChain, project: Project? = null) {
  waitForProjectOpen()
  for (cmd in commands) {
    val commandString = cmd.storeToString()
    val span = TestTelemetryService.spanBuilder("execute command").setAttribute("commands", commandString).startSpan()
    val split = commandString.split(" ")
    val stepName = "Execute command " + split.first()
    step(stepName) {
      if (split.size > 1) {
        AllureHelper.attachText("Params", split.drop(1).joinToString(" "))
      }
      service<PlaybackRunnerService>().runScript(project ?: singleProject(), commandString)
    }
    span.end()
  }
}

fun Driver.execute(project: Project? = null, commands: (CommandChain) -> CommandChain) {
  execute(project = project, commands = commands(CommandChain()))
}

val Driver.ideLogger: DriverTestLogger
  get() = utility(DriverTestLogger::class)

fun <T> DriverTestLogger.run(text: String, action: () -> T): T = try {
  val startedText = "$text started"
  logOutput(startedText.color(LogColor.GREEN))
  info(startedText)
  val actionStarted = System.currentTimeMillis()
  val result = action()
  val time = System.currentTimeMillis() - actionStarted
  val finishedText = "$text finished in ${time}ms"
  logOutput(finishedText.color(LogColor.GREEN))
  info(finishedText)
  result
} catch (e: Throwable) {
  warn("$text failed with '${e.message}'")
  throw e
}

@Remote("com.jetbrains.performancePlugin.DriverTestLogger", plugin = "com.jetbrains.performancePlugin")
interface DriverTestLogger {
  fun info(text: String)
  fun warn(text: String)
}