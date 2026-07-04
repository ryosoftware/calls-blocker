package com.ryosoftware.calls_blocker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ScheduleRuleDialog(
    initialRule: ScheduleRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleRule) -> Unit
) {
    val isEditing = initialRule != null
    val weekDays = stringArrayResource(R.array.week_day_names)
    var startDay by remember { mutableIntStateOf(initialRule?.startDay ?: 1) }
    var startHour by remember { mutableIntStateOf(initialRule?.let { it.startMinute / 60 } ?: 22) }
    var startMinute by remember { mutableIntStateOf(initialRule?.let { it.startMinute % 60 } ?: 0) }
    var endDay by remember { mutableIntStateOf(initialRule?.endDay ?: 3) }
    var endHour by remember { mutableIntStateOf(initialRule?.let { it.endMinute / 60 } ?: 9) }
    var endMinute by remember { mutableIntStateOf(initialRule?.let { it.endMinute % 60 } ?: 0) }
    var showStartDayPicker by remember { mutableStateOf(false) }
    var showEndDayPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                if (isEditing) R.string.schedule_blocking_edit else R.string.schedule_blocking_add
            ))
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.schedule_blocking_start),
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showStartDayPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(weekDays[startDay - 1])
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val startTime = LocalTime.of(startHour, startMinute)
                    val formattedStartTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(startTime)
                    Text(formattedStartTime)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.schedule_blocking_end),
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showEndDayPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(weekDays[endDay - 1])
                    }
                }

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val endTime = LocalTime.of(endHour, endMinute)
                    val formattedEndTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(endTime)
                    Text(formattedEndTime)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ScheduleRule(
                            id = initialRule?.id ?: 0,
                            startDay = startDay,
                            startMinute = startHour * 60 + startMinute,
                            endDay = endDay,
                            endMinute = endHour * 60 + endMinute
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    if (showStartDayPicker) {
        DayPickerDialog(
            selectedDay = startDay,
            onDismiss = { showStartDayPicker = false },
            onSelect = { startDay = it; showStartDayPicker = false }
        )
    }
    if (showEndDayPicker) {
        DayPickerDialog(
            selectedDay = endDay,
            onDismiss = { showEndDayPicker = false },
            onSelect = { endDay = it; showEndDayPicker = false }
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m -> startHour = h; startMinute = m; showStartTimePicker = false },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m -> endHour = h; endMinute = m; showEndTimePicker = false },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_blocking_select_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DayPickerDialog(
    selectedDay: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val weekDays = stringArrayResource(R.array.week_day_names)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_blocking_select_day)) },
        text = {
            Column {
                weekDays.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index + 1) }
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedDay == index + 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
