/*
 * Copyright 2011-2025 Mark Injerd
 *
 * Originally contributed by Alex Balgavy in 2024.
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

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

// WARNING: Moving or renaming this will remove the tile from quick settings if added
@RequiresApi(Build.VERSION_CODES.N)
class VoiceNotifyTileService : TileService() {
	// Called when the tile can be updated
	override fun onStartListening() {
		super.onStartListening()
		updateTile(Service.isSuspended.value)
	}

	// Called when the user taps on the tile in an active or inactive state.
	override fun onClick() {
		super.onClick()
		val isSuspended = Service.toggleSuspend()
		updateTile(isSuspended)
	}

	private fun updateTile(suspended: Boolean) {
		val isRunning = Service.isRunning.value

		qsTile.state = when {
			!isRunning -> Tile.STATE_UNAVAILABLE
			isRunning && suspended -> Tile.STATE_INACTIVE
			else -> Tile.STATE_ACTIVE
		}
		qsTile.updateTile()
	}
}
