package com.intellij.ide.starter.examples

import TestDurationExtension
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.utils.Git
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics.Metric
import com.intellij.tools.ide.metrics.collector.starter.fus.StatisticsEventsHarvester
import com.intellij.tools.ide.metrics.collector.starter.fus.filterByEventId
import com.intellij.tools.ide.metrics.collector.starter.fus.getDataFromEvent
import com.intellij.tools.ide.metrics.collector.starter.metrics.extractIndexingMetrics
import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter
import com.intellij.tools.ide.metrics.collector.telemetry.getMetricsFromSpanAndChildren
import com.intellij.tools.ide.performanceTesting.commands.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.*
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.concurrent.thread


@ExtendWith(TestDurationExtension::class)
class MonolithPerformanceTests{

  private lateinit var context: IDETestContext

  @ParameterizedTest
  @ValueSource(strings = ["2023.1","2024.1"])
  fun openProjectIndexing(version:String) {
    context = MonolithSetup.setupTestContext(version)
    val commands = CommandChain().startProfile("indexing").waitForSmartMode().stopProfile().exitApp()
    val contextForIndexing = context.copy().executeDuringIndexing()
    val results = contextForIndexing.runIDE(commands = commands, launchName = "indexing")
    val indexingMetrics = extractIndexingMetrics(results).getListOfIndexingMetrics()
    writeMetricsToCSV(results, indexingMetrics)
  }

  private fun writeMetricsToCSV(results: IDEStartResult, metrics: List<Metric>, name: String =""): Path {
    var resultCsv = results.runContext.reportsDir / "result.csv$name"
    println("#".repeat(20))
    println("Storing metrics to CSV")
    resultCsv.bufferedWriter().use { writer ->
      metrics.forEach { metric ->
        writer.write(metric.id.name + "," + metric.value)
        println("${metric.id.name}: ${metric.value}")
        writer.newLine()
      }
    }
    println("Result CSV is written to: ${resultCsv.absolutePathString()}")
    println("#".repeat(20))
    return resultCsv
  }

  fun executeCliCommandsWithLiveOutput(commands: List<String>, logFilePath: String) {
    // Create a ProcessBuilder with the shell
    val processBuilder = ProcessBuilder()
    processBuilder.environment()["TERM"] = "xterm"

    // Join commands with '&&' and ensure correct formatting
    var commandString = commands.joinToString(" && ") { it.replace("\n", " ") }
    // increase ulimit so that it can allow for more files to be open. Necessary for `ok mono build`
    commandString = "ulimit -n 100000 && $commandString"
    processBuilder.command("sh", "-c", commandString)

    val logFile = File(logFilePath)

    // Start the process
    val process = processBuilder.start()

    // Thread to read and print the standard output
    val outputReader = thread {
      val reader = BufferedReader(InputStreamReader(process.inputStream))
      val logWriter = FileWriter(logFile, true)
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        println(line)  // Print each line from standard output
        logWriter.write(line + "\n")
      }

      logWriter.close()
    }

    // Thread to read and print the error output
    val errorReader = thread {
      val reader = BufferedReader(InputStreamReader(process.errorStream))
      val logWriter = FileWriter(logFile, true)
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        println("ERROR: $line")  // Print each line from error output with a prefix
        logWriter.write(line + "\n")
      }
    }

    // Wait for the process to complete
    val exitCode = process.waitFor()

    // Ensure the threads finish reading
    outputReader.join()
    errorReader.join()

    if (exitCode == 0) {
      println("Script executed successfully.")
    } else {
      println("Script failed with exit code $exitCode.")
    }
  }


  @Test
  @Disabled
  fun okMonoBuild(){
    println("=".repeat(20))
    val cliCommands = listOf(
            "cd ~/okta/okta-core",
            "ok mono build --no-db"
    )
    executeCliCommandsWithLiveOutput(cliCommands,"out/ok_mono_build.log")
  }

  @ParameterizedTest
  @ValueSource(strings = ["2024.1"])
  fun reloadMavenProject(version: String) {
    context = MonolithSetup.setupTestContext(version)
    val reloadMaven = CommandChain()
            .startProfile("mavenReload")
            .waitForSmartMode()
            .importMavenProject()
            .stopProfile()
            .exitApp()
    val result = context.runIDE(commands = reloadMaven, launchName = "mavenReload")
    val firstImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
            .filterByEventId("import_project.finished").sortedBy {
              it.time
            }.first().getDataFromEvent<Long>(EventFields.DurationMs.name)
    val lastImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
            .filterByEventId("import_project.finished").sortedBy {
              it.time
            }.last().getDataFromEvent<Long>(EventFields.DurationMs.name)

    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/firstImport", firstImport)), "firstImport")
    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/lastImport", lastImport)), "lastImport")
  }

  @ParameterizedTest
  @ValueSource(strings = ["2024.1"])
  fun ideCommands(version: String){
    context = MonolithSetup.setupTestContext(version)
    val configurationName = "Start monolith"
    val okMonoBuild = CommandChain()
            .startProfile("okMonoBuild")
            .waitForSmartMode()
            .importMavenProject()
            .runConfiguration(configurationName)
            .stopProfile()
            .exitApp()
    val result = context.runIDE(commands = okMonoBuild, launchName = "okMonoBuild")
    val firstImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
            .filterByEventId("import_project.finished").sortedBy {
              it.time
            }.first().getDataFromEvent<Long>(EventFields.DurationMs.name)
    val lastImport = StatisticsEventsHarvester(context).getStatisticEventsByGroup("project.import")
            .filterByEventId("import_project.finished").sortedBy {
              it.time
            }.last().getDataFromEvent<Long>(EventFields.DurationMs.name)


    val indexingMetrics = extractIndexingMetrics(result).getListOfIndexingMetrics()
    writeMetricsToCSV(result, indexingMetrics, "index")
    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/firstImport", firstImport)), "firstImport")
    writeMetricsToCSV(result, listOf(Metric.newDuration("maven.import/lastImport", lastImport)), "lastImport")
    var metric = -1L
    result.runContext.logsDir.forEachDirectoryEntry {
      if(it.isRegularFile()){
        it.forEachLine {
          if(it.contains("processTerminated in: $configurationName:")) {
            metric = it.split(":").last().trim().toLong()
          }
        }
      }
    }
    writeMetricsToCSV(result, listOf(Metric.newDuration("runConfiguration", metric)),"runConfig")
  }


  @ParameterizedTest
  @ValueSource(strings = ["2024.1"])
  fun runBuild(version: String) {
    context = MonolithSetup.setupTestContext(version)
    val startRunConfiguration = CommandChain()
      .startProfile("build")
      .build()
      .stopProfile()
      .exitApp()
    val result = context.runIDE(commands = startRunConfiguration, launchName = "build")
    val metrics = getMetricsFromSpanAndChildren(
      (result.runContext.logsDir / "opentelemetry.json"),
      SpanFilter.nameEquals("build_compilation_duration")
    )
    writeMetricsToCSV(result, metrics)
  }

}