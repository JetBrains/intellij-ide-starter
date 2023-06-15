package com.intellij.ide.starter.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** @return T - if successful; null - otherwise */
suspend fun <T> withRetryAsync(retries: Long = 3,
                               messageOnFailure: String = "",
                               delay: Duration = 10.seconds,
                               retryAction: suspend () -> T): T? {

  (1..retries).forEach { failureCount ->
    try {
      return retryAction()
    }
    catch (t: Throwable) {
      if (messageOnFailure.isNotBlank())
        logError(messageOnFailure)

      t.printStackTrace()

      if (failureCount < retries) {
        logError("Retrying in 10 sec ...")
        delay(delay)
      }
    }
  }

  return null
}

/** @return T - if successful; null - otherwise */
fun <T> withRetry(
  retries: Long = 3,
  messageOnFailure: String = "",
  delay: Duration = 10.seconds,
  retryAction: () -> T
): T? = runBlocking(Dispatchers.IO) {
  withRetryAsync(retries, messageOnFailure) { retryAction() }
}

inline fun <reified E : Exception, reified T> executeWithRetry(retries: Int = 3,
                                                               errorMsg: String = "Fail to execute action $retries attempts",
                                                               delay: Duration,
                                                               call: () -> T): T {
  for (i in 0..retries) {
    try {
      return call()
    }
    catch (e: Exception) {
      logError("Got error $e on $i attempt")
      if (e is E) {
        Thread.sleep(delay.inWholeMilliseconds)
        call()
      }
      else throw e
    }
  }
  throw IllegalStateException(errorMsg)
}
