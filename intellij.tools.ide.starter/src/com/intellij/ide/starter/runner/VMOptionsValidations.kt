package com.intellij.ide.starter.runner

import com.intellij.tools.ide.starter.bus.Event
import com.intellij.tools.ide.starter.bus.EventState
import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.utils.FileSystem
import com.intellij.tools.ide.util.common.logOutput
import kotlin.time.Duration.Companion.seconds


class ValidateVMOptionsWereSetEvent(state: EventState, data: IDERunContext) : Event<IDERunContext>(state, data)

internal fun validateVMOptionsWereSet(runContext: IDERunContext) {
  StarterBus.postAndWaitProcessing(ValidateVMOptionsWereSetEvent(state = EventState.IN_TIME, data = runContext), timeout = 15.seconds)

  logOutput("Run VM options validation")

  if (FileSystem.countFiles(runContext.testContext.paths.configDir) <= 3) {
    CIServer.instance.reportTestFailure(
      testName = "IDE must have created files under config directory at ${runContext.testContext.paths.configDir}. Were .vmoptions included correctly?",
      message = "", details = "")
  }

  if (FileSystem.countFiles(runContext.testContext.paths.systemDir) <= 1) {
    CIServer.instance.reportTestFailure(
      testName = "IDE must have created files under system directory at ${runContext.testContext.paths.systemDir}. Were .vmoptions included correctly?",
      message = "", details = "")
  }

  logOutput("Finished VM options validation")
}