package com.intellij.ide.starter.tests.unit

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class IdeProductProviderTest {
  @Test
  fun listingAllIdeInfoShouldWork() {
    IdeProductProvider.getProducts().shouldNotBeEmpty()
  }
}