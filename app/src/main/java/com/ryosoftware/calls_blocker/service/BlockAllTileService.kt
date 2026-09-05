package com.ryosoftware.calls_blocker.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
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
        const val EXTRA_BLOCK_WHEN_DND = "blockWhenDnd"
        const val EXTRA_BLOCK_INTERNATIONAL = "blockInternational"

        private const val CAUSE_MANUAL = "manual"
        private const val CAUSE_DND = "dnd"
        private const val CAUSE_INTERNATIONAL = "international"
        private const val CAUSE_SCHEDULE = "schedule"
    }

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var scheduleRuleRepository: ScheduleRuleRepository

    private var dndReceiver: BroadcastReceiver? = null

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        registerDndReceiver()
        updateTile()
    }

    override fun onStopListening() {
        unregisterDndReceiver()
        super.onStopListening()
    }

    private fun registerDndReceiver() {
        if (dndReceiver != null) return

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                    updateTile()
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        dndReceiver = receiver
    }

    private fun unregisterDndReceiver() {
        dndReceiver?.let { unregisterReceiver(it) }
        dndReceiver = null
    }

    override fun onClick() {
        super.onClick()

        val now = System.currentTimeMillis()
        val manualActive = settingsManager.blockAll && settingsManager.blockAllUntil > now
        val dndActive = settingsManager.shouldBlockDueToDnd()
        val internationalActive = settingsManager.blockInternational &&
            settingsManager.allowedCountryIsos.split(",").none { it.trim().isNotEmpty() }
        val scheduleActive = settingsManager.shouldBlockDueToSchedule(scheduleRuleRepository.isInScheduleBlock())

        val toastRes: Int
        if (manualActive || dndActive || internationalActive || scheduleActive) {
            val disabledCauses = buildList {
                if (manualActive) {
                    settingsManager.blockAll = false
                    add(CAUSE_MANUAL)
                }
                if (dndActive) {
                    settingsManager.blockWhenDndPaused = true
                    add(CAUSE_DND)
                }
                if (internationalActive) {
                    settingsManager.blockInternational = false
                    add(CAUSE_INTERNATIONAL)
                }
                if (scheduleActive) {
                    scheduleRuleRepository.scheduleWindowEndMillis()
                        ?.let { settingsManager.scheduleBlockingPausedUntil = it }
                    add(CAUSE_SCHEDULE)
                }
            }
            settingsManager.tileDisabledCauses = disabledCauses.joinToString(",")
            toastRes = if (disabledCauses.size == 1 && CAUSE_MANUAL in disabledCauses) {
                R.string.block_all_disabled
            } else {
                R.string.tile_blocking_deactivated
            }
        } else {
            val restored = restoreDisabledCauses()
            if (restored) {
                toastRes = R.string.tile_blocking_re_enabled
            } else {
                settingsManager.blockAll = true
                settingsManager.blockAllUntil = Long.MAX_VALUE
                toastRes = R.string.block_all_enabled
            }
            settingsManager.tileDisabledCauses = ""
        }

        Toast.makeText(this, toastRes, Toast.LENGTH_LONG).show()

        val blockAll = settingsManager.blockAll
        sendBroadcast(
            Intent(ACTION_BLOCK_ALL_CHANGED).apply {
                setPackage(packageName)
                putExtra(EXTRA_VALUE, blockAll)
                putExtra(EXTRA_BLOCK_WHEN_DND, settingsManager.blockWhenDnd)
                putExtra(EXTRA_BLOCK_INTERNATIONAL, settingsManager.blockInternational)
                if (blockAll) {
                    putExtra(EXTRA_UNTIL, settingsManager.blockAllUntil)
                }
            }
        )

        updateTile()
    }

    private fun restoreDisabledCauses(): Boolean {
        val disabled = settingsManager.tileDisabledCauses
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        var restored = false

        if (CAUSE_MANUAL in disabled) {
            settingsManager.blockAll = true
            settingsManager.blockAllUntil = Long.MAX_VALUE
            restored = true
        }
        if (CAUSE_DND in disabled && settingsManager.blockWhenDnd && settingsManager.isDndActive()) {
            settingsManager.blockWhenDndPaused = false
            restored = true
        }
        if (CAUSE_INTERNATIONAL in disabled &&
            settingsManager.allowedCountryIsos.split(",").none { it.trim().isNotEmpty() }
        ) {
            settingsManager.blockInternational = true
            restored = true
        }
        if (CAUSE_SCHEDULE in disabled && scheduleRuleRepository.isInScheduleBlock()) {
            settingsManager.scheduleBlockingPausedUntil = 0L
            restored = true
        }

        return restored
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
            settingsManager.shouldBlockDueToDnd() ->
                R.drawable.ic_tile_block_all_dnd to getString(R.string.blocking_all_enabled_by_dnd)
            settingsManager.blockInternational &&
                settingsManager.allowedCountryIsos.split(",").none { it.trim().isNotEmpty() } ->
                R.drawable.ic_tile_block_all_international to getString(R.string.blocking_all_enabled_by_intl)
            settingsManager.shouldBlockDueToSchedule(scheduleRuleRepository.isInScheduleBlock()) ->
                R.drawable.ic_tile_block_all_scheduler to getString(R.string.blocking_all_enabled_by_scheduler)
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