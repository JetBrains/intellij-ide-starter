package com.intellij.ide.starter.runner

import java.util.concurrent.atomic.AtomicReference

/**
 * Container that contains current display name
 */
object CurrentDisplayName {
  private lateinit var displayName: AtomicReference<String>

  fun set(method: String?) {
    if (this::displayName.isInitialized) {
      displayName.set(method)
    }
    else {
      displayName = AtomicReference(method)
    }
  }

  fun get(): String? {
    return if (this::displayName.isInitialized) {
      displayName.get()
    }
    else null
  }
}