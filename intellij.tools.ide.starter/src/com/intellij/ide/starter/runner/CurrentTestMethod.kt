package com.intellij.ide.starter.runner

data class TestMethod(val name: String, val clazz: String, val clazzSimpleName: String, val displayName: String) {
  fun fullName(): String {
    return "$clazz.$name"
  }
}

/**
 * Container that contains the current test method reference.
 * Method is provided by [com.intellij.ide.starter.junit5.CurrentTestMethodProvider]
 */
object CurrentTestMethod {
  @Volatile
  private var testMethod: TestMethod? = null

  fun set(method: TestMethod?) {
      testMethod = method
  }

  fun get(): TestMethod? {
    return testMethod
  }
}