package com.ryosoftware.calls_blocker.service.callsblocker

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.ui.activities.EXTRA_OPEN_HISTORY
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.Main
import com.ryosoftware.calls_blocker.Main.Companion.NOTIFICATION_CHANNEL_ID
import com.ryosoftware.calls_blocker.Main.Companion.SUGGESTION_CHANNEL_ID
import com.ryosoftware.calls_blocker.Main.Companion.hasPostNotificationsPermission
import com.ryosoftware.calls_blocker.Main.Companion.safeStartActivity
import com.ryosoftware.calls_blocker.ui.activities.MainActivity
import com.ryosoftware.calls_blocker.R
import com.ryosoftware.calls_blocker.NORMALIZED_PHONE_NUMBER_REF
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.formatPhoneNumber
import com.ryosoftware.calls_blocker.data.db.HistoryEntry
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.data.repository.HistoryRepository
import com.ryosoftware.calls_blocker.data.repository.BlockSuggestionsRepository
import com.ryosoftware.calls_blocker.data.repository.NumberRepository
import com.ryosoftware.calls_blocker.receiver.BlockSuggestionReceiver
import com.ryosoftware.calls_blocker.ui.activities.FindMyPhoneActivity
import com.ryosoftware.calls_blocker.viewmodel.HistoryViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

const val PHONE_NUMBER_PARAM = "phone-number"
const val REASON_PARAM = "reason"
const val TIME_PARAM = "time"

