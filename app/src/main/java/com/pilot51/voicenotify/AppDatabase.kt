/*
 * Copyright 2011-2023 Mark Injerd
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

import androidx.room.*

@Database(version = 1, entities = [App::class])
@RewriteQueriesToDropUnusedColumns
abstract class AppDatabase : RoomDatabase() {
	abstract val appDao: AppDao

	@Dao
	interface AppDao {
		@Query("SELECT * FROM apps")
		suspend fun getAll(): List<App>

		@Insert
		suspend fun insert(app: App): Long

		@Update
		suspend fun update(apps: List<App>)

		@Query("SELECT EXISTS (SELECT * FROM apps WHERE package = :pkg)")
		suspend fun existsByPackage(pkg: String): Boolean

		@Query("UPDATE apps SET name = :name, is_enabled = :enabled WHERE package = :pkg")
		suspend fun updateByPackage(pkg: String, name: String, enabled: Boolean)

		/**
		 * Updates app in database matching package name or adds if no match found.
		 * @param app The app to add or update in the database.
		 */
		@Transaction
		suspend fun addOrUpdateApp(app: App) {
			if (existsByPackage(app.packageName)) {
				updateByPackage(
					pkg = app.packageName,
					name = app.label,
					enabled = app.enabled
				)
			} else {
				insert(app)
			}
		}

		@Transaction
		suspend fun upsert(apps: List<App>) {
			apps.forEach {
				addOrUpdateApp(it)
			}
		}

		/**
		 * Updates enabled value of app in database matching package name.
		 * @param app The app to update in the database.
		 */
		@Transaction
		suspend fun updateAppEnable(app: App) {
			updateAppEnable(app.packageName, app.enabled)
		}

		@Query("UPDATE apps SET is_enabled = :enabled WHERE package = :pkg")
		suspend fun updateAppEnable(pkg: String, enabled: Boolean?)

		/**
		 * Removes app from database matching package name.
		 * @param app The app to remove from the database.
		 */
		@Transaction
		suspend fun removeApp(app: App) {
			removeApp(app.packageName)
		}

		@Query("DELETE FROM apps WHERE package = :pkg")
		suspend fun removeApp(pkg: String)
	}

	companion object {
		val db by lazy {
			Room.databaseBuilder(
				VNApplication.appContext,
				AppDatabase::class.java, "apps.db"
			).build()
		}
	}
}
