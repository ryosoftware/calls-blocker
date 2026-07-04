package com.ryosoftware.calls_blocker.ui.screens

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
import com.ryosoftware.calls_blocker.data.formatPhoneNumber
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

private sealed interface HistoryHeader {
    data object Today : HistoryHeader
    data object Yesterday : HistoryHeader
    data object ThisWeek : HistoryHeader
    data object ThisMonth : HistoryHeader
    data class Month(val year: Int, val month: Int) : HistoryHeader
}

private sealed interface HistoryListItem {
    data class Header(val header: HistoryHeader, val count: Int) : HistoryListItem
    data class Entry(val entry: HistoryEntry) : HistoryListItem
}

private fun getHistoryHeader(timestamp: Long, today: LocalDate): HistoryHeader {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    if (date == today) return HistoryHeader.Today
    if (date == today.minusDays(1)) return HistoryHeader.Yesterday

    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val startOfWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val endOfWeek = startOfWeek.plusDays(6)

    if (date in startOfWeek..endOfWeek) return HistoryHeader.ThisWeek

    if (date.year == today.year && date.month == today.month) return HistoryHeader.ThisMonth
    
    return HistoryHeader.Month(date.year, date.monthValue)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onMultiSelect: (count: Int, allSelected: Boolean, onClose: () -> Unit, onSelectAll: () -> Unit, onDelete: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    @SuppressLint("LocalContextResourcesRead")
    val monthNames = stringArrayResource(R.array.month_names)
    val history by viewModel.history.collectAsState()
    val blockedPhoneNumbers by viewModel.blockedPhoneNumbers.collectAsState()
    val allowedPhoneNumbers by viewModel.allowedPhoneNumbers.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var entryToDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(true) }

    val today = remember { LocalDate.now() }
    var collapsedHeaders by remember { mutableStateOf(setOf<HistoryHeader>()) }

    val filteredHistory = remember(searchQuery, history) {
        if (searchQuery.isBlank()) history
        else history.filter { it.phoneNumber.contains(searchQuery) }
    }

    val multiSelect = selectedIds.isNotEmpty()
    val allSelected = multiSelect && selectedIds.size == filteredHistory.size

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    LaunchedEffect(selectedIds, filteredHistory.size) {
        if (multiSelect) {
            onMultiSelect(
                selectedIds.size,
                allSelected,
                { selectedIds = emptySet() },
                { selectedIds = filteredHistory.map { it.id }.toSet() },
                { showDeleteConfirm = true }
            )
        } else {
            onMultiSelect(0, false, {}, {}, {})
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_numbers_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else if (searchVisible && searchQuery.isNotBlank() && filteredHistory.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                val groupedItems = buildList {
                        val byHeader = filteredHistory.groupBy { getHistoryHeader(it.timeStamp, today) }

                        fun addGroup(header: HistoryHeader) {
                            val entries = byHeader[header] ?: return
                            add(HistoryListItem.Header(header, entries.size))
                            if (header !in collapsedHeaders) {
                                entries.forEach { add(HistoryListItem.Entry(it)) }
                            }
                        }

                        addGroup(HistoryHeader.Today)
                        addGroup(HistoryHeader.Yesterday)
                        addGroup(HistoryHeader.ThisWeek)
                        addGroup(HistoryHeader.ThisMonth)

                        byHeader.keys
                            .filterIsInstance<HistoryHeader.Month>()
                            .sortedByDescending { it.year * 100 + it.month }
                            .forEach { addGroup(it) }
                    }

                    items(groupedItems, key = {
                        when (it) {
                            is HistoryListItem.Header -> when (val h = it.header) {
                                is HistoryHeader.Today -> "header_today"
                                is HistoryHeader.Yesterday -> "header_yesterday"
                                is HistoryHeader.ThisWeek -> "header_this_week"
                                is HistoryHeader.ThisMonth -> "header_this_month"
                                is HistoryHeader.Month -> "header_month_${h.year}_${h.month}"
                            }
                            is HistoryListItem.Entry -> it.entry.id
                        }
                    }) { item ->
                        when (item) {
                            is HistoryListItem.Header -> {
                                val collapsed = item.header in collapsedHeaders
                                val callsBlockedString = pluralStringResource(R.plurals.calls, item.count, item.count)
                                val text = when (val history = item.header) {
                                    HistoryHeader.Today -> stringResource(R.string.history_header_today, callsBlockedString)
                                    HistoryHeader.Yesterday -> stringResource(R.string.history_header_yesterday, callsBlockedString)
                                    HistoryHeader.ThisWeek -> stringResource(R.string.history_header_this_week, callsBlockedString)
                                    HistoryHeader.ThisMonth -> stringResource(R.string.history_header_this_month, callsBlockedString)
                                    is HistoryHeader.Month -> {
                                        val monthName = remember(history.month) { monthNames[history.month - 1] }
                                        if (history.year == today.year) {
                                            stringResource(R.string.history_header_month_without_year, callsBlockedString, monthName)
                                        } else {
                                            stringResource(R.string.history_header_month_with_year, callsBlockedString, monthName, history.year)
                                        }

                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            collapsedHeaders = if (collapsed) {
                                                collapsedHeaders - item.header
                                            } else {
                                                collapsedHeaders + item.header
                                            }
                                        }
                                        .padding(top = 16.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            is HistoryListItem.Entry -> HistoryItem(
                                entry = item.entry,
                                viewModel = viewModel,
                                isSelected = item.entry.id in selectedIds,
                                multiSelect = multiSelect,
                                onLongClick = { toggleSelection(item.entry.id) },
                                onClick = {
                                    if (multiSelect) toggleSelection(item.entry.id)
                                },
                                onDelete = { entryToDelete = item.entry }
                            )
                        }
                    }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_history_entries_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_history_entries_message, selectedIds.size)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    showDeleteConfirm = false
                    viewModel.removeEntries(ids)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (isDeleting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.deleting_title)) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.deleting_message))
                }
            },
            confirmButton = {}
        )
    }

    entryToDelete?.let { entry ->
        var alsoRemoveFromNumbers by remember(entryToDelete) { mutableStateOf(false) }
        var deleteAllForNumber by remember(entryToDelete) { mutableStateOf(false) }

        val sameNumberCount = history.count { it.phoneNumber == entry.phoneNumber }

        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.delete_history_entry_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.delete_history_entry_message))

                    val checkboxText = when {
                        entry.phoneNumber in blockedPhoneNumbers -> stringResource(R.string.also_unblock)
                        entry.phoneNumber in allowedPhoneNumbers -> stringResource(R.string.also_unallow)
                        else -> null

                    }
                    if (checkboxText != null) {
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alsoRemoveFromNumbers,
                                onCheckedChange = { alsoRemoveFromNumbers = it }
                            )

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = checkboxText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (sameNumberCount > 1) {
                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteAllForNumber,
                                onCheckedChange = { deleteAllForNumber = it }
                            )

                            Spacer(Modifier.width(4.dp))

                            Text(
                                text = stringResource(R.string.delete_all_entries_for_number, sameNumberCount),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeEntry(entry, deleteAllForNumber, alsoRemoveFromNumbers)
                    entryToDelete = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    viewModel: HistoryViewModel,
    isSelected: Boolean,
    multiSelect: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            Locale.getDefault()
        )
    }
    val countryInfo = remember(entry.phoneNumber) {
        findCountryByPhoneNumber(entry.phoneNumber)
    }

    Card(
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        },

        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelect) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onLongClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.phoneNumber.takeIf { it.isNotEmpty() }?.let { formatPhoneNumber(it) } ?: stringResource(R.string.private_number),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                if (countryInfo != null) {
                    val (country, _) = countryInfo

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.number_from_country_name_and_flag, viewModel.getCountryName(country), country.flag),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                val callAllowed = when(entry.reason) {
                    Reason.REASON_WHITELISTED_NUMBER,
                    Reason.REASON_WHITELISTED_PREFIX,
                    Reason.REASON_NONE -> true
                    else -> false
                }

                if (entry.reason != Reason.REASON_NONE) {
                    Text(
                        text = stringResource(R.string.reason, HistoryViewModel.getReasonString(context, entry.reason)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (callAllowed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = dateFormat.format(Date(entry.timeStamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!multiSelect) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }
    }
}
