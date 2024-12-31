/*
 * Copyright 2011-2024 Mark Injerd
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

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

fun <T> T.isAny(vararg list: T) = list.any { this == it }

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

val isPreview @Composable get() = LocalInspectionMode.current

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class VNPreview

class BooleanProvider: PreviewParameterProvider<Boolean> {
	override val values: Sequence<Boolean> = sequenceOf(false, true)
}
