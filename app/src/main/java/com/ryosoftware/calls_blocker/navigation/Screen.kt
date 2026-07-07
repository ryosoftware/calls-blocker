package com.ryosoftware.calls_blocker.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.ui.graphics.vector.ImageVector
import com.ryosoftware.calls_blocker.R

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    object BlockList : Screen("numbers", R.string.nav_block_list, Icons.Default.Tag)
    object History : Screen("history", R.string.nav_history, Icons.Default.History)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Default.Settings)
    object DebugLog : Screen("debug_log", R.string.log_to_file, Icons.Default.Settings)
    object ImportReview : Screen("import_review", R.string.import_preview_title, Icons.Default.FileUpload)
    object FindMyPhone : Screen("find_my_phone", R.string.find_my_phone_activated_no_number, Icons.Default.Alarm)
    object CallBlockingRules : Screen("call_blocking_rules", R.string.nav_call_blocking_rules, Icons.Default.Settings)
}
