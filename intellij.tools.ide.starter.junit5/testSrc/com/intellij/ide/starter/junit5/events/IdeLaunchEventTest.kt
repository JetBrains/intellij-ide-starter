package com.intellij.ide.starter.junit5.events

import com.intellij.tools.ide.starter.bus.EventState
import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.ide.starter.junit5.config.KillOutdatedProcesses
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.IdeLaunchEvent
import com.intellij.ide.starter.runner.Starter
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.exitApp
import examples.data.TestCases
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import io.kotest.inspectors.shouldForAtLeastOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(KillOutdatedProcesses::class)
class IdeLaunchEventTest {

  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  @RepeatedTest(value = 5)
  fun `events for ide launch should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<IdeLaunchEvent>()
    StarterBus.subscribe(this) { event: IdeLaunchEvent -> firedEvents.add(event) }

    val context = Starter.newContext(testInfo.hyphenateWithClass(), TestCases.IU.withProject(NoProject).useRelease())

    context.runIDE(
      commands = CommandChain().exitApp(),
      runTimeout = 5.seconds,
      expectedKill = true
    )

    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        withClue("There should be 3 events fired. Events: ${firedEvents.map { it.state }}") {
          firedEvents.shouldHaveSize(4)
        }
      }
    }

    assertSoftly {
      withClue("During IDE run 3 events should be fired: before IDE start and after IDE finished. " +
               "Events: ${firedEvents.map { it.state }}") {
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.BEFORE) }
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.IN_TIME) }
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.BEFORE_KILL) }
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.AFTER) }
      }
    }
  }
}