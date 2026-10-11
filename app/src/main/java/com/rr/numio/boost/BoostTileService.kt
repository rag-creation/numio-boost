package com.rr.numio.boost

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.rr.numio.boost.engine.BoostPrefs
import com.rr.numio.boost.engine.BoostService

/** Quick Settings tile: tap to turn Boost on or off. */
class BoostTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (BoostService.running.value) {
            BoostService.stop(this)
        } else {
            try {
                BoostService.start(this)
            } catch (_: Exception) {
                // Some Android versions block starting the service from the tile;
                // open the app instead so the user can start it there.
                openApp()
            }
        }
        updateTile(!BoostService.running.value)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(on: Boolean = BoostService.running.value) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "${BoostPrefs(this).boostPercent}%" else "Off"
        }
        tile.updateTile()
    }
}
