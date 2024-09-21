package com.pilot51.voicenotify

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

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
        val result = Service.toggleSuspend()
        updateTile(result)
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