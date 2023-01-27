package com.intellij.ide.starter.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Unhandled exceptions in the tree of child coroutines will not affect any other coroutines (parent included).
 * Usually that what you need.
 */
val supervisorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * In case of unhandled exception in child coroutines all the coroutines tree (parents and other branches) will be cancelled.
 * In most scenarious you don't need that behaviour.
 */
val simpleScope = CoroutineScope(Job() + Dispatchers.IO)