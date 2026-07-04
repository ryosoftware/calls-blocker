package com.ryosoftware.calls_blocker.viewmodel

import androidx.lifecycle.ViewModel
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.data.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DebugInfoViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    val logger: Logger,
) : ViewModel() {

    var isLoggingToFile
        get() = settingsManager.isLoggingToFile
        set(value) { settingsManager.isLoggingToFile = value }
}
