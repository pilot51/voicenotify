/*
 * Copyright 2012 Mark Injerd
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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import java.util.*

internal class Database private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(CREATE_TBL_APPS)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

	companion object {
		private var database: Database? = null
		private const val DB_VERSION = 1
		private const val DB_NAME = "apps.db"
		private const val TABLE_NAME = "apps"
		private const val COLUMN_PACKAGE = "package"
		private const val COLUMN_LABEL = "name"
		private const val COLUMN_ENABLED = "is_enabled"
		private const val CREATE_TBL_APPS = ("create table if not exists " + TABLE_NAME + "(" + BaseColumns._ID
			+ " integer primary key autoincrement, " + COLUMN_PACKAGE + " text not null, "
			+ COLUMN_LABEL + " text not null, " + COLUMN_ENABLED + " integer);")

		/**
		 * Initializes database object if not already initialized.
		 */
		fun init(context: Context) {
			if (database == null) {
				database = Database(context)
			}
		}
		/**
		 * Get: A new List containing all apps from the database.
		 * Set: Clears and sets all apps in database.
		 */
		@get:Synchronized
		@set:Synchronized
		var apps: List<App>
			get() {
				val db = database!!.readableDatabase
				val cursor = db.query(TABLE_NAME, null, null, null, null, null, "$COLUMN_LABEL COLLATE NOCASE")
				val list: MutableList<App> = ArrayList()
				while (cursor.moveToNext()) {
					list.add(App(
						cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PACKAGE)),
						cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LABEL)),
						cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
					))
				}
				cursor.close()
				db.close()
				return list
			}
			set(apps) {
				val list: List<App> = ArrayList(apps)
				val db = database!!.writableDatabase
				db.delete(TABLE_NAME, null, null)
				var values: ContentValues
				for (app in list) {
					values = ContentValues()
					values.put(COLUMN_PACKAGE, app.`package`)
					values.put(COLUMN_LABEL, app.label)
					values.put(COLUMN_ENABLED, if (app.enabled) 1 else 0)
					db.insert(TABLE_NAME, null, values)
				}
				db.close()
			}

		/**
		 * Updates app in database matching package name or adds if no match found.
		 * @param app The app to add or update in the database.
		 */
		@Synchronized
		fun addOrUpdateApp(app: App) {
			val values = ContentValues()
			values.put(COLUMN_PACKAGE, app.`package`)
			values.put(COLUMN_LABEL, app.label)
			values.put(COLUMN_ENABLED, if (app.enabled) 1 else 0)
			val db = database!!.writableDatabase
			if (db.update(TABLE_NAME, values, "$COLUMN_PACKAGE = ?", arrayOf(app.`package`)) == 0) {
				db.insert(TABLE_NAME, null, values)
			}
			db.close()
		}

		/**
		 * Updates enabled value of app in database matching package name.
		 * @param app The app to update in the database.
		 */
		@Synchronized
		fun updateAppEnable(app: App) {
			val values = ContentValues()
			values.put(COLUMN_ENABLED, if (app.enabled) 1 else 0)
			val db = database!!.writableDatabase
			db.update(TABLE_NAME, values, "$COLUMN_PACKAGE = ?", arrayOf(app.`package`))
			db.close()
		}

		/**
		 * Removes app from database matching package name.
		 * @param app The app to remove from the database.
		 */
		@Synchronized
		fun removeApp(app: App) {
			val db = database!!.writableDatabase
			db.delete(TABLE_NAME, "$COLUMN_PACKAGE = ?", arrayOf(app.`package`))
			db.close()
		}
	}
}