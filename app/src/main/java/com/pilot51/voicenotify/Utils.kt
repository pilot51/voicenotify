/*
 * Copyright 2011-2025 Mark Injerd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pilot51.voicenotify

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

fun <T> T.isAny(vararg list: T) = list.any { this == it }

/** Convenience for calling [withLock] inside a new coroutine in [scope]. */
fun <T> Mutex.launchWithLock(
	scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
	action: suspend () -> T
) = scope.launch { withLock { action() } }

/** Same as [withTimeout] except [block] is interrupted when it times out. */
@Throws(TimeoutCancellationException::class)
suspend fun <T> withTimeoutInterruptible(timeout: Duration, block: () -> T) =
	withTimeout(timeout) { runInterruptible(block = block) }

/**
 * @return `false` if [text] is prefixed with [Constants.REGEX_PREFIX]
 * and the regex format is invalid. `true` if format is valid or not prefixed.
 */
fun validateRegexOption(text: String) = !text.startsWith(Constants.REGEX_PREFIX) || runCatching {
	Regex(text.removePrefix(Constants.REGEX_PREFIX))
}.isSuccess

