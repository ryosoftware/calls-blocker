package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import kotlin.math.roundToInt

@Composable
fun CallLogRulesSection(
    blockNotCalled: Boolean,
    onBlockNotCalledChange: (Boolean) -> Unit,
    notCalledWindowDays: Int,
    onNotCalledWindowDaysChange: (Int) -> Unit,
    onNotCalledWindowDaysChangeFinished: () -> Unit,
    blockRejected: Boolean,
    onBlockRejectedChange: (Boolean) -> Unit,
    rejectedWindowDays: Int,
    onRejectedWindowDaysChange: (Int) -> Unit,
    onRejectedWindowDaysChangeFinished: () -> Unit,
    blockRepeated: Boolean,
    onBlockRepeatedChange: (Boolean) -> Unit,
    repeatedCallCount: Int,
    onRepeatedCallCountChange: (Int) -> Unit,
    onRepeatedCallCountChangeFinished: () -> Unit,
    repeatedCallWindowMinutes: Int,
    onRepeatedCallWindowMinutesChange: (Int) -> Unit,
    onRepeatedCallWindowMinutesChangeFinished: () -> Unit,
    callLogPermissionGranted: Boolean,
    onRequestCallLogPermission: () -> Unit,
) {
    AllowPermissionCard(
        canShowPermissionNotAllowed = blockNotCalled,
        permission = Manifest.permission.READ_CALL_LOG,
        isPermissionAllowed = callLogPermissionGranted,
        onShowRationaleRequested = onRequestCallLogPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockNotCalledChange(!blockNotCalled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.block_not_called_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.block_not_called_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = blockNotCalled,
                onCheckedChange = null
            )
        }
        if (blockNotCalled) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.not_called_window_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = pluralStringResource(R.plurals.days, notCalledWindowDays, notCalledWindowDays),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = notCalledWindowDays.toFloat(),
                onValueChange = { onNotCalledWindowDaysChange(it.roundToInt()) },
                onValueChangeFinished = onNotCalledWindowDaysChangeFinished,
                valueRange = 1f..7f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    AllowPermissionCard(
        canShowPermissionNotAllowed = blockRejected,
        permission = Manifest.permission.READ_CALL_LOG,
        isPermissionAllowed = callLogPermissionGranted,
        onShowRationaleRequested = onRequestCallLogPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockRejectedChange(!blockRejected) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.block_rejected_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.block_rejected_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = blockRejected,
                onCheckedChange = null
            )
        }
        if (blockRejected) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.rejected_window_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = pluralStringResource(R.plurals.days, rejectedWindowDays, rejectedWindowDays),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = rejectedWindowDays.toFloat(),
                onValueChange = { onRejectedWindowDaysChange(it.roundToInt()) },
                onValueChangeFinished = onRejectedWindowDaysChangeFinished,
                valueRange = 1f..7f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    AllowPermissionCard(
        canShowPermissionNotAllowed = blockRepeated,
        permission = Manifest.permission.READ_CALL_LOG,
        isPermissionAllowed = callLogPermissionGranted,
        onShowRationaleRequested = onRequestCallLogPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBlockRepeatedChange(!blockRepeated) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.block_repeated_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.block_repeated_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = blockRepeated,
                onCheckedChange = null
            )
        }
        if (blockRepeated) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.repeated_call_count_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = repeatedCallCount.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = repeatedCallCount.toFloat(),
                onValueChange = { onRepeatedCallCountChange(it.roundToInt()) },
                onValueChangeFinished = onRepeatedCallCountChangeFinished,
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.repeated_call_window_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.value_in_minutes, repeatedCallWindowMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = repeatedCallWindowMinutes.toFloat(),
                onValueChange = { onRepeatedCallWindowMinutesChange(it.roundToInt()) },
                onValueChangeFinished = onRepeatedCallWindowMinutesChangeFinished,
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
