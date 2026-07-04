package com.ryosoftware.calls_blocker.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.Main.Companion.hasPostNotificationsPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadCallLogPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import com.ryosoftware.calls_blocker.Main.Companion.hasReadPhoneStatePermission
import com.ryosoftware.calls_blocker.Main.Companion.isIgnoringBatteryOptimizations
import com.ryosoftware.calls_blocker.Main.Companion.requestIgnoreBatteryOptimizationsPermission
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.ContactGroup
import com.ryosoftware.calls_blocker.data.countries
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.ScheduleRule
import com.ryosoftware.calls_blocker.ui.screens.settings.AllowPermissionCard
import com.ryosoftware.calls_blocker.ui.screens.settings.BlockingRulesSection
import com.ryosoftware.calls_blocker.ui.screens.settings.CallLogRulesSection
import com.ryosoftware.calls_blocker.ui.screens.settings.CallScreeningStatusCard
import com.ryosoftware.calls_blocker.ui.screens.settings.FindMyPhoneSection
import com.ryosoftware.calls_blocker.ui.screens.settings.ScheduleRulesSection
import com.ryosoftware.calls_blocker.viewmodel.BackupEvent
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import com.ryosoftware.calls_blocker.viewmodel.SettingsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToDebugLog: () -> Unit = {}
) {
    val context = LocalContext.current
    var blockHidden by remember { mutableStateOf(viewModel.blockHidden) }
    var blockUnknown by remember { mutableStateOf(viewModel.blockUnknown) }
    var blockInternational by remember { mutableStateOf(viewModel.blockInternational) }
    var allowedCountryIsos by remember { mutableStateOf(viewModel.allowedCountryIsos) }
    var skipCallLog by remember { mutableStateOf(viewModel.skipCallLog) }
    var defaultCountryIso by remember { mutableStateOf(viewModel.defaultCountryIso) }
    val screeningEnabled by viewModel.isScreeningEnabled.collectAsState()
    var showCountryDialog by remember { mutableStateOf(false) }
    var showDefaultCountryDialog by remember { mutableStateOf(false) }
    var blockGroups by remember { mutableStateOf(viewModel.blockGroups) }
    var blockedGroupIds by remember { mutableStateOf(viewModel.blockedGroupIds) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var isLoggingToFile by remember { mutableStateOf(viewModel.isLoggingToFile) }
    var blockRepeated by remember { mutableStateOf(viewModel.blockRepeated) }
    var repeatedCallCount by remember { mutableIntStateOf(viewModel.repeatedCallCount) }
    var repeatedCallWindowMinutes by remember { mutableIntStateOf(viewModel.repeatedCallWindowMinutes) }
    var blockNotCalled by remember { mutableStateOf(viewModel.blockNotCalled) }
    var notCalledWindowDays by remember { mutableIntStateOf(viewModel.notCalledWindowDays) }
    var blockRejected by remember { mutableStateOf(viewModel.blockRejected) }
    var rejectedWindowDays by remember { mutableIntStateOf(viewModel.rejectedWindowDays) }
    var findMyPhoneEnabled by remember { mutableStateOf(viewModel.findMyPhoneEnabled) }
    var findMyPhonePhoneNumbers by remember { mutableStateOf(viewModel.findMyPhonePhoneNumbers) }
    var findMyPhoneCallCount by remember { mutableIntStateOf(viewModel.findMyPhoneCallCount) }
    var findMyPhoneWindowMinutes by remember { mutableIntStateOf(viewModel.findMyPhoneWindowMinutes) }
    var findMyPhoneRingtoneUri by remember { mutableStateOf(viewModel.findMyPhoneRingtoneUri) }
    var hasRequestedContacts by remember { mutableStateOf(viewModel.contactsPermissionRequested) }
    var showReadContactsPermissionRationale by remember { mutableStateOf(false) }
    var hasRequestedCallsLog by remember { mutableStateOf(viewModel.callsLogPermissionRequested) }
    var showReadCallLogRationale by remember { mutableStateOf(false) }
    var hasRequestedNotifications by remember { mutableStateOf(false) }
    var showPostNotificationsRationale by remember { mutableStateOf(false) }
    var hasRequestedPhoneState by remember { mutableStateOf(false) }
    var showReadPhoneStateRationale by remember { mutableStateOf(false) }
    var pendingUnknownToggle by remember { mutableStateOf<Boolean?>(null) }
    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    var pendingGroupsToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockRepeatedToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockNotCalledToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingBlockRejectedToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingFindMyPhoneToggle by remember { mutableStateOf<Boolean?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showTestScreeningDialog by remember { mutableStateOf(false) }
    var testScreeningResult by remember { mutableStateOf<Pair<String, Reason?>?>(null) }
    var showScheduleRuleDialog by remember { mutableStateOf(false) }
    var editingScheduleRule by remember { mutableStateOf<ScheduleRule?>(null) }
    val scheduleRules by viewModel.scheduleRules.collectAsState()

    val contactGroups by produceState(
        initialValue = emptyList<ContactGroup>(),
        key1 = permissionCheckTrigger,
    ) {
        value = viewModel.getContactGroups()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.checkScreeningStatus() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.backup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirmDialog = true
        }
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissionCheckTrigger++ }

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
            if (pendingFindMyPhoneToggle != null) {
                findMyPhoneEnabled = pendingFindMyPhoneToggle!!
                viewModel.findMyPhoneEnabled = pendingFindMyPhoneToggle!!
            }
        }
        pendingBlockRepeatedToggle = null
        pendingBlockNotCalledToggle = null
        pendingBlockRejectedToggle = null
        pendingFindMyPhoneToggle = null
        permissionCheckTrigger++
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCheckTrigger++
        }
    }

    val mediaAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCheckTrigger++
        }
    }

    val contactsPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadContactsPermission()
    }

    val notificationPermissionGranted = remember(permissionCheckTrigger) {
        context.hasPostNotificationsPermission()
    }

    val callLogPermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadCallLogPermission()
    }

    val phoneStatePermissionGranted = remember(permissionCheckTrigger) {
        context.hasReadPhoneStatePermission()
    }

    val batteryOptimizationGranted = remember(permissionCheckTrigger) {
        context.isIgnoringBatteryOptimizations()
    }

    LaunchedEffect(Unit) {
        viewModel.checkScreeningStatus()
    }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { event ->
            when (event) {
                is BackupEvent.Success -> {
                    Toast.makeText(context, R.string.backup_success, Toast.LENGTH_SHORT).show()
                }
                is BackupEvent.RestoreSuccess -> {
                    defaultCountryIso = viewModel.defaultCountryIso
                    blockUnknown = viewModel.blockUnknown
                    blockHidden = viewModel.blockHidden
                    blockGroups = viewModel.blockGroups
                    blockedGroupIds = viewModel.blockedGroupIds
                    blockInternational = viewModel.blockInternational
                    allowedCountryIsos = viewModel.allowedCountryIsos
                    blockNotCalled = viewModel.blockNotCalled
                    notCalledWindowDays = viewModel.notCalledWindowDays
                    blockRejected = viewModel.blockRejected
                    rejectedWindowDays = viewModel.rejectedWindowDays
                    blockRepeated = viewModel.blockRepeated
                    repeatedCallCount = viewModel.repeatedCallCount
                    repeatedCallWindowMinutes = viewModel.repeatedCallWindowMinutes
                    skipCallLog = viewModel.skipCallLog
                    findMyPhoneEnabled = viewModel.findMyPhoneEnabled
                    findMyPhonePhoneNumbers = viewModel.findMyPhonePhoneNumbers
                    findMyPhoneCallCount = viewModel.findMyPhoneCallCount
                    findMyPhoneWindowMinutes = viewModel.findMyPhoneWindowMinutes
                    findMyPhoneRingtoneUri = viewModel.findMyPhoneRingtoneUri
                    isLoggingToFile = viewModel.isLoggingToFile
                    hasRequestedContacts = viewModel.contactsPermissionRequested
                    hasRequestedCallsLog = viewModel.callsLogPermissionRequested
                    hasRequestedNotifications = viewModel.notificationsPermissionRequested
                    Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT).show()
                }
                is BackupEvent.Error -> {
                    errorDialogMessage = event.message
                }
            }
        }
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
                if (callLogGranted && pendingFindMyPhoneToggle != null) {
                    findMyPhoneEnabled = pendingFindMyPhoneToggle!!
                    viewModel.findMyPhoneEnabled = pendingFindMyPhoneToggle!!
                    pendingFindMyPhoneToggle = null
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
        CallScreeningStatusCard(
            enabled = screeningEnabled,
            onEnable = {
                val intent = viewModel.createRequestRoleIntent()
                if (intent != null) {
                    roleLauncher.launch(intent)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            },
            contactsPermissionGranted = contactsPermissionGranted,
            phoneStatePermissionGranted = phoneStatePermissionGranted,
            onRequestContactsPermission = {
                showReadContactsPermissionRationale = true
            },
            onRequestPhoneStatePermission = {
                showReadPhoneStateRationale = true
            }
        )

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.default_country_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.default_country_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                val defaultCountry = defaultCountryIso.let { iso ->
                    if (iso.isNotEmpty()) countries.firstOrNull { it.iso == iso }
                    else null
                }

                TextButton(onClick = { showDefaultCountryDialog = true }) {
                    Text(
                        text = defaultCountry?.let { "${it.flag} ${viewModel.getCountryName(it)}" }
                            ?: stringResource(R.string.default_country_not_set),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.blocking_rules_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = stringResource(R.string.blocking_rules_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ScheduleRulesSection(
            scheduleRules = scheduleRules,
            onAddRule = { editingScheduleRule = null; showScheduleRuleDialog = true },
            onEditRule = { editingScheduleRule = it; showScheduleRuleDialog = true },
            onRemoveRule = viewModel::removeScheduleRule,
        )

        Spacer(Modifier.height(12.dp))

        BlockingRulesSection(
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
                if (enabled && allowedCountryIsos.isEmpty() && defaultCountryIso.isNotEmpty()) {
                    allowedCountryIsos = defaultCountryIso
                    viewModel.allowedCountryIsos = defaultCountryIso
                }
            },
            allowedCountryIsos = allowedCountryIsos,
            onAllowedCountryIsosChange = { allowedCountryIsos = it; viewModel.allowedCountryIsos = it },
            defaultCountryIso = defaultCountryIso,
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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTestScreeningDialog = true }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.test_screening_title),
                    style = MaterialTheme.typography.bodyLarge
                )

                val needsContactsPermission = (blockUnknown || blockGroups) && !contactsPermissionGranted
                val needsCallLogPermission = (blockNotCalled || blockRejected || blockRepeated) && !callLogPermissionGranted
                val lines = mutableListOf(stringResource(R.string.test_screening_description))
                if (!scheduleRules.isEmpty()) lines.add(stringResource(R.string.test_screening_description_addon_scheduler))
                if (needsContactsPermission || needsCallLogPermission) lines.add(stringResource(R.string.test_screening_description_addon_permissions))

                Text(
                    text = lines.joinToString("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            skipCallLog = !skipCallLog
                            viewModel.skipCallLog = skipCallLog
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.skip_call_log_title),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Text(
                            text = stringResource(R.string.skip_call_log_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(24.dp))

                    Switch(
                        checked = skipCallLog,
                        onCheckedChange = null
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        FindMyPhoneSection(
            findMyPhoneEnabled = findMyPhoneEnabled,
            onFindMyPhoneEnabledChange = { enabled ->
                if (enabled && !callLogPermissionGranted) {
                    pendingFindMyPhoneToggle = true
                    showReadCallLogRationale = true
                } else {
                    pendingFindMyPhoneToggle = null
                    findMyPhoneEnabled = enabled
                    viewModel.findMyPhoneEnabled = enabled
                }
            },
            findMyPhonePhoneNumbers = findMyPhonePhoneNumbers,
            onFindMyPhonePhoneNumbersChange = { findMyPhonePhoneNumbers = it; viewModel.findMyPhonePhoneNumbers = it },
            findMyPhoneCallCount = findMyPhoneCallCount,
            onFindMyPhoneCallCountChange = { findMyPhoneCallCount = it },
            onFindMyPhoneCallCountChangeFinished = { viewModel.findMyPhoneCallCount = findMyPhoneCallCount },
            findMyPhoneWindowMinutes = findMyPhoneWindowMinutes,
            onFindMyPhoneWindowMinutesChange = { findMyPhoneWindowMinutes = it },
            onFindMyPhoneWindowMinutesChangeFinished = { viewModel.findMyPhoneWindowMinutes = findMyPhoneWindowMinutes },
            findMyPhoneRingtoneUri = findMyPhoneRingtoneUri,
            onFindMyPhoneRingtoneUriChange = { findMyPhoneRingtoneUri = it; viewModel.findMyPhoneRingtoneUri = it },
            callLogPermissionGranted = callLogPermissionGranted,
            batteryOptimizationGranted = batteryOptimizationGranted,
            onRequestCallLogPermission = { showReadCallLogRationale = true },
            onRequestBatteryOptimization = { context.requestIgnoreBatteryOptimizationsPermission() },
            getCountryName = viewModel::getCountryName,
            defaultCountryIso = defaultCountryIso,
        )

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.permissions_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = stringResource(R.string.permissions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { showPostNotificationsRationale = true },
                    enabled = !notificationPermissionGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_notifications))
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { context.requestIgnoreBatteryOptimizationsPermission() },
                    enabled = !batteryOptimizationGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permission_battery))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToDebugLog() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.debug_information),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = if (isLoggingToFile) stringResource(R.string.debug_information_enabled) else stringResource(R.string.debug_information_disabled),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isLoggingToFile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    @SuppressLint("LocalContextGetResourceValueCall")
                    val url = context.getString(R.string.github_repo)

                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.github_repo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        HorizontalDivider()

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.backup_import))
            }

            OutlinedButton(
                onClick = { exportLauncher.launch("calls_blocker_backup.json") },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.backup_export))
            }
        }

        if (showTestScreeningDialog) {
            AddNumberDialog(
                title = stringResource(R.string.test_screening_title),
                getCountryName = viewModel::getCountryName,
                defaultCountryIso = defaultCountryIso,
                showBlockType = false,
                showDescription = false,
                showActionSelector = false,
                confirmButtonText = stringResource(R.string.test_screening_button),
                onDismiss = { showTestScreeningDialog = false },
                onConfirm = { number, _, _, _ ->
                    val reason = viewModel.getPhoneNumberBlockReason(number)
                    testScreeningResult = number to reason
                    showTestScreeningDialog = false
                }
            )
        }

        testScreeningResult?.let { (number, reason) ->
            val isBlocked = reason != null
            AlertDialog(
                onDismissRequest = { testScreeningResult = null },
                title = { Text(stringResource(R.string.test_screening_title)) },
                text = {
                    Column {
                        Text(
                            text = if (isBlocked) stringResource(if (reason == Reason.REASON_FIND_MY_PHONE) R.string.test_screening_result_blocked_find_by_phone else R.string.test_screening_result_blocked, number, HistoryViewModel.getReasonString(context, reason))
                                   else stringResource(R.string.test_screening_result_not_blocked, number),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { testScreeningResult = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
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
                pendingFindMyPhoneToggle = null
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

    if (showPostNotificationsRationale) {
        AlertDialog(
            onDismissRequest = { showPostNotificationsRationale = false },
            title = { Text(stringResource(R.string.permission_notifications_rationale_title)) },
            text = { Text(stringResource(R.string.permission_notifications_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPostNotificationsRationale = false
                    val alreadyRequested = hasRequestedNotifications
                    hasRequestedNotifications = true
                    viewModel.notificationsPermissionRequested = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPostNotificationsRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showReadPhoneStateRationale) {
        AlertDialog(
            onDismissRequest = { showReadPhoneStateRationale = false },
            title = { Text(stringResource(R.string.permission_read_phone_state_rationale_title)) },
            text = { Text(stringResource(R.string.permission_read_phone_state_rationale_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showReadPhoneStateRationale = false
                    val alreadyRequested = hasRequestedPhoneState
                    hasRequestedPhoneState = true
                    if (alreadyRequested) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                        )
                    } else {
                        @Suppress("InlinedApi")
                        phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReadPhoneStateRationale = false }) {
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
                lockedIsos = if (defaultCountryIso.isNotEmpty()) setOf(defaultCountryIso) else emptySet()
            ),
            onDismiss = { showCountryDialog = false },
        )
    }

    if (showDefaultCountryDialog) {
        CountryPickerDialog(
            title = stringResource(R.string.default_country_title),
            getCountryName = viewModel::getCountryName,
            mode = CountryPickerMode.Single(
                onSelect = { country ->
                    defaultCountryIso = country.iso
                    viewModel.defaultCountryIso = country.iso
                    if (country.iso.isNotEmpty() && blockInternational && allowedCountryIsos.isEmpty()) {
                        allowedCountryIsos = country.iso
                        viewModel.allowedCountryIsos = country.iso
                    }
                    showDefaultCountryDialog = false
                }
            ),
            onDismiss = { showDefaultCountryDialog = false },
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

    if (showRestoreConfirmDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri?.let { uri -> viewModel.restore(uri) }
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    errorDialogMessage?.let { message ->
        DisposableEffect(message) {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { _, _ -> errorDialogMessage = null }
                .setOnDismissListener { errorDialogMessage = null }
                .show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }
}
