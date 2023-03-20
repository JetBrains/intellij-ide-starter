package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.ide.IdeProductProvider
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class IdeProductProviderTest {
  @Test
  fun listingAllIdeInfoShouldWork() {
    IdeProductProvider.getProducts().shouldNotBeEmpty()
  }
}