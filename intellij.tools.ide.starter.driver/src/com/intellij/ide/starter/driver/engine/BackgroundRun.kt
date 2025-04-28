package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.process.exec.ProcessExecutor.Companion.killProcessGracefully
import com.intellij.ide.starter.utils.catchAll
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class BackgroundRun(val startResult: Deferred<IDEStartResult>, driverWithoutAwaitedConnection: Driver, val process: IDEHandle) {

  val driver: Driver by lazy {
    if (!driverWithoutAwaitedConnection.isConnected) {
      runCatching {
        waitFor("Driver is connected", 3.minutes) {
          if (!process.isAlive) {
            val message = "Couldn't wait for the driver to connect, it has already exited pid[${process.id}]"
            logError(message)
            throw IllegalStateException(message)
          }
          driverWithoutAwaitedConnection.isConnected
        }
      }.onFailure { t ->
        driverWithoutAwaitedConnection.closeIdeAndWait(1.minutes)
        throw t
      }
    }
    driverWithoutAwaitedConnection
  }

  open fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration = 1.minutes, block: Driver.() -> R): IDEStartResult {
    try {
      driver.withContext { block(this) }
    }
    finally {
      driver.closeIdeAndWait(closeIdeTimeout)
      @Suppress("SSBasedInspection")
      runBlocking {
        startResult.await()
      }
    }
    @Suppress("SSBasedInspection")
    return runBlocking { return@runBlocking startResult.await() }
  }

  open fun closeIdeAndWait(closeIdeTimeout: Duration = 1.minutes, takeScreenshot: Boolean = true) {
    driver.closeIdeAndWait(closeIdeTimeout, takeScreenshot)
  }

  protected fun Driver.closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean = true) {
    try {
      if (isConnected) {
        if (takeScreenshot) {
          takeScreenshot("beforeIdeClosed")
        }
        exitApplication()
        waitFor("Driver is not connected", closeIdeTimeout, 3.seconds) { !isConnected }
      }
    }
    catch (t: Throwable) {
      logError("Error on exit application via Driver", t)
      forceKill()
    }
    finally {
      try {
        if (isConnected) close()
        waitFor("Process is closed", closeIdeTimeout, 3.seconds) { !process.isAlive }
      }
      catch (e: Throwable) {
        logError("Error waiting IDE is closed: ${e.message}: ${e.stackTraceToString()}", e)
        forceKill()
        throw IllegalStateException("Process didn't die after waiting for Driver to close IDE")
      }
    }
  }

  private fun forceKill() {
    logOutput("Performing force kill")
    process.kill()
  }
}