package com.vtccopilot.app.service.accessibility

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vtccopilot.app.R
import com.vtccopilot.app.ui.MainActivity

/**
 * Notification discrète "VTC Copilot actif — Analyse des offres en cours…",
 * affichée tant que le service d'accessibilité tourne.
 */
object ActiveNotification {
    private const val CHANNEL_ID = "vtccopilot_active"
    private const val NOTIF_ID = 4242
    const val ACTION_TOGGLE_PAUSE = "com.vtccopilot.app.TOGGLE_PAUSE"
    const val ACTION_STOP = "com.vtccopilot.app.STOP"

    @SuppressLint("MissingPermission")
    fun show(context: Context, paused: Boolean = ServiceState.isPaused(context)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Analyse des offres", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Indique que VTC Copilot analyse les offres Uber, Bolt et Heetch."
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun action(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code, Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (paused) "VTC Copilot en pause" else "VTC Copilot actif")
            .setContentText(if (paused) "Analyse suspendue — touchez Reprendre" else "Analyse des offres en cours…")
            .addAction(0, if (paused) "Reprendre" else "Pause", action(ACTION_TOGGLE_PAUSE, 1))
            .addAction(0, "Arrêter", action(ACTION_STOP, 2))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, notification) }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }
}
