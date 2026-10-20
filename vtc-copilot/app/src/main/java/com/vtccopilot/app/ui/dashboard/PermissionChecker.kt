package com.vtccopilot.app.ui.dashboard

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.vtccopilot.app.service.accessibility.RideAccessibilityService

/**
 * Utilitaires de vérification des permissions critiques.
 * (Hors ViewModel car ils nécessitent un Context "vivant" et sont ré-évalués
 *  à chaque retour au premier plan.)
 */
object PermissionChecker {

    /** L'utilisateur a-t-il activé notre service d'accessibilité dans les Réglages ? */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, RideAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Le système peut stocker la forme courte ("pkg/.Service") ou longue :
        // on compare des ComponentName, pas des chaînes.
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next()) ?: continue
            if (cn == expected) return true
        }
        return false
    }

    /** Les notifications de l'app sont-elles autorisées ? */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** L'app est-elle exclue de l'optimisation de batterie (Doze) ? */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
