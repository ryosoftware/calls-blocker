package com.ryosoftware.calls_blocker.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.ContactGroup
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.ui.screens.settings.BlockingRulesSection
import com.ryosoftware.calls_blocker.ui.screens.settings.CallLogRulesSection
import com.ryosoftware.calls_blocker.ui.screens.settings.ScheduleRulesSection
import com.ryosoftware.calls_blocker.viewmodel.SettingsViewModel

@Composable
fun CallBlockingRulesScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var blockAll by remember { mutableStateOf(viewModel.blockAll) }
    var blockHidden by remember { mutableStateOf(viewModel.blockHidden) }
    var blockUnknown by remember { mutableStateOf(viewModel.blockUnknown) }
    var blockInternational by remember { mutableStateOf(viewModel.blockInternational) }
    var allowedCountryIsos by remember { mutableStateOf(viewModel.allowedCountryIsos) }
    var blockGroups by remember { mutableStateOf(viewModel.blockGroups) }
    var blockedGroupIds by remember { mutableStateOf(viewModel.blockedGroupIds) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var blockRepeated by remember { mutableStateOf(viewModel.blockRepeated) }
    var repeatedCallCount by remember { mutableIntStateOf(viewModel.repeatedCallCount) }
    var repeatedCallWindowMinutes by remember { mutableIntStateOf(viewModel.repeatedCallWindowMinutes) }
    var blockNotCalled by remember { mutableStateOf(viewModel.blockNotCalled) }
    var notCalledWindowDays by remember { mutableIntStateOf(viewModel.notCalledWindowDays) }
    var blockRejected by remember { mutableStateOf(viewModel.blockRejected) }
    var rejectedWindowDays by remember { mutableIntStateOf(viewModel.rejectedWindowDays) }
    var hasRequestedContacts by remember { mutableStateOf(viewModel.contactsPermissionRequested) }
    var showReadContactsPermissionRationale by remember { mutableStateOf(false) }
    var hasRequestedCallsLog by remember { mutableStateOf(viewModel.callsLogPermissionRequested) }
    var showReadCallLogRationale by remember { mutableStateOf(false) }
    var pendingUnknownToggle by remember { mutableStateOf<Boolean?>(null) }
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    var pendingGroupsToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockRepeatedToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockNotCalledToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockRejectedToggle by remember { mutableStateOf<Boolean?>(null) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showScheduleRuleDialog by remember { mutableStateOf(false) }
    var editingScheduleRule by remember { mutableStateOf<ScheduleRule?>(null) }
    val scheduleRules by viewModel.scheduleRules.collectAsState()

    val contactsPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadContactsPermission()
    }

    val callLogPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadCallLogPermission()
    }

    val contactGroups by produceState(
        initialValue = emptyList<ContactGroup>(),
        key1 = permissionCheckTrigger,
    ) {
        value = viewModel.getContactGroups()
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingUnknownToggle != null) {
                blockUnknown = pendingUnknownToggle!!
                viewModel.blockUnknown = pendingUnknownToggle!!
            }
            if (pendingGroupsToggle != null) {
                blockGroups = pendingGroupsToggle!!
                viewModel.blockGroups = pendingGroupsToggle!!
            }
        }
        pendingUnknownToggle = null
        pendingGroupsToggle = null
        permissionCheckTrigger++
    }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingBlockRepeatedToggle != null) {
                blockRepeated = pendingBlockRepeatedToggle!!
                viewModel.blockRepeated = pendingBlockRepeatedToggle!!
            }
            if (pendingBlockNotCalledToggle != null) {
                blockNotCalled = pendingBlockNotCalledToggle!!
                viewModel.blockNotCalled = pendingBlockNotCalledToggle!!
            }
            if (pendingBlockRejectedToggle != null) {
                blockRejected = pendingBlockRejectedToggle!!
                viewModel.blockRejected = pendingBlockRejectedToggle!!
            }
        }
        pendingBlockRepeatedToggle = null
        pendingBlockNotCalledToggle = null
        pendingBlockRejectedToggle = null
        permissionCheckTrigger++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTrigger++
                val contactsGranted = context.hasReadContactsPermission()
                if (contactsGranted) {
                    if (pendingUnknownToggle != null) {
                        blockUnknown = pendingUnknownToggle!!
                        viewModel.blockUnknown = pendingUnknownToggle!!
                        pendingUnknownToggle = null
                    }
                    if (pendingGroupsToggle != null) {
                        blockGroups = pendingGroupsToggle!!
                        viewModel.blockGroups = pendingGroupsToggle!!
                        pendingGroupsToggle = null
                    }
                }
                val callLogGranted = context.hasReadCallLogPermission()
                if (callLogGranted && pendingBlockRepeatedToggle != null) {
                    blockRepeated = pendingBlockRepeatedToggle!!
                    viewModel.blockRepeated = pendingBlockRepeatedToggle!!
                    pendingBlockRepeatedToggle = null
                }
                if (callLogGranted && pendingBlockNotCalledToggle != null) {
                    blockNotCalled = pendingBlockNotCalledToggle!!
                    viewModel.blockNotCalled = pendingBlockNotCalledToggle!!
                    pendingBlockNotCalledToggle = null
                }
                if (callLogGranted && pendingBlockRejectedToggle != null) {
                    blockRejected = pendingBlockRejectedToggle!!
                    viewModel.blockRejected = pendingBlockRejectedToggle!!
                    pendingBlockRejectedToggle = null
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.blocking_rules_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        BlockingRulesSection(
            blockAll = blockAll,
            onBlockAllChange = {
                blockAll = it
                viewModel.blockAll = it
            },
            blockUnknown = blockUnknown,
            onBlockUnknownChange = { enabled ->
                if (enabled && !contactsPermissionGranted) {
                    pendingUnknownToggle = true
                    showReadContactsPermissionRationale = true
                } else {
                    pendingUnknownToggle = null
                    blockUnknown = enabled
                    viewModel.blockUnknown = enabled
                }
            },
            blockHidden = blockHidden,
            onBlockHiddenChange = {
                blockHidden = it
                viewModel.blockHidden = it
            },
            blockGroups = blockGroups,
            onBlockGroupsChange = { enabled ->
                if (enabled && !contactsPermissionGranted) {
                    pendingGroupsToggle = true
                    showReadContactsPermissionRationale = true
                } else {
                    pendingGroupsToggle = null
                    blockGroups = enabled
                    viewModel.blockGroups = enabled
                }
            },
            blockedGroupIds = blockedGroupIds,
            onBlockedGroupIdsChange = { blockedGroupIds = it; viewModel.blockedGroupIds = it },
            blockInternational = blockInternational,
            onBlockInternationalChange = { enabled ->
                blockInternational = enabled
                viewModel.blockInternational = enabled
                if (enabled && allowedCountryIsos.isEmpty() && viewModel.defaultCountryIso.isNotEmpty()) {
                    allowedCountryIsos = viewModel.defaultCountryIso
                    viewModel.allowedCountryIsos = viewModel.defaultCountryIso
                }
            },
            allowedCountryIsos = allowedCountryIsos,
            onAllowedCountryIsosChange = { allowedCountryIsos = it; viewModel.allowedCountryIsos = it },
            contactGroups = contactGroups,
            contactsPermissionGranted = contactsPermissionGranted,
            onRequestContactsPermission = { showReadContactsPermissionRationale = true },
            getCountryName = viewModel::getCountryName,
            onShowGroupDialog = { showGroupDialog = true },
            onShowCountryDialog = { showCountryDialog = true },
        )

        Spacer(Modifier.height(12.dp))

        CallLogRulesSection(
            blockNotCalled = blockNotCalled,
            onBlockNotCalledChange = { enabled ->
                if (enabled && !callLogPermissionGranted) {
                    pendingBlockNotCalledToggle = true
                    showReadCallLogRationale = true
                } else {
                    pendingBlockNotCalledToggle = null
                    blockNotCalled = enabled
                    viewModel.blockNotCalled = enabled
                }
            },
            notCalledWindowDays = notCalledWindowDays,
            onNotCalledWindowDaysChange = { notCalledWindowDays = it },
            onNotCalledWindowDaysChangeFinished = { viewModel.notCalledWindowDays = notCalledWindowDays },
            blockRejected = blockRejected,
            onBlockRejectedChange = { enabled ->
                if (enabled && !callLogPermissionGranted) {
                    pendingBlockRejectedToggle = true
                    showReadCallLogRationale = true
                } else {
                    pendingBlockRejectedToggle = null
                    blockRejected = enabled
                    viewModel.blockRejected = enabled
                }
            },
            rejectedWindowDays = rejectedWindowDays,
            onRejectedWindowDaysChange = { rejectedWindowDays = it },
            onRejectedWindowDaysChangeFinished = { viewModel.rejectedWindowDays = rejectedWindowDays },
            blockRepeated = blockRepeated,
            onBlockRepeatedChange = { enabled ->
                if (enabled && !callLogPermissionGranted) {
                    pendingBlockRepeatedToggle = true
                    showReadCallLogRationale = true
                } else {
                    pendingBlockRepeatedToggle = null
                    blockRepeated = enabled
                    viewModel.blockRepeated = enabled
                }
            },
            repeatedCallCount = repeatedCallCount,
            onRepeatedCallCountChange = { repeatedCallCount = it },
            onRepeatedCallCountChangeFinished = { viewModel.repeatedCallCount = repeatedCallCount },
            repeatedCallWindowMinutes = repeatedCallWindowMinutes,
            onRepeatedCallWindowMinutesChange = { repeatedCallWindowMinutes = it },
            onRepeatedCallWindowMinutesChangeFinished = { viewModel.repeatedCallWindowMinutes = repeatedCallWindowMinutes },
            callLogPermissionGranted = callLogPermissionGranted,
            onRequestCallLogPermission = { showReadCallLogRationale = true },
        )

        Spacer(Modifier.height(12.dp))

        ScheduleRulesSection(
            scheduleRules = scheduleRules,
            onAddRule = { editingScheduleRule = null; showScheduleRuleDialog = true },
            onEditRule = { editingScheduleRule = it; showScheduleRuleDialog = true },
            onRemoveRule = viewModel::removeScheduleRule,
        )
    }

    if (showReadContactsPermissionRationale) {
        AlertDialog(
            onDismissRequest = {
                showReadContactsPermissionRationale = false
                pendingUnknownToggle = null
                pendingGroupsToggle = null
            },
            title = { Text(stringResource(R.string.permission_read_contacts_rationale_title)) },
            text = { Text(stringResource(R.string.permission_read_contacts_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadContactsPermissionRationale = false
                    val alreadyRequested = hasRequestedContacts
                    hasRequestedContacts = true
                    viewModel.contactsPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReadContactsPermissionRationale = false
                    pendingUnknownToggle = null
                    pendingGroupsToggle = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showReadCallLogRationale) {
        AlertDialog(
            onDismissRequest = {
                showReadCallLogRationale = false
                pendingBlockRepeatedToggle = null
                pendingBlockNotCalledToggle = null
                pendingBlockRejectedToggle = null
            },
            title = { Text(stringResource(R.string.permission_call_log_rationale_title)) },
            text = { Text(stringResource(R.string.permission_call_log_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadCallLogRationale = false
                    val alreadyRequested = hasRequestedCallsLog
                    hasRequestedCallsLog = true
                    viewModel.callsLogPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReadCallLogRationale = false
                    pendingBlockRepeatedToggle = null
                    pendingBlockNotCalledToggle = null
                    pendingBlockRejectedToggle = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCountryDialog) {
        CountryPickerDialog(
            title = stringResource(R.string.select_allowed_countries),
            getCountryName = viewModel::getCountryName,
            mode = CountryPickerMode.Multi(
                selectedIsos = allowedCountryIsos.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                onSave = { isos ->
                    allowedCountryIsos = isos.joinToString(",")
                    viewModel.allowedCountryIsos = allowedCountryIsos
                    showCountryDialog = false
                },
                lockedIsos = if (viewModel.defaultCountryIso.isNotEmpty()) setOf(viewModel.defaultCountryIso) else emptySet()
            ),
            onDismiss = { showCountryDialog = false },
        )
    }

    if (showGroupDialog) {
        GroupSelectionDialog(
            groups = contactGroups,
            selectedIds = blockedGroupIds.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { it.toLongOrNull() }
                .toSet(),
            onSave = { ids ->
                blockedGroupIds = ids.joinToString(",")
                viewModel.blockedGroupIds = blockedGroupIds
                showGroupDialog = false
            },
            onDismiss = { showGroupDialog = false },
        )
    }

    if (showScheduleRuleDialog) {
        ScheduleRuleDialog(
            initialRule = editingScheduleRule,
            onDismiss = {
                showScheduleRuleDialog = false
                editingScheduleRule = null
            },
            onConfirm = { rule ->
                if (editingScheduleRule != null) {
                    viewModel.updateScheduleRule(rule)
                } else {
                    viewModel.addScheduleRule(rule)
                }
                showScheduleRuleDialog = false
                editingScheduleRule = null
            }
        )
    }
}
