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
import android.icu.text.AlphabeticIndex
import android.icu.text.Collator
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.util.Locale

fun <T> T.isAny(vararg list: T) = list.any { this == it }

val isPreview @Composable get() = LocalInspectionMode.current

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
annotation class VNPreview

class BooleanProvider: PreviewParameterProvider<Boolean> {
	override val values: Sequence<Boolean> = sequenceOf(false, true)
}


/**
 * Help to get a alphabetic of a char.
 *
 * Use:
 * ```
 *      val sectionName = AlphabeticIndexHelper.computeSectionName("食神")
 *      log: sectionName = "S"
 * ```
 */
object AlphabeticIndexHelper {

	@JvmStatic
	fun computeSectionName(c: CharSequence): String {
		if (isStartsWithDigit(c)) return c.toString()
		return computeSectionName(Locale.getDefault(), c)
	}


	@JvmStatic
	fun computeCNSectionName(c: CharSequence): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		computeSectionName(
			LocaleList(Locale.CHINESE, Locale.SIMPLIFIED_CHINESE), c
		)
	} else {
		TODO("VERSION.SDK_INT < N")
	}

	@JvmStatic
	fun computeSectionName(locale: Locale, c: CharSequence): String {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			AlphabeticIndex<Any>(locale).buildImmutableIndex().let {
				it.getBucket(it.getBucketIndex(c)).label
			}
		} else {
			TODO("VERSION.SDK_INT < N")
		}
	}


	@JvmStatic
	fun computeSectionName(localeList: LocaleList, c: CharSequence): String {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			val primaryLocale = if (localeList.isEmpty) Locale.ENGLISH else localeList[0]
			val ai = AlphabeticIndex<Any>(primaryLocale)
			for (index in 1 until localeList.size()) {
				ai.addLabels(localeList[index])
			}
			ai.buildImmutableIndex().let {
				it.getBucket(it.getBucketIndex(c)).label
			}
		} else {
			TODO("VERSION.SDK_INT < N")
		}
	}
	//  write CompareCollator wrap computeSectionName
	@RequiresApi(Build.VERSION_CODES.N)
	fun computeCompareCollator(c: CharSequence): String {
		val collator = Collator.getInstance(Locale.getDefault())
		return collator.getCollationKey(c.toString()).sourceString
	}


	@JvmStatic
	fun isStartsWithDigit(c: CharSequence): Boolean = Character.isDigit(c[0])


	fun getAlphabeticIndex(locale: Locale): AlphabeticIndex<Any> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		AlphabeticIndex(locale)
	} else {
		TODO("VERSION.SDK_INT < N")
	}
}

