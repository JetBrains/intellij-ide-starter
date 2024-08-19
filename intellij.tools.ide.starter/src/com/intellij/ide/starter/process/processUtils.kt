package com.intellij.ide.starter.process

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.catchAll
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun getProcessList(): List<ProcessMetaInfo> {
  return oshi.SystemInfo().operatingSystem.processes.map {
    ProcessMetaInfo(it.processID, it.commandLine)
  }
}

/**
 * CI may not kill processes started during the build (for TeamCity: TW-69045).
 * They stay alive and consume resources after tests.
 * This lead to OOM and other errors during tests, for example,
 * IDEA-256265: shared-indexes tests on Linux suspiciously fail with 137 (killed by OOM)
 */
fun killOutdatedProcesses(commandsToSearch: Iterable<String> = listOf("/ide-tests/", "\\ide-tests\\")) {
  val processes = oshi.SystemInfo().operatingSystem.processes
  var killProcess: (Int) -> Unit = {}

  if (SystemInfo.isWindows) catchAll {
    killProcess = { killProcessOnWindows(it) }
  }
  else if (SystemInfo.isLinux) catchAll {
    killProcess = { killProcessOnUnix(it) }
  }
  else catchAll {
    killProcess = { killProcessOnUnix(it) }
  }

  val processIdsToKill = processes.filter { process ->
    commandsToSearch.any { process.commandLine.contains(it) }
  }.map { it.processID }

  logOutput("These processes must be killed before the next test run: [$processIdsToKill]")
  for (pid in processIdsToKill) {
    catchAll { killProcess(pid) }
  }
}

private fun killProcessOnWindows(pid: Int) {
  check(SystemInfo.isWindows)
  logOutput("Killing process $pid")

  ProcessExecutor(
    "kill-process-$pid",
    GlobalPaths.instance.testsDirectory,
    timeout = 1.minutes,
    args = listOf("taskkill", "/pid", pid.toString(), "/f"), //taskkill /pid 23756 /f
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-err]")
  ).start()
}

private fun killProcessOnUnix(pid: Int) {
  check(SystemInfo.isUnix)
  logOutput("Killing process $pid")

  ProcessExecutor(
    "kill-process-$pid",
    GlobalPaths.instance.testsDirectory,
    timeout = 1.minutes,
    args = listOf("kill", "-9", pid.toString()),
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-err]")
  ).start()
}

suspend fun getJavaProcessIdWithRetry(javaHome: Path, workDir: Path, originalProcessId: Long, originalProcess: Process): Long {
  return requireNotNull(
    withRetry(retries = 100, delay = 3.seconds, messageOnFailure = "Couldn't find appropriate java process id for pid $originalProcessId", printFailuresMode = PrintFailuresMode.ONLY_LAST_FAILURE) {
      getJavaProcessId(javaHome, workDir, originalProcessId, originalProcess)
    }
  ) { "Java process id must not be null" }
}

/**
 * On Linux we run IDE using `xvfb-run` tool wrapper, so we need to guess the real PID.
 * Thus, we must guess the original java process ID for capturing the thread dumps.
 * In case of Dev Server, under xvfb-run the whole build process is happening so the waiting time can be long.
 */
