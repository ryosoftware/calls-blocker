package com.ryosoftware.calls_blocker.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.ryosoftware.calls_blocker.ui.screens.FindMyPhoneScreen
import com.ryosoftware.calls_blocker.ui.theme.CallsBlockerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FindMyPhoneActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallsBlockerTheme {
                Surface {
                    FindMyPhoneScreen()
                }
            }
        }
    }
}