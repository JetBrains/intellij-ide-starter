package com.intellij.ide.starter.process

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ide.LinuxIdeDistribution
import com.intellij.ide.starter.path.IDE_TESTS_SUBSTRING
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.NoRetryException
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import com.intellij.util.system.OS
import kotlinx.coroutines.runBlocking
import oshi.SystemInfo
import oshi.software.os.OperatingSystem
import oshi.software.os.OperatingSystem.ProcessFiltering.VALID_PROCESS
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun getProcessList(processesToSearch: Set<String> = emptySet()): List<ProcessInfo> {
  val allProcesses = SystemInfo().operatingSystem.getProcesses(VALID_PROCESS, null, 0)
  return if (processesToSearch.isEmpty()) {
    allProcesses.map { ProcessInfo.create(it) }
  }
  else {
    allProcesses
      .filter { process -> processesToSearch.any { it in process.commandLine } }
      .map { ProcessInfo.create(it) }
  }
}

/**
 * CI may not kill processes started during the build (for TeamCity: TW-69045).
 * They stay alive and consume resources after tests.
 * This lead to OOM and other errors during tests, for example,
 * IDEA-256265: shared-indexes tests on Linux suspiciously fail with 137 (killed by OOM)
 */
fun killOutdatedProcesses(commandsToSearch: Iterable<String> = setOf("/$IDE_TESTS_SUBSTRING/", "\\$IDE_TESTS_SUBSTRING\\"), reportErrors: Boolean = false) {
  val processInfosToKill = getProcessList(commandsToSearch.toSet())
  if (processInfosToKill.isNotEmpty()) {
    if (reportErrors) {
      CIServer.instance.reportTestFailure("Unexpected running processes were detected ${processInfosToKill.joinToString(", ") { it.shortCommand }}",
                                          "Please try to stop them appropriately in tests, as you might be missing some diagnostics.\n" +
                                          "Processes were collected based on command line, containing '${commandsToSearch.joinToString(", ")}'.\n" +
                                          processInfosToKill.joinToString("\n") { it.description }, details = "")
    }
    else {
      logOutput("These outdated processes must be killed: [${processInfosToKill.joinToString(", ")}]")
    }
    ProcessKiller.killPids(processInfosToKill.map { it.pid }.toSet())
  }
}

private val devBuildArgumentsSet = setOf(
  "com.intellij.idea.Main",
  "com.intellij.platform.runtime.loader.IntellijLoader" // thin client
)

private fun isIde(command: String, commandLine: String, arguments: List<String>): Boolean =
  !commandLine.contains(LinuxIdeDistribution.xvfbRunTool) &&
  (
    /** for installer runs
     * Example:
     *  Name: idea
     *  Arguments: [/mnt/agent/temp/buildTmp/testb0bv1hja1z5rg/ide-tests/cache/builds/IU-installer-from-file/idea-IU-261.1243/bin/idea, serverMode,
     *    /mnt/agent/temp/buildTmp/testb0bv1hja1z5rg/ide-tests/cache/projects/unpacked/TestScopesProj]
     **/
    arguments.firstOrNull().contains(IDE_TESTS_SUBSTRING) == true ||
    /**  for dev build runs
     * Example:
     *  Name: java
     *  Arguments: [/mnt/agent/system/.persistent_cache/5tq0kti2dt-jbrsdk_jcef-21.0.8-linux-x64-b1173.3.tar.gz.2qppum.d/bin/java,
     *    @/mnt/agent/temp/buildTmp/testapcvq8gxezoyw/ide-tests/tmp/perf-vmOps-1760988642136-, ... com.intellij.idea.Main, /mnt/agent/temp/buildTmp/test8b25i2v1x4unr/ide-tests/cache/projects/unpacked/ui-tests-data/projects/catch_test_project_sample]
     **/
    (command == "java" && devBuildArgumentsSet.any { commandLine.contains(it) })
  )


suspend fun getIdeProcessIdWithRetry(parentProcess: Process): Long {
  if (OS.CURRENT != OS.Linux) {
    return parentProcess.pid()
  }

  val parentProcessInfo = ProcessInfo.create(SystemInfo().operatingSystem.getProcess(parentProcess.pid().toInt()))
  logOutput("Guessing IDE process ID on Linux: \n${parentProcessInfo.description}")
  val attemptsResult = withRetry(retries = 100, delay = 3.seconds, messageOnFailure = "Couldn't find appropriate java process id for pid ${parentProcess.pid()}", printFailuresMode = PrintFailuresMode.ALL_FAILURES) {
    getIdeProcessId(parentProcessInfo)
  }
  return requireNotNull(attemptsResult) { "Java process id must not be null" }
}


/**
 * On Linux we run IDE using `xvfb-run` tool wrapper, so we need to guess the real PID.
 * Thus, we must guess the IDE process ID for capturing the thread dumps.
 * In case of Dev Server, under xvfb-run the whole build process is happening so the waiting time can be long.
 */
