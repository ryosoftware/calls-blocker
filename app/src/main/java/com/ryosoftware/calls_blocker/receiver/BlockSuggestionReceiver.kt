package com.ryosoftware.calls_blocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.data.db.BlockSuggestion
import com.ryosoftware.calls_blocker.data.db.Number
import com.ryosoftware.calls_blocker.data.db.NumberDao
import com.ryosoftware.calls_blocker.data.db.BlockSuggestionDao
import com.ryosoftware.calls_blocker.data.db.Type
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockSuggestionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_BLOCK = "${BuildConfig.APPLICATION_ID}.BLOCK_SUGGESTION"
        const val ACTION_DISMISS = "${BuildConfig.APPLICATION_ID}.DISMISS_SUGGESTION"
        const val EXTRA_PHONE_NUMBER = "phone_number"
    }
    @Inject lateinit var numberDao: NumberDao
    @Inject lateinit var blockSuggestionDao: BlockSuggestionDao

    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val pendingResult = goAsync()

        val phoneNumberNotEmpty = phoneNumber.isNotEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_BLOCK -> {
                        if (phoneNumberNotEmpty) {
                            numberDao.insert(
                                Number(
                                    phoneNumber = phoneNumber,
                                    description = "",
                                    type = Type.EXACT_COINCIDENCE
                                )
                            )
                        }
                    }
                    ACTION_DISMISS -> {
                        if (phoneNumberNotEmpty) {
                            blockSuggestionDao.insert(
                                BlockSuggestion(phoneNumber = phoneNumber)
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
