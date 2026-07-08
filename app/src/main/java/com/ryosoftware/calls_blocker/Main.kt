package com.ryosoftware.calls_blocker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject

@HiltAndroidApp
class Main : Application(), Configuration.Provider {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "blocked-calls"
        const val SUGGESTION_CHANNEL_ID = "block-suggestions"

        const val FIND_MY_PHONE_CHANNEL_ID = "find-my-phone"

        fun Context.getVibrator(): Vibrator =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val vibratorManager = getSystemService(VibratorManager::class.java)
                    vibratorManager.defaultVibrator
                }
                else -> {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }

        fun Context.safeStartActivity(intent: Intent) =
            try {
                val safeIntent = intent.apply {
                    if (this@safeStartActivity !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                startActivity(safeIntent)
            }
            catch (_: Exception) { }
        fun Context.hasReadContactsPermission(): Boolean =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        fun Context.hasReadCallLogPermission(): Boolean =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        fun Context.hasReadPhoneStatePermission(): Boolean =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        fun Context.hasPostNotificationsPermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun Context.requestIgnoreBatteryOptimizationsPermission() {
            @SuppressLint("BatteryLife")
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = "package:$packageName".toUri()
            }
            safeStartActivity(intent)
        }

        fun Context.isIgnoringBatteryOptimizations(): Boolean {
            val packageManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            return packageManager?.isIgnoringBatteryOptimizations(packageName) == true
        }

    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logger: Logger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        logger.log("App version is ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) and OS version is ${Build.VERSION.SDK_INT}")
    }

    private fun createNotificationChannel(id: String, @StringRes nameRes: Int, importance: Int) {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            id,
            getString(nameRes),
            importance
        )

        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.setShowBadge(true)

        manager.createNotificationChannel(channel)
    }
    private fun createNotificationChannels() {
        createNotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            R.string.notification_channel_name,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        createNotificationChannel(
            SUGGESTION_CHANNEL_ID,
            R.string.notification_suggestion_channel_name,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        createNotificationChannel(
            FIND_MY_PHONE_CHANNEL_ID,
            R.string.find_my_phone_activated_no_number,
            NotificationManager.IMPORTANCE_HIGH
        )
    }
}