private const val FIND_MY_PHONE_NOTIFICATION_ID = 1
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
    private val settingsManager: SettingsManager,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.FIND_MY_PHONE_STOP"
        const val ACTION_SERVICE_DESTROYED = "${BuildConfig.APPLICATION_ID}.FIND_MY_PHONE_SERVICE_DESTROYED"
        const val EXTRA_PHONE_NUMBER = "phone-number"
        fun stop(context: Context) {
            val intent = Intent(ACTION_STOP).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
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

        val formattedNumber = formatPhoneNumber(phoneNumber)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_HISTORY, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reasonString = HistoryViewModel.getReasonString(applicationContext, reason)
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

        val formattedNumber = formatPhoneNumber(phoneNumber)
        val blockIntent = Intent(applicationContext, BlockSuggestionReceiver::class.java).apply {
            action = BlockSuggestionReceiver.ACTION_BLOCK
            putExtra(BlockSuggestionReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            applicationContext, phoneNumber.hashCode(), blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(applicationContext, BlockSuggestionReceiver::class.java).apply {
            action = BlockSuggestionReceiver.ACTION_DISMISS
            putExtra(BlockSuggestionReceiver.EXTRA_PHONE_NUMBER, phoneNumber)
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
        notificationManager.notify(getNotificationId(phoneNumber), notification)
    }

    private val ringtone = AtomicReference<Ringtone?>(null)

    private var isFindMyPhoneRunning = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            logger.log("Received '$action' event")

            if (action == ACTION_STOP) {
                isFindMyPhoneRunning = false
            }
        }
    }

    private fun createForegroundInfo(phoneNumber: String): ForegroundInfo {
        val formattedNumber = formatPhoneNumber(phoneNumber)
        val stopPlaybackIntent = Intent(ACTION_STOP).apply {
            setPackage(applicationContext.packageName)
        }

        val stopPlaybackPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            FIND_MY_PHONE_FULL_STOP_PLAYBACK_REQUEST_CODE,
            stopPlaybackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, Main.FIND_MY_PHONE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification_find_my_phone)
            .setContentTitle(applicationContext.getString(R.string.find_my_phone_activated_from, formattedNumber))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
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

        return ForegroundInfo(FIND_MY_PHONE_NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun launchFindMyPhoneActivity(phoneNumber: String) {
        val activityIntent = Intent(applicationContext, FindMyPhoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
        }
        applicationContext.safeStartActivity(activityIntent)
    }

    private fun playFindMyPhone() {
        val playableRingtone = listOfNotNull(
            settingsManager.findMyPhoneRingtoneUri.ifEmpty { null }?.toUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).firstNotNullOfOrNull { uri ->
            try {
                RingtoneManager.getRingtone(applicationContext, uri)?.also {
                    it.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    it.isLooping = true
                }
            } catch (_: Exception) {
                null
            }
        }

        if (playableRingtone == null) {
            logger.log("No playable ringtone found")
            isFindMyPhoneRunning = false
            return
        }

        playableRingtone.play()

        logger.log("Ringtone is being played")

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = applicationContext.getSystemService(VibratorManager::class.java)
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator())
        {
            val vibrationPattern = longArrayOf(
                0,
                800, 200,
                800, 1000
            )
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
        }

        this@PostServiceWorker.ringtone.set(playableRingtone)
    }

    private fun stopFindMyPhone() {
        isFindMyPhoneRunning = false

        ringtone.getAndSet(null)?.stop()

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = applicationContext.getSystemService(VibratorManager::class.java)
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        vibrator.cancel()

        if (applicationContext.hasPostNotificationsPermission()) {
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.cancel(FIND_MY_PHONE_NOTIFICATION_ID)
        }

        applicationContext.sendBroadcast(
            Intent(ACTION_SERVICE_DESTROYED).apply {
                setPackage(applicationContext.packageName)
            }
        )
    }

    private suspend fun startFindMyPhone(phoneNumber: String) {
        var receiverRegistered = false

        try {
            logger.log("Starting find my phone")

            isFindMyPhoneRunning = true

            try {
                setForegroundAsync(createForegroundInfo(phoneNumber))
            } catch (exception: Exception) {
                logger.log("A exception has been triggered while trying to set foreground: ${exception.toString()}")
            }

            registerReceiver(
                applicationContext,
                receiver,
                IntentFilter(ACTION_STOP),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true

            launchFindMyPhoneActivity(phoneNumber)
            playFindMyPhone()

            while (isFindMyPhoneRunning && (!isStopped)) {
                delay(1.seconds)
            }
        } catch (exception: Exception) {
            logger.log("A exception has been triggered: ${exception.toString()}")
        } finally {
            if (receiverRegistered) {
                applicationContext.unregisterReceiver(receiver)
            }

            stopFindMyPhone()

            logger.log("Find my phone done")
        }
    }

    override suspend fun doWork(): Result {
        val phoneNumber = inputData.getString(PHONE_NUMBER_PARAM) ?: return Result.failure()
        val reason = Reason.fromCode(inputData.getInt(REASON_PARAM, Reason.REASON_NONE.code))
        val time = inputData.getLong(TIME_PARAM, System.currentTimeMillis())

        logger.log("Running Post call screening worker for $NORMALIZED_PHONE_NUMBER_REF", normalizedPhoneNumber = phoneNumber)

        historyRepository.add(
            HistoryEntry(
                phoneNumber = phoneNumber,
                reason = reason,
                timeStamp = time
            )
        )

        when {
            reason == Reason.REASON_FIND_MY_PHONE -> {
                startFindMyPhone(phoneNumber)
            }

            reason == Reason.REASON_NONE &&
                    phoneNumber.isNotEmpty() &&
                    !blockSuggestionsRepository.isAdded(phoneNumber) &&
                    !numberRepository.isAddedExact(phoneNumber) -> {
                postSuggestionNotification(phoneNumber, time)
            }

            reason != Reason.REASON_FIND_MY_PHONE &&
                    reason != Reason.REASON_WHITELISTED_NUMBER &&
                    reason != Reason.REASON_WHITELISTED_PREFIX -> {
                postBlockedNumberNotification(phoneNumber, reason, time)
            }
        }

        logger.log("Post call screening worker for $NORMALIZED_PHONE_NUMBER_REF done", normalizedPhoneNumber = phoneNumber)

        return Result.success()
    }
}
