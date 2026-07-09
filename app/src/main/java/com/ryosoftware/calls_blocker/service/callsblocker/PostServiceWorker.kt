package com.ryosoftware.calls_blocker.service.callsblocker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ryosoftware.calls_blocker.ui.activities.EXTRA_OPEN_HISTORY
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.Main
import com.ryosoftware.calls_blocker.Main.Companion.NOTIFICATION_CHANNEL_ID
import com.ryosoftware.calls_blocker.Main.Companion.SUGGESTION_CHANNEL_ID
import com.ryosoftware.calls_blocker.Main.Companion.hasPostNotificationsPermission
import com.ryosoftware.calls_blocker.ui.activities.MainActivity
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.NORMALIZED_PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.PhoneUtils
import com.ryosoftware.calls_blocker.data.db.Direction
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.db.NumberType
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.db.Reason.Companion.toString
import com.ryosoftware.calls_blocker.data.repository.HistoryRepository
import com.ryosoftware.calls_blocker.data.repository.BlockSuggestionsRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.receiver.SuggestionNotificationActionsReceiver
import com.ryosoftware.calls_blocker.ui.activities.FindMyPhoneActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

const val PHONE_NUMBER_PARAM = "phone-number"
const val REASON_PARAM = "reason"
const val DIRECTION_PARAM = "direction"

const val TIME_PARAM = "time"
const val TESTING_PURPOSES_PARAM = "testing-purposes"

private const val FIND_MY_PHONE_FULL_SCREEN_ACTIVITY_PENDING_INTENT_REQUEST_CODE = 1
private const val FIND_MY_PHONE_FULL_STOP_PLAYBACK_REQUEST_CODE = FIND_MY_PHONE_FULL_SCREEN_ACTIVITY_PENDING_INTENT_REQUEST_CODE + 1

