package com.pilot51.voicenotify

import android.icu.text.AlphabeticIndex
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Help to get a alphabetic of a char.
 *
 * Use:
 * ```
 *      val sectionName = AlphabeticIndexHelper.computeSectionName(Locale.CHINESE, "神")
 *      log: sectionName = "S"
 * ```
 */
object AlphabeticIndexHelper {
    @JvmStatic
    fun computeSectionName(c: CharSequence): String {
        return computeSectionName(Locale.getDefault(), c)
    }

    @JvmStatic
    fun computeCNSectionName(c: CharSequence): String = computeSectionName(
        LocaleList(Locale.CHINESE, Locale.SIMPLIFIED_CHINESE), c
    )

    @JvmStatic
    fun computeSectionName(locale: Locale, c: CharSequence): String {
        return AlphabeticIndex<Any>(locale).buildImmutableIndex().let {
            it.getBucket(it.getBucketIndex(c)).label
        }
    }

    @JvmStatic
    fun computeSectionName(localeList: LocaleList, c: CharSequence): String {
        val primaryLocale = if (localeList.isEmpty) Locale.ENGLISH else localeList[0]
        val ai = AlphabeticIndex<Any>(primaryLocale)
        for (index in 1 until localeList.size()) {
            ai.addLabels(localeList[index])
        }
        return ai.buildImmutableIndex().let {
            it.getBucket(it.getBucketIndex(c)).label
        }
    }

    @JvmStatic
    fun isStartsWithDigit(c: CharSequence): Boolean = Character.isDigit(c[0])
}