private fun getJavaProcessId(javaHome: Path, workDir: Path, originalProcessId: Long, originalProcess: Process): Long {
  if (!SystemInfo.isLinux) {
    return originalProcessId
  }
  logOutput("Guessing java process ID on Linux (pid of the java process wrapper - $originalProcessId)")

  val stdout = ExecOutputRedirect.ToString()
  val stderr = ExecOutputRedirect.ToString()
  ProcessExecutor(
    "jcmd-run",
    workDir,
    timeout = 1.minutes,
    args = listOf(javaHome.resolve("bin/jcmd").toAbsolutePath().toString()),
    stdoutRedirect = stdout,
    stderrRedirect = stderr
  ).start()

  val mergedOutput = stdout.read() + "\n" + stderr.read()
  val candidates = arrayListOf<Long>()
  val candidatesFromProcessHandle = arrayListOf<Long>()
  logOutput("List all java processes IDs:")
  for (line in mergedOutput.lines().map { it.trim() }.filterNot { it.isEmpty() }) {
    logOutput(line)
    /*
    We need to monitor command line parameters otherwise we might get the locally running IDE in local tests.

    An example of a process line:
    1578401 com.intellij.idea.Main /home/sergey.patrikeev/Documents/intellij/out/ide-tests/tests/IU-211.1852/ijx-jdk-empty/verify-shared-index/temp/projects/idea-startup-performance-project-test-03/idea-startup-performance-project-test-03

    An example from TC:
    intellij project:
    81413 com.intellij.idea.Main /opt/teamcity-agent/work/71b862de01f59e23

    another project:
    84318 com.intellij.idea.Main /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests5985285665047908961/ide-tests/tests/IU-installer-from-file/spring_boot/indexing_oldProjectModel/projects/projects/spring-boot-master/spring-boot-master

    An example from TC TestsDynamicBundledPluginsStableLinux
    1879942 com.intellij.idea.Main /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests4436006118811351792/ide-tests/cache/projects/unpacked/javaproject_1.0.0/java-design-patterns-master

    Example from TC
    39848 com.intellij.idea.Main /mnt/agent/work/71b862de01f59e23/../intellij_copy_8157673bdd3048a6990b1214d6e9780b26b348e6
    */
    val pid = line.substringBefore(" ", "").toLongOrNull() ?: continue
    if (line.contains("com.intellij.idea.Main") && (line.contains("/ide-tests/tests/") || line.contains(
        "/ide-tests/cache/") || line.contains("/opt/teamcity-agent/work/") || line.contains("/mnt/agent/work/"))) {
      candidates.add(pid)
    }
  }

  val originalCommand = originalProcess.info().command()
  if (originalCommand.isPresent && originalCommand.get().contains("java")) {
    logOutput("The test was run without wrapper, add original pid")
    candidatesFromProcessHandle.add(originalProcess.pid())
  }

  originalProcess.toHandle().descendants().forEach { processHandle ->
    val command = processHandle.info().command()
    if (command.isPresent && command.get().contains("java")) {
      logOutput("Candidate from ProcessHandle process: ${processHandle.pid()}")
      logOutput("command: ${processHandle.info().command()}")
      candidatesFromProcessHandle.add(processHandle.pid())
    }
  }

  if (candidates.isEmpty() && candidatesFromProcessHandle.isNotEmpty()) {
    logOutput("Candidates from jcmd are missing, will be used first one from ProcessHandle instead: " + candidatesFromProcessHandle.first())
    candidates.add(candidatesFromProcessHandle.first())
  }

  if (candidates.isNotEmpty()) {
    logOutput("Found the following java process ID candidates: " + candidates.joinToString())
    if (originalProcessId in candidates) {
      return originalProcessId
    }
    return candidates.first()
  }
  else {
    throw Exception("There are no suitable candidates for the process")
  }
}

fun collectJavaThreadDump(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  dumpFile: Path,
) {
  val ext = if (SystemInfo.isWindows) ".exe" else ""
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
      stderrRedirect = ExecOutputRedirect.ToStdOut("[jstack-err]")
    ).start()
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
  val ext = if (SystemInfo.isWindows) ".exe" else ""
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

private fun destroyProcessById(processId: Long) {
  ProcessHandle.of(processId)
    .ifPresent {
      logOutput("Destroy process by pid '${it.pid()}'")
      it.destroy()
      catchAll {
        // Usually daemons wait 2 requests for 10 seconds after ide shutdown
        logOutput("Start waiting on exit for process '$processId'")
        it.onExit().get(2, TimeUnit.MINUTES)
        logOutput("Finish waiting on exit for process '$processId'")
      }
      it.destroyForcibly()
    }
}

fun getProcessesIdByProcessName(processName: String): Set<Long> {
  return getAllJavaProcesses().filter {
    it.contains(processName)
  }.map {
    it.split(" ").first().toLong()
  }.toSet()
}

fun destroyProcessIfExists(processName: String) {
  logOutput("Killing '$processName' process ...")
  getProcessesIdByProcessName(processName).forEach {
    logOutput("Killing '$it' process")
    // get up-to date process list on every iteration
    destroyProcessById(it)
  }


  logOutput("Process '$processName' should be killed")
}