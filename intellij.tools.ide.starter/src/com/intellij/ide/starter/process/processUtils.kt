package com.intellij.ide.starter.process

import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withRetry
import org.kodein.di.direct
import org.kodein.di.instance
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// TODO: consider using https://github.com/oshi/oshi for acquiring process info

/**
 * CI may not kill processes started during the build (for TeamCity: TW-69045).
 * They stay alive and consume resources after tests.
 * This lead to OOM and other errors during tests, for example,
 * IDEA-256265: shared-indexes tests on Linux suspiciously fail with 137 (killed by OOM)
 */
fun killOutdatedProcesses(commandsToSearch: Iterable<String> = listOf("/perf-startup/", "\\perf-startup\\")) {
  val processes = arrayListOf<ProcessMetaInfo>()
  var killProcess: (Int) -> Unit = {}

  if (SystemInfo.isWindows) catchAll {
    processes += dumpListOfProcessesOnWindows()
    killProcess = { killProcessOnWindows(it) }
  }
  else if (SystemInfo.isLinux) catchAll {
    processes += dumpListOfProcessesOnLinux()
    killProcess = { killProcessOnUnix(it) }
  }
  else catchAll {
    processes += dumpListOfProcessesOnMacOS()
    killProcess = { killProcessOnUnix(it) }
  }

  val processIdsToKill = processes.filter { process ->
    commandsToSearch.any { process.command.contains(it) }
  }.map { it.pid }

  logOutput("These processes must be killed before the next test run: [$processIdsToKill]")
  for (pid in processIdsToKill) {
    catchAll { killProcess(pid) }
  }
}

private fun dumpListOfProcessesOnMacOS(): List<MacOsProcessMetaInfo> {
  check(SystemInfo.isMac)
  val stdoutRedirect = ExecOutputRedirect.ToString()
  ProcessExecutor("ps",
                  di.direct.instance<GlobalPaths>().testsDirectory,
                  timeout = 1.minutes,
                  args = listOf("ps", "-ax"),
                  stdoutRedirect = stdoutRedirect
  ).start()

  val processLines = stdoutRedirect.read().lines().drop(1).map { it.trim() }.filterNot { it.isBlank() }
  //PID TTY           TIME CMD
  //  1 ??         0:43.67 /sbin/launchd
  val processes = arrayListOf<MacOsProcessMetaInfo>()
  for (line in processLines) {
    var rest = line
    fun nextString(): String {
      val result = rest.substringBefore(" ").trim()
      rest = rest.substringAfter(" ").dropWhile { it == ' ' }
      return result
    }

    val pid = nextString().toInt()
    nextString() //TTY
    nextString() //TIME
    val command = rest
    processes += MacOsProcessMetaInfo(pid, command)
  }
  return processes
}

private fun dumpListOfProcessesOnLinux(): List<LinuxProcessMetaInfo> {
  check(SystemInfo.isLinux)
  val stdoutRedirect = ExecOutputRedirect.ToString()
  ProcessExecutor("ps",
                  di.direct.instance<GlobalPaths>().testsDirectory,
                  timeout = 1.minutes,
                  args = listOf("ps", "-aux"),
                  stdoutRedirect = stdoutRedirect
  ).start()

  val processLines = stdoutRedirect.read().lines().drop(1).map { it.trim() }.filterNot { it.isBlank() }
  //USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
  //root       823  0.0  0.0 1576524 8128 ?        Ssl  дек01   0:08 /usr/bin/containerd
  val processes = arrayListOf<LinuxProcessMetaInfo>()
  for (line in processLines) {
    var rest = line
    fun nextString(): String {
      val result = rest.substringBefore(" ").trim()
      rest = rest.substringAfter(" ").dropWhile { it == ' ' }
      return result
    }
    nextString() //user
    val pid = nextString().toInt()
    nextString() //cpu
    nextString() //mem
    val vsz = nextString().toInt()
    val rss = nextString().toInt()
    nextString() //tty
    nextString() //stat
    nextString() //start
    nextString() //time
    val command = rest
    processes += LinuxProcessMetaInfo(pid, vsz, rss, command)
  }
  return processes
}

private fun dumpListOfProcessesOnWindows(): List<WindowsProcessMetaInfo> {
  check(SystemInfo.isWindows)
  val stdoutRedirect = ExecOutputRedirect.ToString()
  ProcessExecutor("wmic",
                  di.direct.instance<GlobalPaths>().testsDirectory,
                  timeout = 1.minutes,
                  args = listOf("wmic", "process", "get", "Description,ExecutablePath,ProcessId", "/format:csv"),
                  stdoutRedirect = stdoutRedirect
  ).start()

  val processLines = stdoutRedirect.read().lines().map { it.trim() }.filterNot { it.isBlank() }.drop(1)

  val processes = arrayListOf<WindowsProcessMetaInfo>()

  for (line in processLines) {
    val rest = line.split(",")
    processes += WindowsProcessMetaInfo(rest[3].toInt(), rest[2], rest[1])
  }

  return processes
}

