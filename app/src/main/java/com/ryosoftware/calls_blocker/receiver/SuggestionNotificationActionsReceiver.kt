package com.ryosoftware.calls_blocker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.data.db.Action
import com.ryosoftware.calls_blocker.data.db.Type
import com.ryosoftware.calls_blocker.data.repository.BlockSuggestionsRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SuggestionNotificationActionsReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_BLOCK = "${BuildConfig.APPLICATION_ID}.BLOCK_SUGGESTION"
        const val ACTION_DISMISS = "${BuildConfig.APPLICATION_ID}.DISMISS_SUGGESTION"
        const val EXTRA_PHONE_NUMBER = "phone-number"
        const val EXTRA_NOTIFICATION_ID = "notification-id"
    }
    @Inject lateinit var numberRepository: NumberRepository
    @Inject lateinit var blockSuggestionsRepository: BlockSuggestionsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (notificationId != 0) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.cancel(notificationId)
        }

        if (phoneNumber.isNotEmpty()) {
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    when (intent.action) {
                        ACTION_BLOCK -> {
                            numberRepository.add(
                                phoneNumber = phoneNumber,
                                description = "",
                                action = Action.BLOCK,
                                type = Type.EXACT_COINCIDENCE
                            )
                        }

                        ACTION_DISMISS -> {
                            blockSuggestionsRepository.add(phoneNumber)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
