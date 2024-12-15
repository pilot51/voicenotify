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
import com.pilot51.voicenotify.VNApplication.Companion.appContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

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

		@Insert
		suspend fun insert(entities: Collection<T>)

		@Update
		suspend fun update(entity: T)

		@Update
		suspend fun update(entity: Collection<T>)

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

		/**
		 * Updates enabled value of app in database matching package name.
		 * @param app The app to update in the database.
		 */
		@Transaction
		suspend fun updateAppEnable(app: App) {
			updateAppEnable(app.packageName, app.isEnabled)
		}

		@Query("UPDATE apps SET is_enabled = :enabled WHERE package = :pkg")
		suspend fun updateAppEnable(pkg: String, enabled: Boolean)
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
		const val DB_NAME = "apps.db"
		private val _db = MutableStateFlow(buildDB())
		val db: AppDatabase get() = _db.value
		@OptIn(ExperimentalCoroutinesApi::class)
		val globalSettingsFlow = _db.flatMapMerge { it.settingsDao.getGlobalSettings().filterNotNull() }

		private fun buildDB() = Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME).build()

		fun resetInstance() { _db.value = buildDB() }

		@OptIn(ExperimentalCoroutinesApi::class)
		fun getAppSettingsFlow(app: App) = _db.flatMapMerge { db ->
			db.settingsDao.getAppSettings(app.packageName).map {
				it ?: Settings(appPackage = app.packageName)
			}
		}
	}
}
