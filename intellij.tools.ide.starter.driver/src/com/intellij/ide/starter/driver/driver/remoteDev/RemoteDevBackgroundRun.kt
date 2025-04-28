package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.hasVisibleWindow
import com.intellij.driver.sdk.ui.IdeEventQueue
import com.intellij.driver.sdk.ui.requestFocusFromIde
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.IDEHandle
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RemoteDevBackgroundRun(
  private val backendRun: BackgroundRun,
  frontendProcess: IDEHandle,
  frontendDriver: Driver,
  private val frontendStartResult: Deferred<IDEStartResult>,
) : BackgroundRun(startResult = frontendStartResult,
                  driverWithoutAwaitedConnection = frontendDriver,
                  process = frontendProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, block: Driver.() -> R): IDEStartResult {
    try {
      waitAndPrepareForTest()

      driver.withContext { block(this) }
    }
    finally {
      try {
        driver.closeIdeAndWait(closeIdeTimeout)
      }
      finally {
        @Suppress("SSBasedInspection")
        runBlocking {
          catchAll {
            perClientSupervisorScope.coroutineContext.cancelChildren(CancellationException("Client run execution is finished"))
            perClientSupervisorScope.coroutineContext.job.children.forEach { it.join() }
          }
        }
        backendRun.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
      }
    }
    @Suppress("SSBasedInspection")
    return runBlocking {
      backendRun.startResult.await()
        .also { it.frontendStartResult = frontendStartResult.await() }
    }
  }

  private fun waitAndPrepareForTest() {
    awaitBackendIsConnected()
    awaitVisibleFrameFrontend()
    driver.awaitLuxInitialized()
    flushEdtAndRequestFocus()
  }

  private fun awaitBackendIsConnected() {
    waitFor("Backend Driver is connected", 3.minutes) { backendRun.driver.isConnected }
  }

  private fun awaitVisibleFrameFrontend() {
    waitFor("Frontend has a visible IDE frame", timeout = 100.seconds) { driver.hasVisibleWindow() }
  }

  private fun flushEdtAndRequestFocus() {
    // FrontendToolWindowHost should finish it's work to avoid https://youtrack.jetbrains.com/issue/GTW-9730/Some-UI-tests-are-flaky-because-sometimes-actions-are-not-executed
    driver.withContext(OnDispatcher.EDT) {
      driver.utility(IdeEventQueue::class).getInstance().flushQueue()
    }
    driver.requestFocusFromIde(driver.getOpenProjects().singleOrNull())
  }

  @Remote("com.jetbrains.thinclient.lux.LuxClientService")
  interface LuxClientService {
    fun getMaybeInstance(): LuxClientService?
  }

  fun Driver.awaitLuxInitialized() {
    waitFor("Lux is initialized", timeout = 30.seconds) { utility(LuxClientService::class).getMaybeInstance() != null }
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration, takeScreenshot: Boolean) {
    backendRun.driver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
  }
}