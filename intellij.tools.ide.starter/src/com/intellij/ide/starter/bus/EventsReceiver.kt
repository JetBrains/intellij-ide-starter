package com.intellij.ide.starter.bus

import com.intellij.ide.starter.utils.catchAll
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/**
 * @author https://github.com/Kosert/FlowBus
 * @license Apache 2.0 https://github.com/Kosert/FlowBus/blob/master/LICENSE
 * Class for receiving events posted to [FlowBus]
 *
 * @param bus [FlowBus] instance to subscribe to. If not set, [StarterBus] will be used
 */
open class EventsReceiver @JvmOverloads constructor(private val bus: FlowBus = StarterBus) {
  private val jobs = mutableMapOf<Class<*>, List<Job>>()

  private var returnDispatcher: CoroutineDispatcher = Dispatchers.IO

  /**
   * Subscribe to events that are type of [clazz] with the given [callback] function.
   * The [callback] can be called immediately if event of type [clazz] is present in the flow.
   *
   * @param clazz Type of event to subscribe to
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback The callback function
   * @return This instance of [EventsReceiver] for chaining
   */
  @JvmOverloads
  fun <T : Any> subscribeTo(clazz: Class<T>, skipRetained: Boolean = false, callback: suspend (event: T) -> Unit): EventsReceiver {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      throw throwable
    }

    val job = CoroutineScope(Job() + Dispatchers.IO + exceptionHandler).launch {
      bus.forEvent(clazz)
        .drop(if (skipRetained) 1 else 0)
        .filterNotNull()
        .collect {
          catchAll {
            withContext(returnDispatcher) { callback(it) }
          }
        }
    }

    jobs.putIfAbsent(clazz, listOf(job))?.let { jobs[clazz] = it + job }
    return this
  }

  /**
   * Simplified [subscribeTo] for Kotlin.
   * Type of event is automatically inferred from [callback] parameter type.
   *
   * The subscription will be unsubscribed after the test.
   *
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback The callback function
   * @return This instance of [EventsReceiver] for chaining
   */
  inline fun <reified T : Any> subscribe(skipRetained: Boolean = false,
                                         noinline callback: suspend (event: T) -> Unit): EventsReceiver {
    return subscribeTo(T::class.java, skipRetained, callback)
  }

  /**
   * A variant of [subscribe] that uses an instance of [EventCallback] as callback.
   *
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback Interface with implemented callback function
   * @return This instance of [EventsReceiver] for chaining
   * @see [subscribe]
   */
  inline fun <reified T : Any> subscribe(callback: EventCallback<T>, skipRetained: Boolean = false): EventsReceiver {
    return subscribeTo(T::class.java, callback, skipRetained)
  }

  /**
   * A variant of [subscribeTo] that uses an instance of [EventCallback] as callback.
   *
   * @param clazz Type of event to subscribe to
   * @param skipRetained Skips event already present in the flow. This is `false` by default
   * @param callback Interface with implemented callback function
   * @return This instance of [EventsReceiver] for chaining
   * @see [subscribeTo]
   */
  @JvmOverloads
  fun <T : Any> subscribeTo(clazz: Class<T>, callback: EventCallback<T>, skipRetained: Boolean = false): EventsReceiver {
    return subscribeTo(clazz, skipRetained) { callback.onEvent(it) }
  }

  /**
   * Unsubscribe from all events
   */
  @Suppress("RAW_RUN_BLOCKING")
  fun unsubscribe() {
    // TODO: Find a way to wait till all subscribers finished their work
    // https://youtrack.jetbrains.com/issue/AT-18/Simplify-refactor-code-for-starting-IDE-in-IdeRunContext#focus=Comments-27-8300203.0-0
    runBlocking(Dispatchers.IO) {
      for (jobList in jobs.values) {
        for (job in jobList) {
          job.cancelAndJoin()
        }
      }
    }

    jobs.clear()
  }
}
