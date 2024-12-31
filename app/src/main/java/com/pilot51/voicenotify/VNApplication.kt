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

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.*

class VNApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		appContext = applicationContext
		startLoggingToFile()
	}

	/** Writes logcat messages from our process to [LOG_FILE_NAME] in our external files directory. */
	private fun startLoggingToFile() {
		val dir = getExternalFilesDir(null) ?: return
		try {
			dir.mkdirs()
			val logFile = File(dir, LOG_FILE_NAME)
			val process = Runtime.getRuntime().run {
				exec("logcat -c")
				exec("logcat")
			}
			val reader = BufferedReader(InputStreamReader(process.inputStream))
			CoroutineScope(Dispatchers.IO).launch {
				FileOutputStream(logFile, true).use { outStream ->
					try {
						reader.forEachLine {
							outStream.write("$it\n".toByteArray())
							limitFileSize(logFile)
						}
					} catch (e: IOException) {
						e.printStackTrace()
					}
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	/** Removes all lines at and before [MAX_LOG_BYTES] from the end of [logFile]. */
	private fun limitFileSize(logFile: File) {
		try {
			if (!logFile.exists() || logFile.length() < MAX_LOG_BYTES) return
			RandomAccessFile(logFile, "rw").use { raf ->
				raf.seek(raf.length() - MAX_LOG_BYTES)
				while (raf.readByte().toInt().toChar() != '\n') {
					if (raf.filePointer >= raf.length()) break
				}
				val remainingSize = raf.length() - raf.filePointer
				val buffer = ByteArray(remainingSize.toInt())
				raf.readFully(buffer)
				raf.seek(0)
				raf.write(buffer)
				raf.setLength(buffer.size.toLong())
			}
		} catch (e: Exception) {
			// Ignore since printing to log may cause an infinite loop
		}
	}

	companion object {
		private const val MAX_LOG_BYTES = 1_000_000L
		private const val LOG_FILE_NAME = "debug.log"
		lateinit var appContext: Context
		val logFile get() = appContext.getExternalFilesDir(null)?.let { File(it, LOG_FILE_NAME) }
	}
}
