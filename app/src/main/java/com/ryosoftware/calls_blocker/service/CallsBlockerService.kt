package com.ryosoftware.calls_blocker.service

import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.NORMALIZED_PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Direction
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.DIRECTION_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.PHONE_NUMBER_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.REASON_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.PostServiceWorker
import com.ryosoftware.calls_blocker.service.callsblocker.TIME_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.Logic
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallsBlockerService : CallScreeningService() {
    @Inject
    lateinit var settingsManager: SettingsManager
    @Inject
    lateinit var callScreeningLogic: Logic
    @Inject
    lateinit var logger: Logger

    private val scope = CoroutineScope(Dispatchers.Default)

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

    private fun allowCall(callDetails: Call.Details) =
        respondToCall(callDetails, CallResponse.Builder().build())

    private fun allowCall(callDetails: Call.Details, normalizedPhoneNumber: String?, reason: Reason) {
        allowCall(callDetails)

        val reasonString = HistoryViewModel.getReasonString(applicationContext, reason)
        logger.log("Call from $NORMALIZED_PHONE_NUMBER_REF has been allowed due to $reasonString", normalizedPhoneNumber = normalizedPhoneNumber ?: "unknown")
    }

    private fun enqueueWorker(data: Data) {
        val request = OneTimeWorkRequestBuilder<PostServiceWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(request)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
    override fun onScreenCall(callDetails: Call.Details) {
        scope.launch {
            val startedInMemory = logger.startInMemory()
            try {
                if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
                    if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
                        val normalizedPhoneNumber = callScreeningLogic.normalizePhoneNumber(callDetails)

                        val workerData = workDataOf(
                            PHONE_NUMBER_PARAM to normalizedPhoneNumber,
                            DIRECTION_PARAM to Direction.OUTGOING.code,
                            TIME_PARAM to System.currentTimeMillis()
                        )
                        enqueueWorker(workerData)
                    }
                    return@launch
                }

                val (normalizedPhoneNumber, reason) = callScreeningLogic.isCallBlocked(callDetails)

                val isAllowed = (reason in listOf(
                    Reason.NONE,
                    Reason.WHITELISTED_NUMBER,
                    Reason.WHITELISTED_PREFIX
                ))

                if (isAllowed) {
                    allowCall(callDetails, normalizedPhoneNumber, reason)
                } else {
                    rejectCall(callDetails, normalizedPhoneNumber, reason)
                }

                val workerData = workDataOf(
                    PHONE_NUMBER_PARAM to normalizedPhoneNumber,
                    DIRECTION_PARAM to Direction.INCOMING.code,
                    REASON_PARAM to reason.code,
                    TIME_PARAM to System.currentTimeMillis()
                )
                enqueueWorker(workerData)
            }
            finally {
                if (startedInMemory) {
                    logger.resumeToFile()
                }
            }
        }
    }
}
