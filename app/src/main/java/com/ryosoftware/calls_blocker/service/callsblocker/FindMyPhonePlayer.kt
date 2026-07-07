package com.ryosoftware.calls_blocker.service.callsblocker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.net.toUri
import com.ryosoftware.calls_blocker.BuildConfig
import com.ryosoftware.calls_blocker.Logger
import com.ryosoftware.calls_blocker.Main.Companion.safeStartActivity
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.ui.activities.FindMyPhoneActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds


@Singleton
class FindMyPhonePlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val logger: Logger,
) {
    companion object {
        const val ACTION_STOP = "${BuildConfig.APPLICATION_ID}.FIND_MY_PHONE_STOP"
        const val ACTION_SERVICE_DESTROYED = "${BuildConfig.APPLICATION_ID}.FIND_MY_PHONE_SERVICE_DESTROYED"
        const val EXTRA_PHONE_NUMBER = "phone-number"
        const val FIND_MY_PHONE_NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set

        fun stop() {
            isRunning = false
        }
    }

    private val ringtone = AtomicReference<Ringtone?>(null)
    private var alarmVolume  = 0

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == ACTION_STOP) {
                stop()
            }
        }
    }

    suspend fun start(phoneNumber: String) {
        var receiverRegistered = false
        try {
            logger.log("Starting find my phone")

            isRunning = true

            registerReceiver(
                context,
                stopReceiver,
                IntentFilter(ACTION_STOP),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true

            launchFindMyPhoneActivity(phoneNumber)

            playbackStart()

            while (isRunning) {
                delay(1.seconds)
            }
        } catch (e: Exception) {
            logger.log("A exception has been triggered: ${e.toString()}")
        } finally {
            if (receiverRegistered) {
                context.unregisterReceiver(stopReceiver)
            }

            stopPlayback()

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.cancel(FIND_MY_PHONE_NOTIFICATION_ID)

            context.sendBroadcast(
                Intent(ACTION_SERVICE_DESTROYED).apply {
                    setPackage(context.packageName)
                }
            )

            logger.log("Find my phone done")
        }
    }

    fun stop() {
        if (isRunning) {
            isRunning = false
        }
    }

    private fun launchFindMyPhoneActivity(phoneNumber: String) {
        val activityIntent = Intent(context, FindMyPhoneActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
        }
        context.safeStartActivity(activityIntent)
    }

    private fun getVibrator(): Vibrator =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val vibratorManager = context.getSystemService(VibratorManager::class.java)
                vibratorManager.defaultVibrator
            }
            else -> {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }

    private fun playbackStart() {
        val playableRingtone = listOfNotNull(
            settingsManager.findMyPhoneRingtoneUri.ifEmpty { null }?.toUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).firstNotNullOfOrNull { uri ->
            try {
                RingtoneManager.getRingtone(context, uri)?.also {
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
            isRunning = false
            return
        }

        getVibrator()
            .takeIf { it.hasVibrator() }
            ?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 800, 200, 800, 1000),
                    0
                )
            )

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        playableRingtone.play()

        logger.log("Ringtone is being played")

        this.ringtone.set(playableRingtone)

        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun stopPlayback() {
        ringtone.getAndSet(null)?.let { r ->
            r.stop()

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                alarmVolume,
                0
            )
        }

        getVibrator().takeIf { it.hasVibrator() }?.cancel()
    }
}
