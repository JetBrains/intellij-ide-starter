package com.intellij.ide.starter.utils

import com.intellij.ide.starter.utils.FileSystem.isFileUpToDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.HttpClientBuilder
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// TODO: migrate on okhttp ?
object HttpClient {
  private val locks = ConcurrentHashMap<String, Semaphore>()

  fun <Y> sendRequest(request: HttpUriRequest, processor: (HttpResponse) -> Y): Y {
    HttpClientBuilder.create().build().use { client ->
      client.execute(request).use { response ->
        return processor(response)
      }
    }
  }

  fun download(url: String, outFile: File, retries: Long = 3): Boolean = download(url, outFile.toPath(), retries)

  /**
   * Downloading file from [url] to [outPath] with [retries].
   * @return true - if successful, false - otherwise
   */
  fun download(url: String, outPath: Path, retries: Long = 3, timeout: Duration = 10.minutes): Boolean {
    val lock = locks.getOrPut(outPath.toAbsolutePath().toString()) { Semaphore(1) }
    lock.acquire()

    return try {
      logOutput("Downloading $url to $outPath")
      var isSuccessful = false

      @Suppress("RAW_RUN_BLOCKING")
      runBlocking {
        withTimeout(timeout = timeout) {
          withRetry(retries = retries) {
            sendRequest(HttpGet(url)) { response ->
              require(response.statusLine.statusCode == 200) { "Failed to download $url: $response" }
              if (!outPath.parent.exists()) {
                outPath.parent.createDirectories()
              }
              outPath.outputStream().buffered(10 * 1024 * 1024).use { stream ->
                response.entity?.writeTo(stream)
              }

              isSuccessful = true
            }
          }
        }
      }

      isSuccessful
    }
    finally {
      lock.release()
    }
  }

  /**
   * [url] - source to download
   * [targetFile] - output file
   * [retries] - how many times retry to download in case of failure
   * @return true - if successful, false - otherwise
   */
  fun downloadIfMissing(url: String, targetFile: Path, retries: Long = 3, timeout: Duration = 10.minutes): Boolean {
    val lock = locks[targetFile.toAbsolutePath().toString()]
    lock?.tryAcquire()

    try {
      // TODO: move this check to appropriate place
      if (url.contains("https://github.com")) {
        if (!targetFile.isFileUpToDate()) {
          targetFile.toFile().delete()
        }
      }

      if (targetFile.isRegularFile() && targetFile.fileSize() > 0) {
        logOutput("File $targetFile was already downloaded. Size ${targetFile.fileSize().formatSize()}")
        return true
      }
    }
    finally {
      lock?.release()
    }

    return download(url, targetFile, retries, timeout)
  }
}