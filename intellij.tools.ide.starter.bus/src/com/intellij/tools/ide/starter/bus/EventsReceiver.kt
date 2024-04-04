package com.intellij.tools.ide.starter.bus

import com.intellij.tools.ide.starter.bus.events.Event
import com.intellij.tools.ide.util.common.logError
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class EventsReceiver(private val producer: EventsProducer) {
  private val parentJob = Job()
  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    throw throwable
  }

  // Using IO(has more threads) to avoid coroutine's threads lock by producers.
  private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob + exceptionHandler)
  private val subscribers = HashMap<String, Any>()
  private val subscribersLock = ReentrantLock()

  fun <EventType : Event, SubscriberType : Any> subscribeTo(event: Class<EventType>,
                                                            subscriber: SubscriberType,
                                                            callback: suspend (event: EventType) -> Unit) {

    subscribersLock.withLock {
      // To avoid double subscriptions
      val jobHash = subscriber.hashCode().toString() + event.simpleName
      if (subscribers.contains(jobHash) && subscribers[jobHash] == subscriber) return
      subscribers.put(jobHash, subscriber)
    }

    val flow = producer.getOrCreateFlowForEvent(event)
    //Subscription to the bus must occur before execution proceeds further, so CoroutineStart.UNDISPATCHED is used
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      flow
        .filterNotNull()
        .collect { event ->
          launch {
            try {
              callback(event)
            }
            catch (e: Exception) {
              if (e !is CancellationException) {
                logError("Suppressed error: ${e.message}")
                logError(StringWriter().let {
                  e.printStackTrace(PrintWriter(it))
                  it.buffer.toString()
                })
              }
            }
            finally {
              // SharedFlow.emit only waits for the event to be delivered, not for the event to be processed.
              producer.processedEvent(event)
            }
          }
        }
    }
  }


  fun unsubscribeAll() {
    subscribersLock.withLock {
      parentJob.children.forEach { runBlocking { it.cancelAndJoin() } }
      subscribers.clear()
      producer.clear()
    }
  }
}
