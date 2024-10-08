package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.driver.client.Driver
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.hasVisibleWindow
import com.intellij.driver.sdk.ui.IdeEventQueue
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.mainToolbar
import com.intellij.driver.sdk.ui.requestFocusFromIde
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.ide.starter.coroutine.perClientSupervisorScope
import com.intellij.ide.starter.driver.engine.BackgroundRun
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
  private val frontendStartResult: Deferred<IDEStartResult>,
  private val backendStartResult: Deferred<IDEStartResult>,
  private val backendDriver: Driver,
  remoteFrontendDriver: Driver,
  frontendProcess: ProcessHandle,
) : BackgroundRun(startResult = frontendStartResult, driverWithoutAwaitedConnection = remoteFrontendDriver, process = frontendProcess) {
  override fun <R> useDriverAndCloseIde(closeIdeTimeout: Duration, block: Driver.() -> R): IDEStartResult {
    try {
      waitFor("Backend Driver is connected", 3.minutes) {
        backendDriver.isConnected
      }
      waitAndPrepareForTest()

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
      backendDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
    }
    @Suppress("SSBasedInspection")
    return runBlocking {
      backendStartResult.await()
        .also { it.frontendStartResult = frontendStartResult.await() }
    }
  }

  private fun waitAndPrepareForTest() {
    awaitVisibleFrameFrontend()
    val backendProjects = backendDriver.getOpenProjects()
    if (backendProjects.isNotEmpty()) {
      awaitProjectsAreOpenedOnFrontend(backendProjects.size)
      awaitToolbarIsShownOnFrontend()
    }
    flushEdtAndRequestFocus()
  }


  private fun awaitVisibleFrameFrontend() {
    waitFor("Frontend has a visible IDE frame", timeout = 100.seconds) { driver.hasVisibleWindow() }
  }

  private fun awaitToolbarIsShownOnFrontend() {
    // toolbar won't be shown until the window manager is initialized properly, there is no other way for us to check it has happened
    driver.ui.ideFrame().mainToolbar.waitFound(100.seconds)
  }

  private fun awaitProjectsAreOpenedOnFrontend(projectsNumber: Int) {
    waitFor("Projects are opened on frontend", 30.seconds, getter = { driver.getOpenProjects() }, checker = { it.size == projectsNumber })
  }

  private fun flushEdtAndRequestFocus() {
    // FrontendToolWindowHost should finish it's work to avoid https://youtrack.jetbrains.com/issue/GTW-9730/Some-UI-tests-are-flaky-because-sometimes-actions-are-not-executed
    driver.withContext(OnDispatcher.EDT) {
      driver.utility(IdeEventQueue::class).getInstance().flushQueue()
    }
    driver.requestFocusFromIde(driver.getOpenProjects().singleOrNull())
  }

  override fun closeIdeAndWait(closeIdeTimeout: Duration) {
    backendDriver.closeIdeAndWait(closeIdeTimeout + 30.seconds, false)
  }
}