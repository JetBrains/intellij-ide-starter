package com.intellij.ide.starter.command


private const val CMD_PREFIX = '%'

const val RUN_ACTION_CMD_PREFIX = "${CMD_PREFIX}action"

fun <T : CommandChain> T.runAction(actionId: String): T {
  addCommand(RUN_ACTION_CMD_PREFIX, actionId)
  return this
}

const val RUN_PHP_CMD_PREFIX = "${CMD_PREFIX}runPhpRunConfiguration"

fun <T : CommandChain> T.runPhpRunConfiguration(runConfigurationName: String): T {
  addCommand(RUN_PHP_CMD_PREFIX, runConfigurationName)
  return this
}

const val INLINE_METHOD_TEST_CMD_PREFIX = "${CMD_PREFIX}phpInlineMethodTest"

fun <T : CommandChain> T.phpInlineMethodTest(command: String): T {
  addCommand(INLINE_METHOD_TEST_CMD_PREFIX, command)
  return this
}

const val INLINE_METHOD_CMD_PREFIX = "${CMD_PREFIX}phpInlineMethod"

fun <T : CommandChain> T.phpInlineMethod(): T {
  addCommand(INLINE_METHOD_CMD_PREFIX)
  return this
}

const val EXTRACT_METHOD_TEST_CMD_PREFIX = "${CMD_PREFIX}phpExtractMethodTest"

fun <T : CommandChain> T.phpExtractMethodTest(command: String): T {
  addCommand(EXTRACT_METHOD_TEST_CMD_PREFIX, command)
  return this
}

const val EXTRACT_METHOD_CMD_PREFIX = "${CMD_PREFIX}phpExtractMethod"

fun <T : CommandChain> T.phpExtractMethod(): T {
  addCommand(EXTRACT_METHOD_CMD_PREFIX)
  return this
}