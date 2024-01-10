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

import android.content.Context
import android.provider.BaseColumns
import android.widget.Toast
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.pilot51.voicenotify.AppDatabase.Companion.db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Entity(tableName = "apps")
data class App(
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = BaseColumns._ID)
	val id: Int? = null,
	@ColumnInfo(name = "package")
	val packageName: String,
	@ColumnInfo(name = "name", collate = ColumnInfo.NOCASE)
	val label: String,
	@ColumnInfo(name = "is_enabled")
	var isEnabled: Boolean?
) {
	@get:Ignore
	var enabled: Boolean
		get() = isEnabled!!
		set(value) { isEnabled = value }

	/**
	 * Updates self in database.
	 * @return This instance.
	 */
	suspend fun updateDb(): App {
		db.appDao.addOrUpdateApp(this)
		return this
	}

	fun setEnabled(enable: Boolean, updateDb: Boolean = true) {
		enabled = enable
		if (updateDb) CoroutineScope(Dispatchers.IO).launch {
			db.appDao.updateAppEnable(this@App)
		}
	}

	fun setEnabledWithToast(enable: Boolean, context: Context) {
		setEnabled(enable)
		Toast.makeText(context,
			context.getString(
				if (enable) R.string.app_is_not_ignored else R.string.app_is_ignored,
				label
			),
			Toast.LENGTH_SHORT
		).show()
	}

	/** Removes self from database. */
	fun remove() {
		CoroutineScope(Dispatchers.IO).launch {
			db.appDao.removeApp(this@App)
		}
	}
}
