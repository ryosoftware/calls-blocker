package com.ryosoftware.calls_blocker.ui.activities

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.AlertDialog
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.data.CountryNameProvider
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.importexport.ImportResult
import com.ryosoftware.calls_blocker.navigation.Screen
import com.ryosoftware.calls_blocker.ui.screens.DebugLogScreen
import com.ryosoftware.calls_blocker.ui.screens.HistoryScreen
import com.ryosoftware.calls_blocker.ui.screens.ImportReviewScreen
import com.ryosoftware.calls_blocker.ui.screens.NumbersListScreen
import com.ryosoftware.calls_blocker.ui.screens.SettingsScreen
import com.ryosoftware.calls_blocker.ui.theme.CallsBlockerTheme
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

const val EXTRA_OPEN_HISTORY = "open-history"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager
    @Inject
    lateinit var countryNameProvider: CountryNameProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (settingsManager.defaultCountryIso.isEmpty()) {
            val iso = PhoneUtils.getNetworkCountriesIso(this)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: resources.configuration.locales[0].country
            if (iso != null) {
                settingsManager.defaultCountryIso = iso
            }
        }

        setContent {
            CallsBlockerTheme {
                CallsBlockerApp(settingsManager, countryNameProvider)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsBlockerApp(
    settingsManager: SettingsManager,
    countryNameProvider: CountryNameProvider
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDebugLogScreen = navBackStackEntry
        ?.destination
        ?.hierarchy
        ?.any { it.route == Screen.DebugLog.route } == true
    val isImportReviewScreen = navBackStackEntry
        ?.destination
        ?.hierarchy
        ?.any { it.route == Screen.ImportReview.route } == true
    val isFindMyPhoneScreen = navBackStackEntry
        ?.destination
        ?.hierarchy
        ?.any { it.route == Screen.FindMyPhone.route } == true

    val bottomNavItems = listOf(
        Screen.BlockList,
        Screen.History,
        Screen.Settings
    )

    val currentScreen = bottomNavItems.find { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    var onSaveLog by remember { mutableStateOf<() -> Unit>({}) }
    var onShareLog by remember { mutableStateOf<() -> Unit>({}) }
    var selectionCount by remember { mutableIntStateOf(0) }
    var selectionAllSelected by remember { mutableStateOf(false) }
    var selectionOnClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectionOnSelectAll by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectionOnDelete by remember { mutableStateOf<(() -> Unit)?>(null) }

    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    val selectionActive = selectionCount > 0

    val context = LocalContext.current
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val exportCsvScope = rememberCoroutineScope()
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            exportCsvScope.launch {
                historyViewModel.exportCsv(context.applicationContext, uri)
                Toast.makeText(context, R.string.history_export_success, Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showScreeningDialog by remember { mutableStateOf(false) }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { showScreeningDialog = false }

    val startRoute = remember {
        val intent = (context as? Activity)?.intent
        when {
            intent?.getBooleanExtra(EXTRA_OPEN_HISTORY, false) == true -> {
                intent.removeExtra(EXTRA_OPEN_HISTORY)
                Screen.History.route
            }

            else -> settingsManager.lastActiveTab.ifEmpty { Screen.Settings.route }
        }
    }

    LaunchedEffect(Unit) {
        if (!settingsManager.screeningDialogDismissed) {
            showScreeningDialog = !settingsManager.isScreeningActive()
        }
    }

    var showBottomBar by remember { mutableStateOf(true) }

    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route

        if (route == Screen.DebugLog.route || route == Screen.ImportReview.route) {
            delay(300.milliseconds)
            showBottomBar = false
        } else {
            showBottomBar = true
        }
    }

    if (showScreeningDialog) {
        AlertDialog(
            onDismissRequest = { showScreeningDialog = false },
            title = { Text(stringResource(R.string.screening_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.screening_dialog_message,
                        stringResource(R.string.app_name)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsManager.createRequestRoleIntent()?.let {
                        roleLauncher.launch(it)
                    }
                }) {
                    Text(stringResource(R.string.screening_dialog_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    settingsManager.screeningDialogDismissed = true
                    showScreeningDialog = false
                }) {
                    Text(stringResource(R.string.screening_dialog_later))
                }
            }
        )
    }

    val appBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),

        topBar = {
            if (!isFindMyPhoneScreen) {
                if (isDebugLogScreen) {
                    TopAppBar(
                        title = {
                            Text(stringResource(R.string.debug_screen_title))
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onShareLog) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_log)
                                )
                            }
                            IconButton(onClick = onSaveLog) {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = stringResource(R.string.save_log)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = appBarColors
                    )
                } else if (isImportReviewScreen) {
                    TopAppBar(
                        title = {
                            Text(stringResource(R.string.import_preview_title))
                        },
                        scrollBehavior = scrollBehavior,
                        colors = appBarColors
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (selectionActive) {
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.n_selected,
                                        selectionCount,
                                        selectionCount
                                    )
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        currentScreen?.titleResId ?: R.string.app_name
                                    )
                                )
                            }
                        },
                        navigationIcon = {
                            if (selectionActive) {
                                IconButton(onClick = { selectionOnClose?.invoke() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null
                                    )
                                }
                            }
                        },
                        actions = {
                            if (selectionActive) {
                                if (!selectionAllSelected) {
                                    IconButton(onClick = { selectionOnSelectAll?.invoke() }) {
                                        Icon(
                                            Icons.Default.SelectAll,
                                            contentDescription = null
                                        )
                                    }
                                }
                                IconButton(onClick = { selectionOnDelete?.invoke() }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                if (currentScreen == Screen.History) {
                                    IconButton(onClick = { exportCsvLauncher.launch("history_export.csv") }) {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = stringResource(R.string.history_export)
                                        )
                                    }
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = appBarColors
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                settingsManager.lastActiveTab = screen.route
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = stringResource(screen.titleResId)
                                )
                            },
                            label = {
                                Text(stringResource(screen.titleResId))
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.BlockList.route) {
                NumbersListScreen(
                    defaultCountryIso = settingsManager.defaultCountryIso,
                    onMultiSelect = { count, allSelected, onClose, onSelectAll, onDelete ->
                        selectionCount = count
                        selectionAllSelected = allSelected
                        selectionOnClose = onClose
                        selectionOnSelectAll = onSelectAll
                        selectionOnDelete = onDelete
                    },
                    onImportReady = { result ->
                        importResult = result
                        navController.navigate(Screen.ImportReview.route)
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    onMultiSelect = { count, allSelected, onClose, onSelectAll, onDelete ->
                        selectionCount = count
                        selectionAllSelected = allSelected
                        selectionOnClose = onClose
                        selectionOnSelectAll = onSelectAll
                        selectionOnDelete = onDelete
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToDebugLog = {
                        navController.navigate(Screen.DebugLog.route)
                    }
                )
            }
            composable(Screen.ImportReview.route) {
                importResult?.let { result ->
                    ImportReviewScreen(
                        importResult = result,
                        viewModel = hiltViewModel(),
                        onDismiss = {
                            importResult = null
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable(Screen.DebugLog.route) {
                DebugLogScreen(
                    onSaveReady = { onSaveLog = it },
                    onShareReady = { onShareLog = it }
                )
            }
        }

        BackHandler(enabled = currentScreen != null) {
            (context as? Activity)?.finish()
        }
    }
}