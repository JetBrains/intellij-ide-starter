package com.intellij.ide.starter.examples.junit5

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.starter.examples.data.TestCases
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.junit5.getName
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.delay
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import com.intellij.tools.ide.performanceTesting.commands.importGradleProject
import com.intellij.tools.ide.performanceTesting.commands.openFile
import com.intellij.tools.ide.performanceTesting.commands.waitForCodeAnalysisFinished
import com.intellij.tools.ide.performanceTesting.commands.waitForSmartMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

@ExtendWith(JUnit5StarterAssistant::class)
class StartupPerformanceTest {
  @Test
  fun testStartupPerformance() {
    var pathToStats: Path? = null
    val context = Starter.newContext(CurrentTestMethod.getName(), TestCases.IC.GradleJitPackSimple)

    context.runIDE(commands = CommandChain().importGradleProject().waitForSmartMode().openFile("src/main/java/Hello.java").exitApp(), runTimeout = 10.minutes, launchName = "warmup")
    context
      .runIDE(commands = CommandChain().waitForCodeAnalysisFinished().delay(1000).exitApp(), runTimeout = 5.minutes, launchName = "startup") {
        addVMOptionsPatch {
          val statsJson = getStartupStatsJson()
          pathToStats = statsJson
          addSystemProperty("idea.record.classloading.stats", true)
          addSystemProperty("idea.log.perf.stats.file", statsJson)
        }
      }
    println("Total startup time: ${ObjectMapper().readTree(pathToStats!!.toFile()).get("totalDuration")} ms")
  }

  fun IDERunContext.getStartupStatsJson(): Path = reportsDir / "startup" / "startup-stats.json"
}