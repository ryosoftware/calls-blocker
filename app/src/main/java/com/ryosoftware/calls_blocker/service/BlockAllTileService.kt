package com.ryosoftware.calls_blocker.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.SettingsManager
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class BlockAllTileService : TileService() {
    companion object {
        const val ACTION_BLOCK_ALL_CHANGED = "${BuildConfig.APPLICATION_ID}.BLOCK_ALL_CHANGED"
        const val EXTRA_VALUE = "value"
    }

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        settingsManager.blockAll = !settingsManager.blockAll

        val toastMessage = if (settingsManager.blockAll) R.string.block_all_enabled else R.string.block_all_disabled
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()

        sendBroadcast(
            Intent(ACTION_BLOCK_ALL_CHANGED).apply {
                setPackage(packageName)
                putExtra(EXTRA_VALUE, settingsManager.blockAll)
            }
        )

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = when {
            ! settingsManager.isScreeningActive() -> Tile.STATE_UNAVAILABLE
            settingsManager.blockAll -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}