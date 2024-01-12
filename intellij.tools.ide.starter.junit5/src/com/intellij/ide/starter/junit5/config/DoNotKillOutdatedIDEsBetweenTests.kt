package com.intellij.ide.starter.junit5.config

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Kill of hanged/outdated IDE processes will not be performed.
 * It's useful for the reuse the same IDE instance between multiple tests.
 */
open class DoNotKillOutdatedIDEsBetweenTests : AfterAllCallback {

  companion object {
    var shouldBeKilled: Boolean = true
  }

  init {
    // on each extension usage/init in tests
    shouldBeKilled = false
  }

  override fun afterAll(context: ExtensionContext?) {
    // make sure other tests are not affected
    shouldBeKilled = true
  }
}

