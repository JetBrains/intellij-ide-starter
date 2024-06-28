package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.isProjectOpened
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.mainToolbar
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RemoteDevBackgroundRun(private val clientResult: Deferred<IDEStartResult>,
                             private val hostResult: Deferred<IDEStartResult>,
                             private val hostDriver: Driver,
                             private val remoteClientDriver: Driver,
                             hostProcess: ProcessHandle? = null
) : BackgroundRun(clientResult, remoteClientDriver, hostProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, block: Driver.() -> R): IDEStartResult {
    try {
      if (hostDriver.isProjectOpened()) {
        projectOpenAwaitOnFrontend()
        toolbarIsShownAwaitOnFrontend()
      }
      remoteClientDriver.withContext { block(this) }
    }
    finally {
      remoteClientDriver.closeIdeAndWait(closeIdeTimeout, false)
      hostDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
    }
    val clientResult = runBlocking { return@runBlocking clientResult.await() }
    catchAll {
      perClientSupervisorScope.coroutineContext.cancelChildren(CancellationException("Client run `${clientResult.runContext.contextName}` execution is finished"))
    }
    return runBlocking { hostResult.await().apply { this.clientResult = clientResult } }
  }

  private fun projectOpenAwaitOnFrontend() {
    waitFor(message = "Project is opened on frontend", timeout = 30.seconds) {
      remoteClientDriver.isProjectOpened()
    }
  }

  private fun toolbarIsShownAwaitOnFrontend() {
    waitFor(message = "The toolbar is shown on frontend", timeout = 100.seconds) {
      // toolbar won't be shown until the window manager is initialized properly, there is no other way for us to check it has happened
      runCatching { remoteClientDriver.ui.ideFrame { mainToolbar } }.isSuccess
    }
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration) {
    hostDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
  }
}