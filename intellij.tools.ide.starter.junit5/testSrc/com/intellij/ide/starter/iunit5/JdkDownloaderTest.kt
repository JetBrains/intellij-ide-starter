package com.intellij.ide.starter.iunit5

import com.intellij.ide.starter.sdk.JdkDownloaderFacade
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Files

class JdkDownloaderTest {
  @Test
  fun testJDKCanBeDownloaded(){
    JdkDownloaderFacade.allJdks.shouldHaveAtLeastSize(10)
    val sdk = JdkDownloaderFacade.jdk11.toSdk()
    sdk.sdkPath.toFile().exists().shouldBe(true)
    Files.walk(sdk.sdkPath).use { it.count() }.shouldBeGreaterThan(JdkDownloaderFacade.MINIMUM_JDK_FILES_COUNT.toLong())
  }
}