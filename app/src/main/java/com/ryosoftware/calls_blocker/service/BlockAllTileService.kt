package com.ryosoftware.calls_blocker.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.repository.ScheduleRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date

@AndroidEntryPoint
class BlockAllTileService : TileService() {
    companion object {
        const val ACTION_BLOCK_ALL_CHANGED = "${BuildConfig.APPLICATION_ID}.BLOCK_ALL_CHANGED"
        const val EXTRA_VALUE = "value"
        const val EXTRA_UNTIL = "until"
    }

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var scheduleRuleRepository: ScheduleRuleRepository

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
        if (settingsManager.blockAll) {
            settingsManager.blockAllUntil = Long.MAX_VALUE
        }

        val toastMessage = if (settingsManager.blockAll) R.string.block_all_enabled else R.string.block_all_disabled
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()

        sendBroadcast(
            Intent(ACTION_BLOCK_ALL_CHANGED).apply {
                val blockAll = settingsManager.blockAll
                setPackage(packageName)
                putExtra(EXTRA_VALUE, blockAll)
                if (blockAll) {
                    putExtra(EXTRA_UNTIL, settingsManager.blockAllUntil)
                }
            }
        )

        updateTile()
    }

    private fun getTileResources(): Pair<Int, String>? =
        when {
            settingsManager.blockAll && settingsManager.blockAllUntil > System.currentTimeMillis() -> {
                val blockAllUntil = settingsManager.blockAllUntil

                val string = if (blockAllUntil == Long.MAX_VALUE) {
                    getString(R.string.blocking_all_enabled)
                } else {
                    val date = Date(blockAllUntil)
                    getString(
                        R.string.blocking_all_enabled_until,
                        resources.getQuantityString(
                            R.plurals.date_and_time,
                            date.toInstant().atZone(ZoneId.systemDefault()).hour,
                            DateFormat.getDateInstance(DateFormat.SHORT).format(date),
                            DateFormat.getTimeInstance(DateFormat.MEDIUM).format(date)
                        )
                    )
                }

                R.drawable.ic_tile_block_all to string
            }
            settingsManager.blockWhenDnd && settingsManager.isDndActive() ->
                R.drawable.ic_tile_block_all_dnd to getString(R.string.blocking_all_enabled)
            settingsManager.blockInternational &&
                settingsManager.allowedCountryIsos.split(",").none { it.trim().isNotEmpty() } ->
                R.drawable.ic_tile_block_all_international to getString(R.string.blocking_all_enabled)
            scheduleRuleRepository.isInScheduleBlock() ->
                R.drawable.ic_tile_block_all_scheduler to getString(R.string.blocking_all_enabled)
            else -> null
        }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.blocking_all)
        if (! settingsManager.isScreeningActive()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = getString(R.string.blocking_all_missing_permissions)
        } else {
            val tileResources = getTileResources()
            if (tileResources == null) {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.blocking_all_disabled)
                tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_block_all)
            } else {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = tileResources.second
                tile.icon = Icon.createWithResource(this, tileResources.first)
            }
        }
        tile.updateTile()
    }
}