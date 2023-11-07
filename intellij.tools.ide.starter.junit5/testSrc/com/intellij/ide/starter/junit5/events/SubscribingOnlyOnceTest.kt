package com.intellij.ide.starter.junit5.events

import com.intellij.ide.starter.bus.Signal
import com.intellij.ide.starter.bus.StarterBus
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger


class SubscribingOnlyOnceTest {
  @AfterEach
  fun afterEach() {
    StarterBus.LISTENER.unsubscribe()
  }

  @Test
  fun `multiple subscription should not work if subscribed only once`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()
    val secondProcessedTimes = AtomicInteger()

    StarterBus
      .subscribeOnlyOnce(this) { _: Signal ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribeOnlyOnce(this) { _: Signal ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribeOnlyOnce(obj) { _: Signal ->
        secondProcessedTimes.incrementAndGet()
      }
      .subscribeOnlyOnce(obj) { _: Signal ->
        secondProcessedTimes.incrementAndGet()
      }

    StarterBus.postAsync(Signal())

    withClue("Multiple subscription should not work if subscribed only once") {
      eventProcessedTimes.get().shouldBe(1)
      secondProcessedTimes.get().shouldBe(1)
    }

    eventProcessedTimes.set(0)
    secondProcessedTimes.set(0)

    StarterBus.postAndWaitProcessing(Signal())

    withClue("Multiple subscription should not work if subscribed only once") {
      eventProcessedTimes.get().shouldBe(1)
      secondProcessedTimes.get().shouldBe(1)
    }
  }

  @Test
  fun `multiple subscription should work by default`() {
    val obj = Any()

    val eventProcessedTimes = AtomicInteger()
    val secondProcessedTimes = AtomicInteger()

    StarterBus
      .subscribe(this) { _: Signal ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(this) { _: Signal ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(this) { _: Signal ->
        eventProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: Signal ->
        secondProcessedTimes.incrementAndGet()
      }
      .subscribe(obj) { _: Signal ->
        secondProcessedTimes.incrementAndGet()
      }

    StarterBus.postAsync(Signal())

    withClue("Multiple subscription should work by default") {
      eventProcessedTimes.get().shouldBe(3)
      secondProcessedTimes.get().shouldBe(2)
    }

    eventProcessedTimes.set(0)
    secondProcessedTimes.set(0)

    StarterBus.postAndWaitProcessing(Signal())

    withClue("Multiple subscription should work by default") {
      eventProcessedTimes.get().shouldBe(3)
      secondProcessedTimes.get().shouldBe(2)
    }
  }
}