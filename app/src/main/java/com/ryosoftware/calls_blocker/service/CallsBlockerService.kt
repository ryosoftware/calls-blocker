package com.ryosoftware.calls_blocker.service

import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.NORMALIZED_PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.PHONE_NUMBER_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.REASON_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.PostServiceWorker
import com.ryosoftware.calls_blocker.service.callsblocker.TIME_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.Logic
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CallsBlockerService : CallScreeningService() {
    @Inject
    lateinit var settingsManager: SettingsManager
    @Inject
    lateinit var callScreeningLogic: Logic
    @Inject
    lateinit var logger: Logger

    private fun rejectCall(callDetails: Call.Details, normalizedPhoneNumber: String?, reason: Reason) {
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(settingsManager.skipCallLog)
                .setSkipNotification(true)
                .build()
        )

        val reasonString = HistoryViewModel.getReasonString(applicationContext, reason)
        logger.log("Call from $NORMALIZED_PHONE_NUMBER_REF has been rejected due to $reasonString", normalizedPhoneNumber = normalizedPhoneNumber ?: "unknown")
    }

    private fun allowCall(callDetails: Call.Details, normalizedPhoneNumber: String?, reason: Reason) {
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .build()
        )

        val reasonString = HistoryViewModel.getReasonString(applicationContext, reason)
        logger.log("Call from $NORMALIZED_PHONE_NUMBER_REF has been allowed due to $reasonString", normalizedPhoneNumber = normalizedPhoneNumber ?: "unknown")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val startedInMemory = logger.startInMemory()
        try {
            val (normalizedPhoneNumber, reason) = callScreeningLogic.test(callDetails)

            val isAllowed = (reason in listOf(
                Reason.REASON_NONE,
                Reason.REASON_WHITELISTED_NUMBER,
                Reason.REASON_WHITELISTED_PREFIX
            ))

            if (isAllowed) {
                allowCall(callDetails, normalizedPhoneNumber, reason)
            } else {
                rejectCall(callDetails, normalizedPhoneNumber, reason)
            }

            val workerData = workDataOf(
                PHONE_NUMBER_PARAM to normalizedPhoneNumber,
                REASON_PARAM to reason.code,
                TIME_PARAM to System.currentTimeMillis()
            )

            val request = OneTimeWorkRequestBuilder<PostServiceWorker>()
                .setInputData(workerData)
                .build()

            WorkManager.getInstance(applicationContext).enqueue(request)
        }
        finally {
            if (startedInMemory) {
                logger.resumeToFile()
            }
        }
    }
}
