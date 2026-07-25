package com.ryosoftware.calls_blocker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.service.BlockAllTileService
import com.ryosoftware.calls_blocker.ui.theme.CallsBlockerTheme
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
@AndroidEntryPoint
class BlockAllTimePickerActivity : ComponentActivity() {
    companion object {
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
    }

    @Inject
    lateinit var settingsManager: SettingsManager

    private fun getIntervalString(seconds: Int): String {
        if (seconds == 0) return getString(R.string.until_manually_disabled)

        val hours = seconds / SECONDS_PER_HOUR
        val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE

        return if ((hours > 0) && (minutes > 0)) {
            getString(R.string.hours_and_minutes_large, resources.getQuantityString(R.plurals.hours, hours.toInt(), hours), resources.getQuantityString(R.plurals.minutes, minutes.toInt(), minutes))
        } else if (hours > 0) {
            resources.getQuantityString(R.plurals.hours, hours.toInt(), hours)
        } else {
            resources.getQuantityString(R.plurals.minutes, minutes.toInt(), minutes)
        }
    }

    private fun notifyBlockAllEnabled(until: Long) =
        sendBroadcast(
            Intent(BlockAllTileService.ACTION_BLOCK_ALL_CHANGED).apply {
                setPackage(packageName)
                putExtra(BlockAllTileService.EXTRA_VALUE, true)
                putExtra(BlockAllTileService.EXTRA_UNTIL, until)
            }
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (settingsManager.blockAll) {
            finish()
            return
        }

        val values = resources.getIntArray(R.array.predefined_block_all_timeouts)
        val labels = values.map { getIntervalString(it) }

        setContent {
            var selectedIndex by remember { mutableIntStateOf(-1) }

            CallsBlockerTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.block_all_title)) },
                    text = {
                        Column {
                            values.forEachIndexed { index, value ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIndex = index
                                            val until = if (values[index] == 0) Long.MAX_VALUE else System.currentTimeMillis() + values[index] * DateUtils.SECOND_IN_MILLIS
                                            settingsManager.temporaryBlockAll(until)
                                            val text = if (values[index] == 0) getString(R.string.block_all_enabled) else getString(R.string.block_all_enabled_for, labels[index])
                                            Toast.makeText(this@BlockAllTimePickerActivity, text, Toast.LENGTH_LONG).show()
                                            notifyBlockAllEnabled(until)
                                            finish()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedIndex == index,
                                        onClick = null
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(labels[index])
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }
}
