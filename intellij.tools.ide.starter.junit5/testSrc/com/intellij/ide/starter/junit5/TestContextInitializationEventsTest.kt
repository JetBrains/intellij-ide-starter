package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.EventState
import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.runner.TestContextInitializedEvent
import com.intellij.ide.starter.utils.hyphenateTestName
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

@ExtendWith(JUnit5StarterAssistant::class)
class TestContextInitializationEventsTest {
  @AfterEach
  fun afterEach() {
    StarterListener.unsubscribe()
  }

  @RepeatedTest(value = 200)
  fun `events for test runner init should be fired`(testInfo: TestInfo) {
    val firedEvents = mutableListOf<TestContextInitializedEvent>()
    StarterListener.subscribe { event: TestContextInitializedEvent -> firedEvents.add(event) }

    val testName = testInfo.displayName.hyphenateTestName()

    Starter.newContext(testName = testName, testCase = TestCases.IU.withProject(NoProject).useRelease())

    runBlocking(Dispatchers.IO) {
      eventually(duration = 2.seconds, poll = 100.milliseconds) {
        withClue("There should be 1 events fired. Events: ${firedEvents.map { it.state }}") {
          firedEvents.shouldHaveSize(1)
        }
      }
    }

    assertSoftly {
      withClue("Event should be fired at the end of test context initialization: Events: ${firedEvents.map { it.state }}") {
        firedEvents.shouldForAtLeastOne { it.state.shouldBe(EventState.AFTER) }
      }
    }
  }
}