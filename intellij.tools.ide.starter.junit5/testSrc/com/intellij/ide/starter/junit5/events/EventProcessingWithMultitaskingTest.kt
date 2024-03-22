package com.intellij.ide.starter.junit5.events

import com.intellij.tools.ide.starter.bus.Signal
import com.intellij.tools.ide.starter.bus.StarterBus
import com.intellij.tools.ide.util.common.logOutput
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


class EventProcessingWithMultitaskingTest {
  class YourEvent : Signal()
  class YourEventsReceiver

  private val maxTasksNumber = 10
  private val counter = AtomicInteger(0)

  @BeforeEach
  fun beforeEach() {
    counter.set(0)
  }

  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  /** If event is null - new event  */
  private fun runEventProcessingTest(event: YourEvent? = null, timeout: Duration) = runBlocking {
    val jobs = List(maxTasksNumber) {
      launch(Dispatchers.Default) {
        withClue("All subscribers must finish their work before timeout") {
          val result: Boolean

          val eventToFire = event ?: YourEvent()
          val duration = measureTime {
            result = StarterBus.postAndWaitProcessing(eventToFire, true, 3.seconds)
          }

          logOutput("Processing event ${eventToFire.hashCode()} took $duration")
          result.shouldBeTrue()
        }
      }
    }

    withTimeoutOrNull(timeout) { // Timeout longer than the event wait time
      jobs.forEach { it.join() }
    } ?: throw AssertionError("Test timed out in $timeout waiting for all events to be processed")

    counter.get().shouldBe(maxTasksNumber)
  }

  @Test
  fun `awaiting event processing on exactly the same event reference`(): Unit = runBlocking {
    val event = YourEvent()

    StarterBus.subscribe<YourEvent, YourEventsReceiver>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("Handling event ${counter.incrementAndGet()} times")
    }

    // synchronization in FlowBus for the same event instance makes it slow
    runEventProcessingTest(event, timeout = 7.seconds)
  }

  @Test
  fun `awaiting event processing on different event reference`(): Unit = runBlocking {
    StarterBus.subscribe<YourEvent, YourEventsReceiver>(YourEventsReceiver()) {
      delay(500.milliseconds)
      println("Handling event ${counter.incrementAndGet()} times")
    }

    runEventProcessingTest(timeout = 3.seconds)
  }
}