private fun getIdeProcessId(parentProcessInfo: ProcessInfo): Long {
  if (OS.CURRENT != OS.Linux) {
    return parentProcessInfo.pid
  }

  if (parentProcessInfo.processHandle?.isAlive != true) {
    throw NoRetryException("Couldn't guess IDE process: parent process is not alive", null)
  }
  logOutput("Guessing IDE process ID on Linux (pid of the IDE process wrapper ${parentProcessInfo.pid})")

  if (isIde(parentProcessInfo.command, parentProcessInfo.commandLine, parentProcessInfo.arguments)) {
    logOutput("Parent process is an IDE process itself (was launched without wrapper)")
    return parentProcessInfo.pid
  }

  val suitableChildren = SystemInfo().operatingSystem.getChildProcesses(
    /* parentPid = */ parentProcessInfo.pid.toInt(),
    /* filter = */ { isIde(it.name, it.commandLine, it.arguments) },
    /* sort = */ OperatingSystem.ProcessSorting.UPTIME_DESC,
    /* limit = */ 0
  ).map { ProcessInfo.create(it) }

  if (suitableChildren.isEmpty()) {
    throw Exception("There are no suitable candidates for IDE process\n" +
                    "All children: \n" +
                    SystemInfo().operatingSystem.getChildProcesses(
                      /* parentPid = */ parentProcessInfo.pid.toInt(),
                      /* filter = */ null,
                      /* sort = */ OperatingSystem.ProcessSorting.UPTIME_DESC,
                      /* limit = */ 0
                    ).joinToString("\n") { ProcessInfo.create(it).description })
  }

  if (suitableChildren.size > 1) {
    logOutput("Found more than one IDE process candidates: " + suitableChildren.joinToString("\n") { it.description } +
              "Returning oldest suitable IDE process: ${suitableChildren.first()}")
    return suitableChildren.first().pid
  }
  else {
    logOutput("Returning single suitable IDE process: ${suitableChildren.single().description}")
    return suitableChildren.single().pid
  }
}

fun collectJavaThreadDump(
  javaHome: Path,
  workDir: Path?,
  javaProcessId: Long,
  dumpFile: Path,
) {
  runBlocking {
    collectJavaThreadDumpSuspendable(javaHome, workDir, javaProcessId, dumpFile)
  }
}


/**
 * DON'T ADD ANY LOGGING OTHERWISE IF STDOUT IS BLOCKED THERE WILL BE NO DUMPS
 */
suspend fun collectJavaThreadDumpSuspendable(
  javaHome: Path,
  workDir: Path?,
  javaProcessId: Long,
  dumpFile: Path,
) {
  val ext = if (OS.CURRENT == OS.Windows) ".exe" else ""
  val jstackPath = listOf(
    javaHome.resolve("bin/jstack$ext"),
    javaHome.parent.resolve("bin/jstack$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jstack under $javaHome")

  val command = listOf(jstackPath.toAbsolutePath().toString(), "-l", javaProcessId.toString())

  try {
    ProcessExecutor(
      "jstack",
      workDir,
      timeout = 1.minutes,
      args = command,
      stdoutRedirect = ExecOutputRedirect.ToFile(dumpFile.toFile()),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[jstack-err]"),
      silent = true
    ).startCancellable()
  }
  catch (ise: IllegalStateException) {
    val message = ise.message ?: ""
    if (message.startsWith("External process `jstack` failed with code ")
        || message.startsWith("Shutdown in progress")) {
      logOutput("... " + ise.message)
    }
    else {
      throw ise
    }
  }
}

fun collectMemoryDump(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  dumpFile: Path,
) {
  logOutput("Collecting memory dump to $dumpFile")
  val command = listOf("GC.heap_dump", "-gz=4", dumpFile.toString())
  jcmd(javaHome, workDir, javaProcessId, command)
}

fun jcmd(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  command: List<String>,
) {
  val pathToJcmd = "bin/jcmd"
  val ext = if (OS.CURRENT == OS.Windows) ".exe" else ""
  val jcmdPath = listOf(
    javaHome.resolve("$pathToJcmd$ext"),
    javaHome.parent.resolve("$pathToJcmd$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jcmd under $javaHome")

  val jcmdCommand = listOf(jcmdPath.toAbsolutePath().toString(), javaProcessId.toString()) + command
  ProcessExecutor(
    "jcmd",
    workDir,
    timeout = 5.minutes,
    args = jcmdCommand,
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[jcmd-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[jcmd-err]")
  ).start()

}

fun getAllJavaProcesses(): List<String> {
  val stdout = ExecOutputRedirect.ToString()
  ProcessExecutor(
    "get jps process",
    workDir = null,
    timeout = 30.seconds,
    args = listOf("jps", "-l"),
    stdoutRedirect = stdout
  ).start(printEnvVariables = CIServer.instance.isBuildRunningOnCI)

  logOutput("List of java processes: \n" + stdout.read())
  return stdout.read().split("\n")
}


fun getProcessesIdByProcessName(processName: String): Set<Long> {
  return getAllJavaProcesses().filter {
    it.contains(processName)
  }.map {
    it.split(" ").first().toLong()
  }.toSet()
}

fun destroyProcessIfExists(processName: String) {
  val pids = getProcessesIdByProcessName(processName)
  if (pids.isNotEmpty()) {
    logOutput("Killing '$processName' process ...")
    ProcessKiller.killPids(getProcessesIdByProcessName(processName))
    logOutput("Process '$processName' should be killed")
  }
  else {
    logOutput("No '$processName' processes found to kill")
  }
}