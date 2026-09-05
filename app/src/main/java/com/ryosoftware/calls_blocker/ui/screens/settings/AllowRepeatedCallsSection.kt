package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import kotlin.math.roundToInt

@Composable
fun AllowRepeatedCallsSection(
    allowRepeated: Boolean,
    onAllowRepeatedChange: (Boolean) -> Unit,
    allowRepeatedCallCount: Int,
    onAllowRepeatedCallCountChange: (Int) -> Unit,
    onAllowRepeatedCallCountChangeFinished: () -> Unit,
    allowRepeatedWindowMinutes: Int,
    onAllowRepeatedWindowMinutesChange: (Int) -> Unit,
    onAllowRepeatedWindowMinutesChangeFinished: () -> Unit,
    callLogPermissionGranted: Boolean,
    onRequestCallLogPermission: () -> Unit,
) {
    AllowPermissionCard(
        canShowPermissionNotAllowed = allowRepeated,
        permission = Manifest.permission.READ_CALL_LOG,
        isPermissionAllowed = callLogPermissionGranted,
        onShowRationaleRequested = onRequestCallLogPermission
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAllowRepeatedChange(!allowRepeated) },
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.allow_repeated_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.allow_repeated_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = allowRepeated,
                onCheckedChange = null
            )
        }
        if (allowRepeated) {
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.allow_repeated_call_count_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = allowRepeatedCallCount.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = allowRepeatedCallCount.toFloat(),
                onValueChange = { onAllowRepeatedCallCountChange(it.roundToInt()) },
                onValueChangeFinished = onAllowRepeatedCallCountChangeFinished,
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
                    text = stringResource(R.string.allow_repeated_call_window_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.value_in_minutes, allowRepeatedWindowMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp)
                )
            }

            Slider(
                value = allowRepeatedWindowMinutes.toFloat(),
                onValueChange = { onAllowRepeatedWindowMinutesChange(it.roundToInt()) },
                onValueChangeFinished = onAllowRepeatedWindowMinutesChangeFinished,
                valueRange = 1f..30f,
                steps = 28,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}