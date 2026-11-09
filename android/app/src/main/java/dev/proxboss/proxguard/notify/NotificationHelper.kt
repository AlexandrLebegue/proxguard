package dev.proxboss.proxguard.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.proxboss.proxguard.MainActivity
import dev.proxboss.proxguard.R
import dev.proxboss.proxguard.data.alerts.AlertEvent

object NotificationHelper {

    const val CHANNEL_ID = "proxguard_alerts_v1"
    const val EXTRA_NAVIGATE = "dev.proxboss.proxguard.NAVIGATE_TO"
    const val ROUTE_ALERTS = "alerts"
    private const val ACCENT = "#22D3EE"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableLights(true)
            lightColor = Color.parseColor(ACCENT)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notificationsAllowed(context: Context): Boolean {
        val permissionGranted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyEvents(context: Context, events: List<AlertEvent>) {
        if (events.isEmpty() || !notificationsAllowed(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        events.forEach { event ->
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE, ROUTE_ALERTS)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                100 + event.key.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setColor(Color.parseColor(ACCENT))
                .setContentTitle(event.title)
                .setContentText(event.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.message))
                .setSubText("ProxGuard · ${event.severity.label}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setWhen(event.timestamp)
                .setContentIntent(pendingIntent)
                .addAction(0, "Ouvrir", pendingIntent)
                .build()
            runCatching { manager.notify(event.key.hashCode(), notification) }
        }
    }
}
