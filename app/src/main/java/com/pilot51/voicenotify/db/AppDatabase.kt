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
package com.pilot51.voicenotify.db

import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import com.pilot51.voicenotify.VNApplication
import kotlinx.coroutines.flow.Flow

@Database(
	version = 2,
	entities = [
		App::class,
		Settings::class
	],
	autoMigrations = [
		AutoMigration (from = 1, to = 2, spec = AppDatabase.Migration1To2::class)
	]
)
@RewriteQueriesToDropUnusedColumns
abstract class AppDatabase : RoomDatabase() {
	abstract val appDao: AppDao
	abstract val settingsDao: SettingsDao

	interface BaseDao<T> {
		@Insert
		suspend fun insert(entity: T)

		@Upsert
		suspend fun upsert(entity: T)

		@Delete
		suspend fun delete(entity: T)
	}

	@Dao
	interface AppDao : BaseDao<App> {
		@Query("SELECT * FROM apps WHERE package = :pkg")
		suspend fun get(pkg: String): App

		@Query("SELECT * FROM apps")
		suspend fun getAll(): List<App>

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

	@Dao
	interface SettingsDao : BaseDao<Settings> {
		@Query("SELECT * FROM settings WHERE app_package IS NULL")
		fun getGlobalSettings(): Flow<Settings?>

		@Query("SELECT * FROM settings WHERE app_package = :pkg")
		fun getAppSettings(pkg: String): Flow<Settings?>

		@Query("SELECT EXISTS (SELECT * FROM settings WHERE app_package IS NULL)")
		suspend fun hasGlobalSettings(): Boolean

		@Query("SELECT app_package FROM settings WHERE app_package IS NOT NULL")
		fun packagesWithOverride(): Flow<List<String>>

		@Query("DELETE FROM settings WHERE app_package = :pkg")
		suspend fun deleteByPackage(pkg: String)
	}

	@DeleteColumn(tableName = "apps", columnName = "_id")
	class Migration1To2 : AutoMigrationSpec

	companion object {
		val db by lazy {
			Room.databaseBuilder(
				VNApplication.appContext,
				AppDatabase::class.java, "apps.db"
			).build()
		}
	}
}
