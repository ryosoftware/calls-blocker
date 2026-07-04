package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.ui.screens.AddNumberDialog
import kotlin.math.roundToInt

@Composable
fun FindMyPhoneSection(
    findMyPhoneEnabled: Boolean,
    onFindMyPhoneEnabledChange: (Boolean) -> Unit,
    findMyPhonePhoneNumbers: String,
    onFindMyPhonePhoneNumbersChange: (String) -> Unit,
    findMyPhoneCallCount: Int,
    onFindMyPhoneCallCountChange: (Int) -> Unit,
    onFindMyPhoneCallCountChangeFinished: () -> Unit,
    findMyPhoneWindowMinutes: Int,
    onFindMyPhoneWindowMinutesChange: (Int) -> Unit,
    onFindMyPhoneWindowMinutesChangeFinished: () -> Unit,
    findMyPhoneRingtoneUri: String,
    onFindMyPhoneRingtoneUriChange: (String) -> Unit,
    callLogPermissionGranted: Boolean,
    batteryOptimizationGranted: Boolean,
    onRequestCallLogPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    getCountryName: (Country) -> String,
    defaultCountryIso: String,
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val dataIntent = result.data
            if (dataIntent != null) {
                val uri = IntentCompat.getParcelableExtra(
                    dataIntent,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
                if (uri != null) {
                    onFindMyPhoneRingtoneUriChange(uri.toString())
                }
            }
        }
    }

    AllowPermissionCard(
        canShowPermissionNotAllowed = findMyPhoneEnabled,
        permissions = listOf(
            PermissionUiState(
                permission = Manifest.permission.READ_CALL_LOG,
                isPermissionAllowed = callLogPermissionGranted,
                onShowRationaleRequested = onRequestCallLogPermission
            ),
            PermissionUiState(
                permission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                isPermissionAllowed = batteryOptimizationGranted,
                onShowRationaleRequested = onRequestBatteryOptimization
            )
        ),
    ) {
        Text(
            text = stringResource(R.string.find_my_phone_activated_no_number),
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = stringResource(R.string.find_my_phone_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onFindMyPhoneEnabledChange(!findMyPhoneEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.find_my_phone_enabled_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(24.dp))

            Switch(
                checked = findMyPhoneEnabled,
                onCheckedChange = null
            )
        }

        if (findMyPhoneEnabled) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.find_my_phone_trusted_numbers),
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(stringResource(R.string.find_my_phone_add))
            }

            Spacer(Modifier.height(8.dp))

            val numbers = findMyPhonePhoneNumbers.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (numbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.find_my_phone_no_numbers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                numbers.forEach { number ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                val updated = numbers.filter { it != number }.joinToString(",")
                                onFindMyPhonePhoneNumbersChange(updated)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.find_my_phone_remove)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.find_my_phone_call_count_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = findMyPhoneCallCount.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp)
                )
            }

            Slider(
                value = findMyPhoneCallCount.toFloat(),
                onValueChange = { onFindMyPhoneCallCountChange(it.roundToInt()) },
                onValueChangeFinished = onFindMyPhoneCallCountChangeFinished,
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (findMyPhoneCallCount > 1) {
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.find_my_phone_window_label),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.value_in_minutes, findMyPhoneWindowMinutes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                }

                Slider(
                    value = findMyPhoneWindowMinutes.toFloat(),
                    onValueChange = { onFindMyPhoneWindowMinutesChange(it.roundToInt()) },
                    onValueChangeFinished = onFindMyPhoneWindowMinutesChangeFinished,
                    valueRange = 1f..3f,
                    steps = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        val currentUri = findMyPhoneRingtoneUri.ifEmpty { null }
                        if (currentUri != null) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri.toUri())
                        }
                    }
                    ringtonePickerLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val ringtoneTitle = if (findMyPhoneRingtoneUri.isNotEmpty()) {
                    val uri = findMyPhoneRingtoneUri.toUri()
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    ringtone?.getTitle(context) ?: stringResource(R.string.find_my_phone_ringtone)
                } else {
                    stringResource(R.string.find_my_phone_ringtone)
                }
                Text(ringtoneTitle)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.find_my_phone_background_permission_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
    }

    if (showAddDialog) {
        AddNumberDialog(
            title = stringResource(R.string.find_my_phone_add),
            getCountryName = getCountryName,
            defaultCountryIso = defaultCountryIso,
            showBlockType = false,
            showDescription = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { number, _, _, _ ->
                val currentNumbers = findMyPhonePhoneNumbers.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toMutableList()
                if (number !in currentNumbers) {
                    currentNumbers.add(number)
                    onFindMyPhonePhoneNumbersChange(currentNumbers.joinToString(","))
                }
                showAddDialog = false
            }
        )
    }
}