private fun killProcessOnWindows(pid: Int) {
  check(SystemInfo.isWindows)
  logOutput("Killing process $pid")

  ProcessExecutor(
    "kill-process-$pid",
    di.direct.instance<GlobalPaths>().testsDirectory,
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
    di.direct.instance<GlobalPaths>().testsDirectory,
    timeout = 1.minutes,
    args = listOf("kill", "-9", pid.toString()),
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[kill-$pid-err]")
  ).start()
}

fun getJavaProcessIdWithRetry(javaHome: Path, workDir: Path, originalProcessId: Long, originalProcess: Process): Long {
  return requireNotNull(
    withRetry(retries = 2, delay = 5.seconds, messageOnFailure = "Couldn't find appropriate java process id for pid $originalProcessId") {
      getJavaProcessId(javaHome, workDir, originalProcessId, originalProcess)
    }
  ) { "Java process id must not be null" }
}

/**
 * Workaround for IDEA-251643.
 * On Linux we run IDE using `xvfb-run` tool wrapper.
 * Thus we must guess the original java process ID for capturing the thread dumps.
 * TODO: try to use java.lang.ProcessHandle to get the parent process ID.
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
    An example of a process line:

    1578401 com.intellij.idea.Main /home/sergey.patrikeev/Documents/intellij/out/perf-startup/tests/IU-211.1852/ijx-jdk-empty/verify-shared-index/temp/projects/idea-startup-performance-project-test-03/idea-startup-performance-project-test-03

    An example from TC:
    intellij project:
    81413 com.intellij.idea.Main /opt/teamcity-agent/work/71b862de01f59e23

    another project:
    84318 com.intellij.idea.Main /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests5985285665047908961/perf-startup/tests/IU-installer-from-file/spring_boot/indexing_oldProjectModel/projects/projects/spring-boot-master/spring-boot-master

    An example from TC TestsDynamicBundledPluginsStableLinux
    1879942 com.intellij.idea.Main /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests4436006118811351792/perf-startup/cache/projects/unpacked/javaproject_1.0.0/java-design-patterns-master
    */

    //TODO(Monitor case /opt/teamcity-agent/work/)
    val pid = line.substringBefore(" ", "").toLongOrNull() ?: continue
    if (line.contains("com.intellij.idea.Main") && (line.contains("/perf-startup/tests/") || line.contains(
        "/perf-startup/cache/") || line.contains("/opt/teamcity-agent/work/"))) {
      candidates.add(pid)
    }
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
  dumpFile: Path
) {
  val ext = if (SystemInfo.isWindows) ".exe" else ""
  val jstackPath = listOf(
    javaHome.resolve("bin/jstack$ext"),
    javaHome.parent.resolve("bin/jstack$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jstack under $javaHome")

  val command = listOf(jstackPath.toAbsolutePath().toString(), "-l", javaProcessId.toString())

  ProcessExecutor(
    "jstack",
    workDir,
    timeout = 1.minutes,
    args = command,
    stdoutRedirect = ExecOutputRedirect.ToFile(dumpFile.toFile()),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[jstack-err]")
  ).start()
}

fun collectMemoryDump(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  dumpFile: Path,
) {
  val pathToJcmd = "bin/jcmd"
  val ext = if (SystemInfo.isWindows) ".exe" else ""
  val jcmdPath = listOf(
    javaHome.resolve("$pathToJcmd$ext"),
    javaHome.parent.resolve("$pathToJcmd$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jcmd under $javaHome")

  val command = listOf(jcmdPath.toAbsolutePath().toString(), javaProcessId.toString(), "GC.heap_dump", "-gz=4", dumpFile.toString())

  ProcessExecutor(
    "jcmd",
    workDir,
    timeout = 5.minutes,
    args = command,
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[jcmd-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[jcmd-err]")
  ).start()
}

fun destroyGradleDaemonProcessIfExists() {
  val stdout = ExecOutputRedirect.ToString()
  ProcessExecutor(
    "get jps process",
    workDir = null,
    timeout = 30.seconds,
    args = listOf("jps", "-l"),
    stdoutRedirect = stdout
  ).start()

  logOutput("List of java processes: " + stdout.read())

  if (stdout.read().contains("GradleDaemon")) {
    val readLines = stdout.read().split('\n')
    readLines.forEach {
      if (it.contains("GradleDaemon")) {
        logOutput("Killing GradleDaemon process")
        val processId = it.split(" ").first().toLong()

        // get up-to date process list on every iteration
        ProcessHandle.allProcesses()
          .filter { ph -> ph.isAlive && ph.pid() == processId }
          .forEach { ph -> ph.destroy() }
      }
    }
  }
}
