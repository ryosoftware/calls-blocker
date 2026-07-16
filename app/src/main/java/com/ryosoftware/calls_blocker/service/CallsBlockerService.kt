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
import com.ryosoftware.calls_blocker.data.db.FLAG_CALL_SILENCED
import com.ryosoftware.calls_blocker.data.db.FLAG_SKIP_CALL_LOG
import com.ryosoftware.calls_blocker.data.db.FLAG_SKIP_NOTIFICATION
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.Reason.Companion.toString
import com.ryosoftware.calls_blocker.service.callsblocker.DIRECTION_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.FLAGS_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.PHONE_NUMBER_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.REASON_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.PostServiceWorker
import com.ryosoftware.calls_blocker.service.callsblocker.TIME_PARAM
import com.ryosoftware.calls_blocker.service.callsblocker.Logic
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private fun rejectCall(callDetails: Call.Details, normalizedPhoneNumber: String?, reason: Reason): Int {
        val flags = if (settingsManager.silenceInsteadOfHangup) {
            FLAG_CALL_SILENCED
        } else {
            (if (settingsManager.skipCallLog) FLAG_SKIP_CALL_LOG else 0) or
            (if (settingsManager.skipMissedCallNotification) FLAG_SKIP_NOTIFICATION else 0)
        }

        val callResponse = CallResponse.Builder()
            .setSkipCallLog((flags and FLAG_SKIP_CALL_LOG) != 0)
            .setSkipNotification((flags and FLAG_SKIP_NOTIFICATION) != 0)

        if ((flags and FLAG_CALL_SILENCED) != 0) {
            callResponse.setSilenceCall(true)
        } else {
            callResponse.setDisallowCall(true).setRejectCall(true)
        }

        respondToCall(callDetails, callResponse.build())

        val reasonString = reason.toString(this)
        logger.log("Call from $NORMALIZED_PHONE_NUMBER_REF has been rejected due to $reasonString (flags are ${flags.toString(2)})", normalizedPhoneNumber = normalizedPhoneNumber ?: "unknown")

        return flags
    }

    private fun allowCall(callDetails: Call.Details) =
        respondToCall(callDetails, CallResponse.Builder().build())

    private fun allowCall(callDetails: Call.Details, normalizedPhoneNumber: String?, reason: Reason) {
        allowCall(callDetails)

        val reasonString = reason.toString(this)
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
                if (callDetails.callDirection == Call.Details.DIRECTION_UNKNOWN) {
                    return@launch
                }

                val direction: Direction
                val normalizedPhoneNumber: String?
                val reason: Reason?
                val flags: Int?

                if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
                    direction = Direction.OUTGOING
                    normalizedPhoneNumber = callScreeningLogic.normalizePhoneNumber(callDetails)
                    reason = null
                    flags = null
                } else {
                    val result = callScreeningLogic.isCallBlocked(callDetails)
                    direction = Direction.INCOMING
                    normalizedPhoneNumber = result.first
                    reason = result.second

                    val isAllowed = reason in listOf(
                        Reason.NONE,
                        Reason.WHITELISTED_NUMBER,
                        Reason.WHITELISTED_PREFIX
                    )

                    if (isAllowed) {
                        allowCall(callDetails, normalizedPhoneNumber, reason)
                        flags = null
                    } else {
                        flags = rejectCall(callDetails, normalizedPhoneNumber, reason)
                    }
                }

                val workerData = mutableListOf(
                    DIRECTION_PARAM to direction.code,
                    PHONE_NUMBER_PARAM to normalizedPhoneNumber,
                    TIME_PARAM to System.currentTimeMillis(),
                )
                reason?.let { workerData += REASON_PARAM to it.code }
                flags?.let { workerData += FLAGS_PARAM to it }

                enqueueWorker(workDataOf(*workerData.toTypedArray()))
            }
            finally {
                if (startedInMemory) {
                    logger.resumeToFile()
                }
            }
        }
    }
}
