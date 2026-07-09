package com.ryosoftware.calls_blocker.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.ui.rememberContactInfo
import com.ryosoftware.calls_blocker.data.db.Direction
import com.ryosoftware.calls_blocker.data.findCountryByPhoneNumber
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
import androidx.core.net.toUri
import com.ryosoftware.calls_blocker.data.db.NumberType.Companion.toString
import com.ryosoftware.calls_blocker.data.db.Reason.Companion.toString

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
    @SuppressLint("LocalContextResourcesRead")
    val monthNames = stringArrayResource(R.array.month_names)
    val history by viewModel.history.collectAsState()
    val blockedPhoneNumbers by viewModel.blockedPhoneNumbers.collectAsState()
    val allowedPhoneNumbers by viewModel.allowedPhoneNumbers.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var entryToDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(true) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var blockHidden by remember { mutableStateOf(viewModel.blockHidden) }
    var showBlockHiddenConfirm by remember { mutableStateOf(false) }
    var showUnblockHiddenConfirm by remember { mutableStateOf(false) }

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

    BackHandler(enabled = expandedEntryId != null) {
        expandedEntryId = null
    }

    BackHandler(enabled = multiSelect) {
        selectedIds = emptySet()
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
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            is HistoryListItem.Entry -> HistoryItem(
                                context = LocalContext.current,
                                entry = item.entry,
                                viewModel = viewModel,
                                isBlocked = item.entry.phoneNumber in blockedPhoneNumbers,
                                isAllowed = item.entry.phoneNumber in allowedPhoneNumbers,
                                isSelected = item.entry.id in selectedIds,
                                multiSelect = multiSelect,
                                expanded = expandedEntryId == item.entry.id,
                                blockHidden = blockHidden,
                                onBlockHidden = { showBlockHiddenConfirm = true },
                                onUnblockHidden = { showUnblockHiddenConfirm = true },
                                onLongClick = { toggleSelection(item.entry.id) },
                                onClick = {
                                    if (multiSelect) {
                                        toggleSelection(item.entry.id)
                                    } else {
                                        expandedEntryId = if (expandedEntryId == item.entry.id) null else item.entry.id
                                    }
                                },
                                onBlock = { pendingAction = item.entry.phoneNumber to "block" },
                                onUnblock = { pendingAction = item.entry.phoneNumber to "unblock" },
                                onAllow = { pendingAction = item.entry.phoneNumber to "allow" },
                                onUnallow = { pendingAction = item.entry.phoneNumber to "unallow" },
                                onDelete = { entryToDelete = item.entry }
                            )
                        }
                    }
            }
        }
    }

    if (showDeleteConfirm) {
        val selectedEntries = remember(selectedIds, filteredHistory) {
            filteredHistory.filter { it.id in selectedIds }
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_history_entries_title)) },
            text = {
                val displayEntries = selectedEntries.take(10)
                val remaining = selectedEntries.size - displayEntries.size

                Text(
                    stringResource(
                        if (remaining > 0) R.string.delete_numbers_and_x_more else R.string.delete_numbers,
                        displayEntries.joinToString("\n") { PhoneUtils.formatPhoneNumber(it.phoneNumber) },
                        remaining
                    )
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

    pendingAction?.let { (phoneNumber, action) ->
        val isBlockAction = action == "block"
        val isUnblockAction = action == "unblock"
        val isAllowAction = action == "allow"
        val isUnallowAction = action == "unallow"
        
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    when {
                        isBlockAction -> stringResource(R.string.action_block)
                        isUnblockAction -> stringResource(R.string.action_unblock)
                        isAllowAction -> stringResource(R.string.action_allow)
                        isUnallowAction -> stringResource(R.string.action_unallow)
                        else -> ""
                    }
                )
            },
            text = {
                Text(
                    when {
                        isBlockAction -> stringResource(R.string.confirm_block_number, PhoneUtils.formatPhoneNumber(phoneNumber))
                        isUnblockAction -> stringResource(R.string.confirm_unblock_number, PhoneUtils.formatPhoneNumber(phoneNumber))
                        isAllowAction -> stringResource(R.string.confirm_allow_number, PhoneUtils.formatPhoneNumber(phoneNumber))
                        isUnallowAction -> stringResource(R.string.confirm_unallow_number, PhoneUtils.formatPhoneNumber(phoneNumber))
                        else -> ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        isBlockAction -> viewModel.blockNumber(phoneNumber)
                        isUnblockAction -> viewModel.unblockNumber(phoneNumber)
                        isAllowAction -> viewModel.allowNumber(phoneNumber)
                        isUnallowAction -> viewModel.unallowNumber(phoneNumber)
                    }
                    pendingAction = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    entryToDelete?.let { entry ->
        var alsoRemoveFromNumbers by remember(entryToDelete) { mutableStateOf(false) }
        var deleteAllForNumber by remember(entryToDelete) { mutableStateOf(false) }

        val sameNumberCount = history.count { it.phoneNumber == entry.phoneNumber }

        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(PhoneUtils.formatPhoneNumber(entry.phoneNumber)) },
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

    if (showBlockHiddenConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockHiddenConfirm = false },
            title = { Text(stringResource(R.string.block_hidden_title)) },
            text = { Text(stringResource(R.string.block_hidden_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.blockHidden = true
                    blockHidden = true
                    showBlockHiddenConfirm = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockHiddenConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showUnblockHiddenConfirm) {
        AlertDialog(
            onDismissRequest = { showUnblockHiddenConfirm = false },
            title = { Text(stringResource(R.string.unblock_hidden_title)) },
            text = { Text(stringResource(R.string.unblock_hidden_description)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.blockHidden = false
                    blockHidden = false
                    showUnblockHiddenConfirm = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnblockHiddenConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    context: Context,
    entry: HistoryEntry,
    viewModel: HistoryViewModel,
    isBlocked: Boolean,
    isAllowed: Boolean,
    isSelected: Boolean,
    multiSelect: Boolean,
    expanded: Boolean,
    blockHidden: Boolean,
    onBlockHidden: () -> Unit,
    onUnblockHidden: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onAllow: () -> Unit,
    onUnallow: () -> Unit,
    onDelete: () -> Unit
) {
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
    val numberTypeString = remember(entry.type) {
        entry.type.toString(context)
    }

    val contactInfo = rememberContactInfo(entry.phoneNumber, context)

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
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (multiSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onLongClick() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Icon(
                    imageVector = when (entry.direction) {
                        Direction.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                        Direction.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    },
                    contentDescription = null,
                    tint = when (entry.reason) {
                        Reason.BLOCK_ALL,
                        Reason.HIDDEN_NUMBER,
                        Reason.BLACKLISTED_NUMBER,
                        Reason.BLACKLISTED_PREFIX,
                        Reason.NOT_A_CONTACT,
                        Reason.MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS,
                        Reason.INTERNATIONAL_NUMBER,
                        Reason.NOT_CALLED,
                        Reason.REJECTED_BEFORE,
                        Reason.REPEATED_CALL,
                        Reason.SCHEDULE,
                        Reason.FIND_MY_PHONE,
                        Reason.FIND_MY_PHONE_CANCELLED -> colorResource(R.color.status_inactive_text)
                        Reason.WHITELISTED_NUMBER,
                        Reason.WHITELISTED_PREFIX -> colorResource(R.color.status_active_text)
                        Reason.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = entry.phoneNumber.takeIf { it.isNotEmpty() }?.let { PhoneUtils.formatPhoneNumber(it) } ?: stringResource(R.string.hidden_number),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = when (entry.reason) {
                            Reason.BLOCK_ALL,
                            Reason.HIDDEN_NUMBER,
                            Reason.BLACKLISTED_NUMBER,
                            Reason.BLACKLISTED_PREFIX,
                            Reason.NOT_A_CONTACT,
                            Reason.MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS,
                            Reason.INTERNATIONAL_NUMBER,
                            Reason.NOT_CALLED,
                            Reason.REJECTED_BEFORE,
                            Reason.REPEATED_CALL,
                            Reason.SCHEDULE,
                            Reason.FIND_MY_PHONE,
                            Reason.FIND_MY_PHONE_CANCELLED -> colorResource(R.color.status_inactive_text)
                            Reason.WHITELISTED_NUMBER,
                            Reason.WHITELISTED_PREFIX -> colorResource(R.color.status_active_text)
                            Reason.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (countryInfo != null) {
                        val (country, _) = countryInfo

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = if (numberTypeString != null) stringResource(R.string.country_name_and_flag_and_number_type, viewModel.getCountryName(country), country.flag, numberTypeString)
                                   else stringResource(R.string.country_name_and_flag, viewModel.getCountryName(country), country.flag),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (numberTypeString != null) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = numberTypeString,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (!contactInfo.name.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = contactInfo.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (entry.reason != Reason.NONE) {
                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.reason, entry.reason.toString(context)!!),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = dateFormat.format(Date(entry.timeStamp)),
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

            if (!multiSelect && expanded) {
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    if (entry.phoneNumber.isNotEmpty()) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = "tel:${entry.phoneNumber}".toUri()
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = stringResource(R.string.call),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("phone", entry.phoneNumber)
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isBlocked) {
                            IconButton(onClick = onUnblock) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = stringResource(R.string.action_unblock),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (!isAllowed) {
                            IconButton(onClick = onBlock) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = stringResource(R.string.action_block),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isAllowed) {
                            IconButton(onClick = onUnallow) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = stringResource(R.string.action_unallow),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (!isBlocked) {
                            IconButton(onClick = onAllow) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = stringResource(R.string.action_allow),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (entry.phoneNumber.isEmpty() && !blockHidden) {
                        IconButton(onClick = onBlockHidden) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = stringResource(R.string.block_hidden_title),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (entry.phoneNumber.isEmpty() && blockHidden) {
                        IconButton(onClick = onUnblockHidden) {
                            Icon(
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = stringResource(R.string.action_unblock),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

