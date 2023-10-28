package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import com.intellij.ide.starter.bus.StarterListener
import io.kotest.assertions.timing.eventually
import io.kotest.assertions.withClue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EventsFilteringTest {
  private var isEventHappened: AtomicBoolean = AtomicBoolean(false)

  private fun checkIsEventFired(shouldEventBeFired: Boolean, isEventFiredGetter: () -> Boolean) {
    val shouldNotMessage = if (!shouldEventBeFired) "NOT" else ""

    runBlocking {
      eventually(duration = 2.seconds, poll = 200.milliseconds) {
        withClue("Event should $shouldNotMessage be fired in 2 sec") {
          isEventFiredGetter() == shouldEventBeFired
        }
      }
    }
  }

  @BeforeEach
  fun beforeEach() {
    isEventHappened.set(false)
  }

  @AfterEach
  fun afterEach() {
    StarterListener.unsubscribe()
  }

  @RepeatedTest(value = 200)
  fun `filtering events by type is working`() {
    StarterListener.subscribe { _: Signal ->
      isEventHappened.set(true)
    }

    StarterBus.post(2)
    checkIsEventFired(false) { isEventHappened.get() }

    StarterBus.post(Signal())
    checkIsEventFired(true) { isEventHappened.get() }
  }
}