@HiltWorker
class PostServiceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val historyRepository: HistoryRepository,
    private val numberRepository: NumberRepository,
    private val blockSuggestionsRepository: BlockSuggestionsRepository,
    private val logger: Logger,
    private val findMyPhonePlayer: FindMyPhonePlayer,
) : CoroutineWorker(appContext, workerParams) {

    private fun getNotificationId(phoneNumber: String): Int = 2 + (phoneNumber.hashCode() and 0x7FFFFFFF)

    private fun getStringDateTime(context: Context, timeInMillis: Long): String {
        val locale = Locale.getDefault()
        val zone = ZoneId.systemDefault()

        val instant = Instant.ofEpochMilli(timeInMillis)
        val dateTime = instant.atZone(zone)

        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withLocale(locale)

        val dateText = dateTime.toLocalDate().format(dateFormatter)
        val timeText = dateTime.toLocalTime().format(timeFormatter)

        return context.resources.getQuantityString(R.plurals.date_and_time, dateTime.hour, dateText, timeText)
    }

    private fun postBlockedNumberNotification(phoneNumber: String, reason: Reason, time: Long) {
        if (!applicationContext.hasPostNotificationsPermission()) return

        val formattedNumber = PhoneUtils.formatPhoneNumber(phoneNumber)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_HISTORY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reasonString = reason.toString(applicationContext)
        val date = getStringDateTime(applicationContext, time)

        @SuppressLint("StringFormatInvalid")
        val title = applicationContext.getString(R.string.notification_blocked_title, formattedNumber, reasonString, date)
        @SuppressLint("StringFormatMatches")
        val body = applicationContext.getString(R.string.notification_blocked_body, formattedNumber, reasonString, date)

        val notification =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification_blocked_call)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setWhen(time)
                .build()

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.notify(getNotificationId(phoneNumber), notification)
    }

    private fun postSuggestionNotification(phoneNumber: String, time: Long) {
        if (!applicationContext.hasPostNotificationsPermission()) return

        val notificationId = getNotificationId(phoneNumber)

        val formattedNumber = PhoneUtils.formatPhoneNumber(phoneNumber)

        val blockIntent = Intent(applicationContext, SuggestionNotificationActionsReceiver::class.java).apply {
            action = SuggestionNotificationActionsReceiver.ACTION_BLOCK
            putExtra(SuggestionNotificationActionsReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(SuggestionNotificationActionsReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            applicationContext, phoneNumber.hashCode(), blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(applicationContext, SuggestionNotificationActionsReceiver::class.java).apply {
            action = SuggestionNotificationActionsReceiver.ACTION_DISMISS
            putExtra(SuggestionNotificationActionsReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(SuggestionNotificationActionsReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            applicationContext, phoneNumber.hashCode() xor 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val date = getStringDateTime(applicationContext, time)
        @SuppressLint("StringFormatInvalid")
        val title = applicationContext.getString(R.string.notification_suggestion_title, formattedNumber, date)
        @SuppressLint("StringFormatMatches")
        val body = applicationContext.getString(R.string.notification_suggestion_body, formattedNumber, date)

        val notification = NotificationCompat.Builder(applicationContext, SUGGESTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification_block_suggestion)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setWhen(time)
            .addAction(0, applicationContext.getString(R.string.notification_suggestion_block), blockPendingIntent)
            .addAction(0, applicationContext.getString(R.string.notification_suggestion_dismiss), dismissPendingIntent)
            .build()

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }

    private fun createForegroundInfo(phoneNumber: String): ForegroundInfo {
        val formattedNumber = PhoneUtils.formatPhoneNumber(phoneNumber)
        val stopPlaybackIntent = Intent(FindMyPhonePlayer.ACTION_STOP).apply {
            setPackage(applicationContext.packageName)
        }

        val stopPlaybackPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            FIND_MY_PHONE_FULL_STOP_PLAYBACK_REQUEST_CODE,
            stopPlaybackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (formattedNumber.isEmpty()) applicationContext.getString(R.string.find_my_phone_activated_no_number)
                           else applicationContext.getString(R.string.find_my_phone_activated_from, formattedNumber)

        val notification = NotificationCompat.Builder(applicationContext, Main.FIND_MY_PHONE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification_find_my_phone)
            .setContentTitle(title)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDeleteIntent(stopPlaybackPendingIntent)
            .addAction(0, applicationContext.getString(R.string.find_my_phone_stop_sound), stopPlaybackPendingIntent)

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) || notificationManager.canUseFullScreenIntent()) {
            val fullScreenIntent = Intent(applicationContext, FindMyPhoneActivity::class.java).apply {
                setPackage(applicationContext.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                applicationContext,
                FIND_MY_PHONE_FULL_SCREEN_ACTIVITY_PENDING_INTENT_REQUEST_CODE,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            @SuppressLint("FullScreenIntentPolicy")
            notification.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        return ForegroundInfo(
            FindMyPhonePlayer.FIND_MY_PHONE_NOTIFICATION_ID,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private suspend fun startFindMyPhone(phoneNumber: String) {
        runCatching {
            setForegroundAsync(createForegroundInfo(phoneNumber))
            findMyPhonePlayer.start(phoneNumber)
        }
    }

    private fun stopFindMyPhone() =
        findMyPhonePlayer.stop()

    override suspend fun doWork(): Result {
        val reason = Reason.fromCode(inputData.getInt(REASON_PARAM, Reason.NONE.code))
        val testingPurposes = inputData.getBoolean(TESTING_PURPOSES_PARAM, false)

        if (testingPurposes && (reason != Reason.FIND_MY_PHONE)) return Result.failure()

        val phoneNumber = inputData.getString(PHONE_NUMBER_PARAM) ?: ""
        val direction = Direction.fromCode(inputData.getInt(DIRECTION_PARAM, Direction.INCOMING.code))
        val time = inputData.getLong(TIME_PARAM, System.currentTimeMillis())

        if (! testingPurposes) {
            logger.log("Running Post call screening worker for $NORMALIZED_PHONE_NUMBER_REF", normalizedPhoneNumber = phoneNumber)

            historyRepository.add(
                HistoryEntry(
                    phoneNumber = phoneNumber,
                    type = PhoneUtils.getNumberType(phoneNumber),
                    reason = reason,
                    direction = direction,
                    timeStamp = time
                )
            )
        }

        if (direction == Direction.INCOMING) {
            when {
                reason == Reason.FIND_MY_PHONE -> {
                    startFindMyPhone(phoneNumber)
                }

                reason == Reason.FIND_MY_PHONE_CANCELLED -> {
                    stopFindMyPhone()
                }

                reason == Reason.NONE &&
                          phoneNumber.isNotEmpty() &&
                          !blockSuggestionsRepository.isAdded(phoneNumber) &&
                          !numberRepository.isIncomingAddedExact(phoneNumber) -> {
                    postSuggestionNotification(phoneNumber, time)
                }

                reason != Reason.NONE &&
                          reason != Reason.FIND_MY_PHONE &&
                          reason != Reason.WHITELISTED_NUMBER &&
                          reason != Reason.WHITELISTED_PREFIX -> {
                    postBlockedNumberNotification(phoneNumber, reason, time)
                }
            }
        }

        if (!testingPurposes)
        {
            logger.log("Post call screening worker for $NORMALIZED_PHONE_NUMBER_REF done", normalizedPhoneNumber = phoneNumber)
        }

        return Result.success()
    }
}
