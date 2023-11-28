package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.utils.hyphenateTestName
import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method

/**
 * Format: ClassName/testMethodName => class-name/test-method-name
 */
fun TestInfo.hyphenateWithClass(): String {

  val className = this.testClass.get().simpleName
  val methodName = this.testMethodName()

  return "$className/$methodName".hyphenateTestName()
}

fun TestInfo.hyphenate(): String {
  return testMethod.get().name.hyphenateTestName()
}

fun TestInfo.testMethodName(): String {
  return if (this.testMethod.isPresent) testMethod.get().name
  else CurrentTestMethod.get()?.name ?: ""
}


private fun checkTestMethodIsNotNull(testMethod: Method?) {
  if (testMethod == null)
    throw IllegalArgumentException("Cannot find current test method. Most likely you need to provide testName manually in the test context")
}

/**
 * @return Hyphenated test name from the current test method as testMethodName => test-method-name
 */
fun CurrentTestMethod.hyphenate(): String {
  val method: Method? = this.get()
  checkTestMethodIsNotNull(method)
  return requireNotNull(method).name.hyphenateTestName()
}

/**
 * @return Hyphenated test class and test method name from the current test method as ClassName/testMethodName => class-name/test-method-name
 */
fun CurrentTestMethod.hyphenateWithClass(): String {
  val method: Method? = this.get()
  checkTestMethodIsNotNull(method)

  return "${requireNotNull(method).declaringClass.simpleName}/${method.name}".hyphenateTestName()
}