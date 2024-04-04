package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class EventsProducer {
  private val flows = HashMap<String, MutableSharedFlow<*>>()
  private val flowsLock = ReentrantReadWriteLock()

  private val eventsLatch = HashMap<Any, CountDownLatch>()
  private val eventsLatchLock = ReentrantReadWriteLock()

  private fun <T : Event> getLatchForEvent(event: T): CountDownLatch? {
    return eventsLatchLock.readLock().withLock {
      eventsLatch[event]
    }
  }

  private fun <T : Event> setLatchForEvent(event: T, latchCount: Int): CountDownLatch {
    return eventsLatchLock.writeLock().withLock {
      val latch = CountDownLatch(latchCount)
      eventsLatch[event] = latch
      latch
    }
  }

  private fun <T : Event> getFlowForEvent(clazz: Class<T>): MutableSharedFlow<T?>? {
    return flowsLock.readLock().withLock {
      flows[clazz.simpleName] as? MutableSharedFlow<T?>
    }
  }

  fun <T : Event> processedEvent(event: T) {
    getLatchForEvent(event)?.countDown()
  }

  fun <T : Event> getOrCreateFlowForEvent(clazz: Class<T>): MutableSharedFlow<T?> {
    return getFlowForEvent(clazz) ?: run {
      flowsLock.writeLock().withLock {
        val newFlow = MutableSharedFlow<T?>()
        flows[clazz.simpleName] = newFlow
        newFlow
      }
    }
  }

  fun <T : Event> postAndWaitProcessing(
    event: T,
    timeout: Duration = 30.seconds,
  ) {
    val eventClass = event.javaClass
    // Synchronization is needed to avoid overwriting the latch while waiting
    synchronized(event) {
      getFlowForEvent(eventClass)?.also {
        val latch = setLatchForEvent(event, it.subscriptionCount.value)
        runBlocking {   it.emit (event) }
        // Emit only waits for the event to be delivered, not for the event to be processed.
        val latchResult = latch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!latchResult) {
          throw TimeoutException(
            "${latch.count} subscribers for event ${eventClass.simpleName} haven't finished their work in $timeout"
          )
        }
      }
    }
  }

  fun clear() {
    flowsLock.writeLock().withLock {
      flows.clear()
    }
    eventsLatchLock.writeLock().withLock {
      eventsLatch.clear()
    }
  }
}