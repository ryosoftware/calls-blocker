package com.ryosoftware.calls_blocker.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
import com.ryosoftware.calls_blocker.ui.rememberContactInfo
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

data class CallLogEntry(
    val phoneNumber: String,
    val callType: Int,
    val duration: Int,
    val timestamp: Long,
)

private sealed interface CallLogHeader {
    data object Today : CallLogHeader
    data object Yesterday : CallLogHeader
    data object ThisWeek : CallLogHeader
    data object ThisMonth : CallLogHeader
    data class Month(val year: Int, val month: Int) : CallLogHeader
}

private sealed interface CallLogListItem {
    data class Header(val header: CallLogHeader, val count: Int) : CallLogListItem
    data class Entry(val entry: CallLogEntry) : CallLogListItem
}

private fun getCallLogHeader(timestamp: Long, today: LocalDate): CallLogHeader {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    if (date == today) return CallLogHeader.Today
    if (date == today.minusDays(1)) return CallLogHeader.Yesterday

    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val endOfWeek = startOfWeek.plusDays(6)

    if (date in startOfWeek..endOfWeek) return CallLogHeader.ThisWeek

    if (date.year == today.year && date.month == today.month) return CallLogHeader.ThisMonth

    return CallLogHeader.Month(date.year, date.monthValue)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogPickerDialog(
    countryNameProvider: CountryNameProvider,
    blockedNumbers: Set<String>,
    allowedNumbers: Set<String>,
    onMultiSelect: (List<CallLogEntry>, String) -> Unit,  // action: "block" or "allow"
    onDismiss: () -> Unit,
    onRequestCallLogPermission: () -> Unit
) {
    val context = LocalContext.current
    val monthNames = stringArrayResource(R.array.month_names)
    val today = remember { LocalDate.now() }
    var search by remember { mutableStateOf("") }
    var callLogEntries by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    val hasCallLogPermission = remember { context.hasReadCallLogPermission() }

    @SuppressLint("MutableCollectionMutableState")
    var selectedPhoneNumbers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    val selectedEntries = remember(selectedPhoneNumbers, callLogEntries) {
        callLogEntries.filter { it.phoneNumber in selectedPhoneNumbers }
    }

    val rejectedBlockedEntries = remember(callLogEntries) {
        callLogEntries.filter { it.callType == CallLog.Calls.REJECTED_TYPE || it.callType == CallLog.Calls.BLOCKED_TYPE }
    }

    val rejectedBlockedPhoneNumbers = remember(rejectedBlockedEntries) {
        rejectedBlockedEntries.map { it.phoneNumber }.toSet()
    }

    val canSelectRejectedBlocked = rejectedBlockedPhoneNumbers.isNotEmpty()

    LaunchedEffect(hasCallLogPermission) {
        if (hasCallLogPermission) {
            callLogEntries = queryCallLog(context)
        }
    }

    val filtered = remember(search, callLogEntries) {
        if (search.isBlank()) callLogEntries
        else {
            val q = search.lowercase()
            callLogEntries.filter { it.phoneNumber.lowercase().contains(q) }
        }
    }

    val groupedItems = remember(filtered, today) {
        buildList {
            val byHeader = filtered.groupBy { getCallLogHeader(it.timestamp, today) }

            fun addGroup(header: CallLogHeader) {
                val entries = byHeader[header] ?: return
                add(CallLogListItem.Header(header, entries.size))
                entries.forEach { add(CallLogListItem.Entry(it)) }
            }

            addGroup(CallLogHeader.Today)
            addGroup(CallLogHeader.Yesterday)
            addGroup(CallLogHeader.ThisWeek)
            addGroup(CallLogHeader.ThisMonth)

            byHeader.keys
                .filterIsInstance<CallLogHeader.Month>()
                .sortedByDescending { it.year * 100 + it.month }
                .forEach { addGroup(it) }
        }
    }

    fun togglePhoneNumber(phoneNumber: String) {
        selectedPhoneNumbers = if (phoneNumber in selectedPhoneNumbers) {
            selectedPhoneNumbers - phoneNumber
        } else {
            selectedPhoneNumbers + phoneNumber
        }
    }

    fun selectAllRejectedBlocked() {
        selectedPhoneNumbers = rejectedBlockedPhoneNumbers
    }

    fun clearSelection() {
        selectedPhoneNumbers = emptySet()
    }

    fun onConfirmAction(action: String) {
        pendingAction = action
        showConfirmDialog = true
    }

    fun executeAction() {
        val entriesToProcess = selectedEntries.toList()
        if (entriesToProcess.isNotEmpty() && pendingAction != null) {
            onMultiSelect(entriesToProcess, pendingAction!!)
        }
        clearSelection()
        pendingAction = null
        showConfirmDialog = false
    }

    BackHandler(enabled = selectedPhoneNumbers.isNotEmpty()) {
        clearSelection()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (selectedPhoneNumbers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.numbers_selected, selectedPhoneNumbers.size, selectedPhoneNumbers.size, pluralStringResource(R.plurals.calls, selectedEntries.size, selectedEntries.size)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = { onConfirmAction("block") }) {
                        Text(stringResource(R.string.action_block))
                    }

                    TextButton(onClick = { onConfirmAction("allow") }) {
                        Text(stringResource(R.string.action_allow))
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            if (!hasCallLogPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.call_log_permission_required),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = onRequestCallLogPermission) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            } else {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = groupedItems,
                        key = {
                            when (it) {
                                is CallLogListItem.Header -> when (val h = it.header) {
                                    is CallLogHeader.Today -> "header_today"
                                    is CallLogHeader.Yesterday -> "header_yesterday"
                                    is CallLogHeader.ThisWeek -> "header_this_week"
                                    is CallLogHeader.ThisMonth -> "header_this_month"
                                    is CallLogHeader.Month -> "header_month_${h.year}_${h.month}"
                                }
                                is CallLogListItem.Entry -> "entry_${it.entry.timestamp}_${it.entry.phoneNumber}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is CallLogListItem.Header -> {
                                val callsString = pluralStringResource(R.plurals.calls, item.count, item.count)
                                val text = when (val h = item.header) {
                                    CallLogHeader.Today -> stringResource(R.string.history_header_today, callsString)
                                    CallLogHeader.Yesterday -> stringResource(R.string.history_header_yesterday, callsString)
                                    CallLogHeader.ThisWeek -> stringResource(R.string.history_header_this_week, callsString)
                                    CallLogHeader.ThisMonth -> stringResource(R.string.history_header_this_month, callsString)
                                    is CallLogHeader.Month -> {
                                        val monthName = remember(h.month) { monthNames[h.month - 1] }
                                        if (h.year == today.year) {
                                            stringResource(R.string.history_header_month_without_year, callsString, monthName)
                                        } else {
                                            stringResource(R.string.history_header_month_with_year, callsString, monthName, h.year)
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
                                ) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            is CallLogListItem.Entry -> CallLogRow(
                                entry = item.entry,
                                countryNameProvider = countryNameProvider,
                                isBlocked = item.entry.phoneNumber in blockedNumbers,
                                isAllowed = item.entry.phoneNumber in allowedNumbers,
                                isSelected = item.entry.phoneNumber in selectedPhoneNumbers,
                                onClick = {
                                    val isExisting = item.entry.phoneNumber in blockedNumbers || item.entry.phoneNumber in allowedNumbers
                                    if (!isExisting) {
                                        togglePhoneNumber(item.entry.phoneNumber)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        val action = pendingAction ?: ""
        val isBlockAction = action == "block"
        val actionTitle = if (isBlockAction) stringResource(R.string.action_block) else stringResource(R.string.action_allow)
        val selectedNumbersList = remember(selectedPhoneNumbers) { selectedPhoneNumbers.toList() }
        val displayNumbers = selectedNumbersList.take(10).joinToString("\n") { PhoneUtils.formatPhoneNumber(it) }
        val remaining = if (selectedNumbersList.size > 10) "\n... y ${selectedNumbersList.size - 10} más" else ""
        val confirmText = if (isBlockAction)
            stringResource(R.string.confirm_block_multiple_numbers_detail, displayNumbers, remaining)
        else
            stringResource(R.string.confirm_allow_multiple_numbers_detail, displayNumbers, remaining)

        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingAction = null
            },
            title = { Text(actionTitle) },
            text = { Text(confirmText) },
            confirmButton = {
                TextButton(onClick = { executeAction() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    pendingAction = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CallLogRow(
    entry: CallLogEntry,
    countryNameProvider: CountryNameProvider,
    isBlocked: Boolean,
    isAllowed: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val contactInfo = rememberContactInfo(entry.phoneNumber, context)
    val countryInfo = remember(entry.phoneNumber) {
        findCountryByPhoneNumber(entry.phoneNumber)
    }
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    }

    val isExisting = isBlocked || isAllowed

    val iconColor = when {
        entry.callType == CallLog.Calls.REJECTED_TYPE ||
        entry.callType == CallLog.Calls.BLOCKED_TYPE ||
        entry.callType == CallLog.Calls.MISSED_TYPE -> colorResource(R.color.blocked_call)
        entry.duration == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> colorResource(R.color.allowed_call)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isExisting) Modifier else Modifier.clickable(onClick = onClick))
            .alpha(if (isExisting) 0.4f else 1f)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = isSelected,
            enabled = !isExisting,
            onCheckedChange = { _ -> onClick() },
            modifier = Modifier.padding(end = 8.dp)
        )

        Icon(
            imageVector = when (entry.callType) {
                CallLog.Calls.INCOMING_TYPE,
                CallLog.Calls.REJECTED_TYPE,
                CallLog.Calls.BLOCKED_TYPE -> Icons.AutoMirrored.Filled.CallReceived
                CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade
                else -> Icons.AutoMirrored.Filled.PhoneMissed
            },
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            if (!contactInfo.name.isNullOrBlank()) {
                Text(
                    text = contactInfo.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else iconColor
                )

                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = PhoneUtils.formatPhoneNumber(entry.phoneNumber),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else iconColor
            )

            if (countryInfo != null) {
                val (country, _) = countryInfo

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.country_name_and_flag, countryNameProvider.get(country), country.flag),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if ((entry.callType == CallLog.Calls.REJECTED_TYPE) || (entry.callType == CallLog.Calls.BLOCKED_TYPE)) {
                Spacer(Modifier.height(8.dp))

                val resource = if (entry.callType == CallLog.Calls.REJECTED_TYPE) R.string.rejected_call else R.string.blocked_call

                Text(
                    text = stringResource(resource),
                    style = MaterialTheme.typography.bodySmall,
                    color = iconColor
                )
            }

            if (isExisting) {
                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(if (isBlocked) R.string.label_blocked else R.string.label_allowed),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) colorResource(R.color.blocked_call) else colorResource(R.color.allowed_call),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            val date = dateFormat.format(Date(entry.timestamp))

            Text(
                text = if (entry.duration > 0) stringResource(R.string.call_date_and_duration, date, getStringTimeFromInterval(context, entry.duration))
                       else date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        contactInfo.photo?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun queryCallLog(context: Context): List<CallLogEntry> {
    val entries = mutableListOf<CallLogEntry>()

    val projection = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.DURATION,
        CallLog.Calls.DATE,
    )

    val sortOrder = "${CallLog.Calls.DATE} DESC"

    context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)

        while (cursor.moveToNext()) {
            entries.add(
                CallLogEntry(
                    phoneNumber = cursor.getString(numberIndex) ?: "",
                    callType = cursor.getInt(typeIndex),
                    duration = cursor.getInt(durationIndex),
                    timestamp = cursor.getLong(dateIndex),
                )
            )
        }
    }

    return entries
}

fun getStringTimeFromInterval(context: Context, minutes: Int): String {
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    val remainingMinutes = minutes % 60

    return if (days > 0) {
        context.getString(R.string.days_and_hours_and_minutes, days, hours, remainingMinutes)
    } else if (hours > 0) {
        context.getString(R.string.hours_and_minutes, hours, remainingMinutes)
    } else {
        context.getString(R.string.minutes, remainingMinutes)
    }
}