package com.firefly.app.radio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.firefly.app.MainActivity
import com.firefly.app.R
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.data.db.PingEntity

/** Haptics and system notifications for incoming pings (PRD C2, F1). */
class Alerts(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PINGS, context.getString(R.string.notif_pings_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, context.getString(R.string.notif_sos_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setBypassDnd(true)
            },
        )
    }

    fun onPing(ping: PingEntity, senderName: String, text: String, appInForeground: Boolean) {
        val sos = ping.code == Codebook.HELP
        vibrate(if (sos) SOS_PATTERN else PING_PATTERN)
        if (!appInForeground || sos) notify(ping, senderName, text, sos)
    }

    private fun vibrate(pattern: LongArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun notify(ping: PingEntity, senderName: String, text: String, sos: Boolean) {
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, if (sos) CHANNEL_SOS else CHANNEL_PINGS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (sos) context.getString(R.string.notif_sos_title, senderName) else senderName)
            .setContentText(text)
            .setPriority(if (sos) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (sos) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(sos)
            .build()
        nm.notify((PING_NOTIFICATION_BASE + (ping.id % 1000)).toInt(), n)
    }

    private companion object {
        const val CHANNEL_PINGS = "firefly_pings"
        const val CHANNEL_SOS = "firefly_sos"
        const val PING_NOTIFICATION_BASE = 1000L
        val PING_PATTERN = longArrayOf(0, 120, 80, 120)
        val SOS_PATTERN = longArrayOf(0, 300, 150, 300, 150, 300, 300, 600, 150, 600, 150, 600)
    }
}
