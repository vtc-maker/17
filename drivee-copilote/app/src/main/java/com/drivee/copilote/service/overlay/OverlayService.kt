package com.drivee.copilote.service.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.drivee.copilote.R
import com.drivee.copilote.domain.model.ProfitabilityMetrics
import com.drivee.copilote.domain.model.ProfitabilityScore
import java.util.Locale

/**
 * ⭐ SERVICE OVERLAY — affiche une vignette flottante (WindowManager) par-dessus
 * l'application VTC active, sans faire planter l'app.
 *
 * Cycle de vie & robustesse :
 *  - Foreground service (notification obligatoire) pour survivre en arrière-plan.
 *  - La vue est ajoutée / mise à jour / retirée sur le THREAD PRINCIPAL uniquement.
 *  - Ajout unique : on ne rajoute jamais 2× la même vue (garde isAdded).
 *  - Vérification de la permission overlay avant tout addView.
 *  - Auto-disparition après [AUTO_DISMISS_MS] si aucune nouvelle offre.
 *  - Bulle déplaçable (drag) + bouton fermer.
 *
 * On pilote le service via des actions statiques [show] / [hide] pour éviter de
 * multiplier les instances.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable: Runnable = Runnable { removeOverlay() }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "drivee_overlay"
        private const val NOTIF_ID = 4242
        private const val AUTO_DISMISS_MS = 20_000L

        private const val ACTION_SHOW = "com.drivee.copilote.SHOW"
        private const val ACTION_HIDE = "com.drivee.copilote.HIDE"
        private const val ACTION_STOP = "com.drivee.copilote.STOP"

        private const val EXTRA_NET_HOURLY = "net_hourly"
        private const val EXTRA_NET_PER_KM = "net_per_km"
        private const val EXTRA_GROSS_PER_KM = "gross_per_km"
        private const val EXTRA_SCORE = "score"

        /** Le service est-il actuellement vivant ? (évite de le démarrer juste pour "masquer"). */
        @Volatile
        private var isRunning = false

        /** Affiche/actualise la vignette avec les [metrics] fournies. */
        fun show(context: Context, metrics: ProfitabilityMetrics) {
            // Sans permission overlay, on n'essaie même pas (évite un crash sécurité).
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Permission overlay absente — affichage ignoré.")
                return
            }
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_NET_HOURLY, metrics.netHourlyRateEuros ?: Double.NaN)
                putExtra(EXTRA_NET_PER_KM, metrics.netPricePerKmEuros ?: Double.NaN)
                putExtra(EXTRA_GROSS_PER_KM, metrics.grossPricePerKmEuros ?: Double.NaN)
                putExtra(EXTRA_SCORE, metrics.score.name)
            }
            startFgsCompat(context, intent)
        }

        /** Masque la vignette (le service reste prêt pour la prochaine offre). */
        fun hide(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, OverlayService::class.java).apply { action = ACTION_HIDE }
            runCatching { context.startService(intent) }
        }

        /** Arrête complètement le service (ex: service d'accessibilité désactivé). */
        fun stop(context: Context) {
            if (!isRunning) return
            val intent = Intent(context, OverlayService::class.java).apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }

        private fun startFgsCompat(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "startForegroundService a échoué", it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val score = runCatching {
                    ProfitabilityScore.valueOf(intent.getStringExtra(EXTRA_SCORE) ?: "UNKNOWN")
                }.getOrDefault(ProfitabilityScore.UNKNOWN)

                showOrUpdate(
                    netHourly = intent.getDoubleExtra(EXTRA_NET_HOURLY, Double.NaN),
                    netPerKm = intent.getDoubleExtra(EXTRA_NET_PER_KM, Double.NaN),
                    grossPerKm = intent.getDoubleExtra(EXTRA_GROSS_PER_KM, Double.NaN),
                    score = score
                )
            }
            ACTION_HIDE -> removeOverlay()
            ACTION_STOP -> {
                removeOverlay()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_NOT_STICKY : ne pas recréer automatiquement sans données.
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------
    //  Gestion de la vue flottante (toujours sur le thread principal).
    // ------------------------------------------------------------------
    private fun showOrUpdate(
        netHourly: Double,
        netPerKm: Double,
        grossPerKm: Double,
        score: ProfitabilityScore
    ) {
        mainHandler.post {
            try {
                if (overlayView == null) inflateOverlay()
                bindData(netHourly, netPerKm, grossPerKm, score)

                // Programme la disparition automatique.
                mainHandler.removeCallbacks(autoDismissRunnable)
                mainHandler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)
            } catch (t: Throwable) {
                Log.w(TAG, "showOrUpdate a échoué", t)
            }
        }
    }

    private fun inflateOverlay() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 160
        }

        // Bouton fermer.
        view.findViewById<View>(R.id.overlay_close)?.setOnClickListener { removeOverlay() }

        // Rendre la bulle déplaçable.
        enableDrag(view, params)

        // addView protégé : si déjà ajoutée, on ne double pas.
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
        overlayView = view
        layoutParams = params
    }

    private fun bindData(
        netHourly: Double,
        netPerKm: Double,
        grossPerKm: Double,
        score: ProfitabilityScore
    ) {
        val view = overlayView ?: return

        view.findViewById<TextView>(R.id.overlay_hourly)?.text =
            if (netHourly.isNaN()) "—" else String.format(Locale.FRANCE, "%.1f €/h", netHourly)

        view.findViewById<TextView>(R.id.overlay_per_km)?.text =
            if (netPerKm.isNaN()) "—" else String.format(Locale.FRANCE, "%.2f €/km", netPerKm)

        view.findViewById<TextView>(R.id.overlay_gross_per_km)?.text =
            if (grossPerKm.isNaN()) "" else String.format(Locale.FRANCE, "brut %.2f €/km", grossPerKm)

        // Pastille couleur.
        val dot = view.findViewById<View>(R.id.overlay_score_dot)
        val colorRes = when (score) {
            ProfitabilityScore.GREEN -> R.color.score_green
            ProfitabilityScore.ORANGE -> R.color.score_orange
            ProfitabilityScore.RED -> R.color.score_red
            ProfitabilityScore.UNKNOWN -> R.color.score_unknown
        }
        dot?.background?.setTint(getColor(colorRes))
    }

    private fun enableDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (e.rawX - touchX).toInt()
                    params.y = initialY + (e.rawY - touchY).toInt()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeOverlay() {
        mainHandler.post {
            mainHandler.removeCallbacks(autoDismissRunnable)
            overlayView?.let { v ->
                runCatching { if (v.parent != null) windowManager.removeView(v) }
                    .onFailure { Log.w(TAG, "removeView a échoué", it) }
            }
            overlayView = null
            layoutParams = null
        }
    }

    // ------------------------------------------------------------------
    //  Foreground service : notification obligatoire.
    // ------------------------------------------------------------------
    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Assistant Drivee",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Vignette de rentabilité par-dessus l'application VTC." }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Drivee actif")
            .setContentText("Analyse des offres en cours…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        // Android 14+ exige le TYPE de foreground service (specialUse, déclaré dans le Manifest).
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else 0

        runCatching { ServiceCompat.startForeground(this, NOTIF_ID, notification, fgsType) }
            .onFailure { Log.w(TAG, "startForeground a échoué", it) }
    }

    override fun onDestroy() {
        removeOverlay()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
