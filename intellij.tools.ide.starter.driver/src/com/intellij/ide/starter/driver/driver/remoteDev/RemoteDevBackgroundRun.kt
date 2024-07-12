package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.isProjectOpened
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.mainToolbar
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RemoteDevBackgroundRun(private val clientResult: Deferred<IDEStartResult>,
                             private val hostResult: Deferred<IDEStartResult>,
                             private val hostDriver: Driver,
                             remoteClientDriver: Driver,
                             hostProcess: ProcessHandle? = null
) : BackgroundRun(clientResult, remoteClientDriver, hostProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, block: Driver.() -> R): IDEStartResult {
    try {
      @Suppress("SSBasedInspection")
      runBlocking {
        withTimeout(3.minutes) {
          while (!hostDriver.isConnected) {
            delay(3.seconds)
          }
        }
      }
      if (hostDriver.isProjectOpened()) {
        projectOpenAwaitOnFrontend()
        toolbarIsShownAwaitOnFrontend()
      }
      driver.withContext { block(this) }
    }
    finally {
      driver.closeIdeAndWait(closeIdeTimeout, false)

      @Suppress("SSBasedInspection")
      runBlocking {
        catchAll {
          perClientSupervisorScope.coroutineContext.cancelChildren(CancellationException("Client run execution is finished"))
          perClientSupervisorScope.coroutineContext.job.children.forEach { it.join() }
        }
      }
      hostDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
    }
    @Suppress("SSBasedInspection")
    return runBlocking {
      val clientResult = clientResult.await()
      hostResult.await().apply { this.clientResult = clientResult }
    }
  }

  private fun projectOpenAwaitOnFrontend() {
    waitFor(message = "Project is opened on frontend", timeout = 30.seconds) {
      driver.isProjectOpened()
    }
  }

  private fun toolbarIsShownAwaitOnFrontend() {
    // toolbar won't be shown until the window manager is initialized properly, there is no other way for us to check it has happened
    driver.ui.ideFrame().mainToolbar.waitFound(100.seconds)
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration) {
    hostDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
  }
}