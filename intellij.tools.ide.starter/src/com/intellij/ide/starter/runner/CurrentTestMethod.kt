package com.intellij.ide.starter.runner

data class TestMethod(val name: String, val clazz: String, val clazzSimpleName: String, val displayName: String) {
  fun fullName(): String {
    return "$clazz.$name"
  }
}

/**
 * Container that contains current test method reference
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