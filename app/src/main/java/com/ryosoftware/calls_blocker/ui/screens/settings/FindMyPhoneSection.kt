package com.ryosoftware.calls_blocker.ui.screens.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.VibrationEffect
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.ryosoftware.calls_blocker.Main.Companion.getVibrator
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.Country
import com.ryosoftware.calls_blocker.data.toVibrationPattern
import com.ryosoftware.calls_blocker.ui.screens.AddNumberDialog
import kotlin.math.roundToInt

private data class PatternEntry(
    val nameResId: Int,
    val arrayResId: Int,
)

private val patternEntries = listOf(
    PatternEntry(R.string.find_my_phone_vibration_aggressive, R.array.find_my_phone_vibration_aggressive),
    PatternEntry(R.string.find_my_phone_vibration_siren, R.array.find_my_phone_vibration_siren),
    PatternEntry(R.string.find_my_phone_vibration_hammer, R.array.find_my_phone_vibration_hammer),
    PatternEntry(R.string.find_my_phone_vibration_sos, R.array.find_my_phone_vibration_sos),
    PatternEntry(R.string.find_my_phone_vibration_continuous, R.array.find_my_phone_vibration_continuous),
    PatternEntry(R.string.find_my_phone_vibration_alarm, R.array.find_my_phone_vibration_alarm),
    PatternEntry(R.string.find_my_phone_vibration_double_knock, R.array.find_my_phone_vibration_double_knock),
)

private fun getPatternArrayCsv(context: Context, arrayResId: Int): String =
    context.resources.getIntArray(arrayResId).joinToString(",")

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
    findMyPhoneVibrationPattern: String,
    onFindMyPhoneVibrationPatternChange: (String) -> Unit,
    callLogPermissionGranted: Boolean,
    onTestFindMyPhone: () -> Unit = {},
    batteryOptimizationGranted: Boolean,
    onRequestCallLogPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    getCountryName: (Country) -> String,
    defaultCountryIso: String,
) {
    val context = LocalContext.current
    val hasVibrator = context.getVibrator().hasVibrator()
    var showAddDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showVibrationPickerDialog by remember { mutableStateOf(false) }

    @SuppressLint("LocalContextGetResourceValueCall")
    val currentVibrationPatternName = remember(context, findMyPhoneVibrationPattern) {
        patternEntries.firstOrNull { (_, arrayResId) ->
            getPatternArrayCsv(context, arrayResId) == findMyPhoneVibrationPattern
        }?.let { (nameResId) ->
            context.getString(nameResId)
        } ?: context.getString(R.string.find_my_phone_no_vibration_pattern_selected)
    }

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
            text = stringResource(R.string.find_my_phone_title),
            style = MaterialTheme.typography.bodyLarge
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
            verticalAlignment = Alignment.Top
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
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            val numbers = findMyPhonePhoneNumbers.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (numbers.isEmpty()) {
                Text(
                    text = stringResource(R.string.find_my_phone_no_numbers),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorResource(R.color.status_inactive_text)
                )

                Spacer(Modifier.height(8.dp))
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                numbers.forEach { number ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                end = 4.dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                        ) {
                            Text(
                                text = PhoneUtils.formatPhoneNumber(number),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    val updated = numbers.filter { it != number }.joinToString(",")
                                    onFindMyPhonePhoneNumbersChange(updated)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.find_my_phone_remove),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.find_my_phone_add),
                                modifier = Modifier.size(16.dp)
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

            Text(
                text = stringResource(R.string.find_my_phone_select_ringtone),
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        val currentUri = findMyPhoneRingtoneUri.ifEmpty { null }
                        if (currentUri != null) {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                currentUri.toUri()
                            )
                        }
                    }
                    ringtonePickerLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val ringtoneTitle = if (findMyPhoneRingtoneUri.isNotEmpty()) {
                    val uri = findMyPhoneRingtoneUri.toUri()
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    ringtone?.getTitle(context) ?: stringResource(R.string.find_my_phone_no_ringtone_selected)
                } else {
                    stringResource(R.string.find_my_phone_no_ringtone_selected)
                }
                Text(ringtoneTitle)
            }

            if (hasVibrator) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.find_my_phone_vibration_pattern),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { showVibrationPickerDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(currentVibrationPatternName)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.find_my_phone_background_permission_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Button(
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

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showTestDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.find_my_phone_test_button))
            }
        }
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text(stringResource(R.string.find_my_phone_test_title)) },
            text = { Text(stringResource(R.string.find_my_phone_test_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showTestDialog = false
                    onTestFindMyPhone()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showVibrationPickerDialog) {
        VibrationPatternPickerDialog(
            context = context,
            currentPattern = findMyPhoneVibrationPattern,
            onDismiss = { showVibrationPickerDialog = false },
            onConfirm = { csv ->
                onFindMyPhoneVibrationPatternChange(csv)
                showVibrationPickerDialog = false
            }
        )
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

@Composable
private fun VibrationPatternPickerDialog(
    context: Context,
    currentPattern: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val vibrator = context.getVibrator()
    var selectedCsv by remember { mutableStateOf(currentPattern) }

    DisposableEffect(Unit) {
        onDispose { vibrator.cancel() }
    }

    AlertDialog(
        onDismissRequest = {
            vibrator.cancel()
            onDismiss()
        },
        title = { Text(stringResource(R.string.find_my_phone_vibration_pattern)) },
        text = {
            Column {
                patternEntries.forEach { (nameResId, arrayResId) ->
                    val csv = getPatternArrayCsv(context, arrayResId)
                    val isSelected = (csv == selectedCsv)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCsv = csv
                                vibrator.apply {
                                    cancel()
                                    vibrate(
                                        VibrationEffect.createWaveform(
                                            csv.toVibrationPattern(),
                                            0
                                        )
                                    )
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(nameResId),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vibrator.cancel()
                onConfirm(selectedCsv)
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                vibrator.cancel()
                onDismiss()
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
