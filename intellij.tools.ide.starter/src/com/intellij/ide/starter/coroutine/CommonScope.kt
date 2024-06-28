package com.intellij.ide.starter.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Lifespan is as long as entire test suite run. When test suite is finished whole coroutines tree will be cancelled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that what you need.
 */
val testSuiteSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Lifespan is limited to duration each test. By the end of the test whole coroutines tree will be cancelled.
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that what you need.
 */
val perTestSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Lifespan is limited to the duration of a single thin client execution. When the client is shut down, the whole coroutines tree is going
 * to be canceled.
 */
val perClientSupervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * In case of unhandled exception in child coroutines all the coroutines tree (parents and other branches) will be cancelled.
 * In most scenarious you don't need that behaviour.
 */
val simpleScope = CoroutineScope(Job() + Dispatchers.IO)