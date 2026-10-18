package com.drivee.copilote.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.drivee.copilote.data.repository.RideRepository
import com.drivee.copilote.data.repository.SettingsRepository
import com.drivee.copilote.domain.engine.ProfitabilityEngine
import com.drivee.copilote.domain.model.Platforms
import com.drivee.copilote.domain.model.RideOffer
import com.drivee.copilote.service.overlay.FloatingBubble
import com.drivee.copilote.service.overlay.OfferDisplay
import com.drivee.copilote.service.overlay.OfferOverlay
import com.drivee.copilote.service.overlay.VerdictSound
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SERVICE D'ACCESSIBILITÉ — lit les offres Uber / Bolt / Heetch et affiche la pop-up.
 *
 * Lecture en 2 temps :
 *  1. NŒUDS d'accessibilité (rapide, toutes les 250 ms max) ;
 *  2. si aucune offre n'est trouvée : OCR embarqué d'une capture d'écran (Android 11+,
 *     1 fois / s max). Indispensable quand l'app VTC n'expose pas les textes de sa carte
 *     d'offre (constaté sur Uber : seul l'écran d'accueil est lisible).
 *
 * Throttle (et non debounce) : la carte Uber/Bolt se rafraîchit en continu.
 * Bulle flottante = service actif ; un appui met en pause / reprend.
 */
@AndroidEntryPoint
class RideAccessibilityService : AccessibilityService() {

    @Inject lateinit var parser: RideParser
    @Inject lateinit var engine: ProfitabilityEngine
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var rideRepository: RideRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val overlay by lazy { OfferOverlay(this) }
    private val sound by lazy { VerdictSound(this) }
    private val ocr by lazy { ScreenOcr(this) }
    private val bubble by lazy { FloatingBubble(this) { togglePause() } }

    private var analyzeJob: Job? = null
    private var hideJob: Job? = null
    private var lastOfferSignature: String? = null
    @Volatile private var lastEventPkg: String? = null
    private var demoIndex = 0

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ActiveNotification.ACTION_TOGGLE_PAUSE -> togglePause()
                ActiveNotification.ACTION_STOP -> disableSelf()
            }
        }
    }

    companion object {
        private const val TAG = "RideA11yService"
        private const val THROTTLE_MS = 250L
        private const val HIDE_DELAY_MS = 1_500L

        @Volatile
        private var instance: RideAccessibilityService? = null

        val isRunning: Boolean get() = instance != null
        val ocrSupported: Boolean get() = instance?.ocr?.isSupported ?: (android.os.Build.VERSION.SDK_INT >= 30)

        fun showDemo(): Boolean {
            val service = instance ?: return false
            service.showNextDemo()
            return true
        }

        fun setPaused(context: Context, paused: Boolean) {
            ServiceState.setPaused(context, paused)
            instance?.applyState()
        }

        fun setBubbleEnabled(context: Context, enabled: Boolean) {
            ServiceState.setBubbleEnabled(context, enabled)
            instance?.applyState()
        }

        /** Arrête complètement le service (il faudra le réactiver dans Accessibilité). */
        fun stop(): Boolean {
            val service = instance ?: return false
            service.disableSelf()
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ContextCompat.registerReceiver(
            this, actionReceiver,
            IntentFilter().apply {
                addAction(ActiveNotification.ACTION_TOGGLE_PAUSE)
                addAction(ActiveNotification.ACTION_STOP)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        applyState()
        Log.i(TAG, "Service d'accessibilité connecté.")
    }

    /** Synchronise bulle, notification et pop-up avec l'état pause / bulle. */
    private fun applyState() {
        val paused = ServiceState.isPaused(this)
        if (paused) {
            overlay.hide()
            lastOfferSignature = null
        }
        if (ServiceState.isBubbleEnabled(this)) bubble.show(active = !paused) else bubble.hide()
        ActiveNotification.show(this, paused)
    }

    private fun togglePause() {
        val paused = !ServiceState.isPaused(this)
        ServiceState.setPaused(this, paused)
        applyState()
        Toast.makeText(this, if (paused) "Drivee en pause" else "Drivee actif", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in Platforms.packages) return
        Diagnostics.onEvent(pkg)
        if (ServiceState.isPaused(this)) return
        lastEventPkg = pkg

        if (analyzeJob?.isActive == true) return
        analyzeJob = scope.launch {
            delay(THROTTLE_MS)
            try {
                handleScreen()
            } catch (t: Throwable) {
                Log.w(TAG, "Erreur de traitement d'écran", t)
            }
        }
    }

    private suspend fun handleScreen() {
        val roots = collectRoots()
        val pkg = roots.firstOrNull()?.packageName?.toString() ?: lastEventPkg ?: return
        val config = settingsRepository.getParserConfig()

        // 1) Nœuds d'accessibilité.
        val sameApp = roots.filter { it.packageName?.toString() == pkg }
        var parsed: RideOffer? = parser.parseRoots(sameApp, pkg, config)
        Diagnostics.onAnalysis("NŒUDS", pkg, roots.size, parser.lastTexts, parsed)

        // 2) Plan B : OCR de l'écran (nos propres fenêtres exclues).
        if (parsed == null) {
            val lines = ocr.readLines(listOfNotNull(overlay.visibleRect(), bubble.visibleRect()))
                ?: return // capture trop récente : on ne décide rien, prochaine analyse dans 250 ms
            parsed = parser.parseTexts(lines, pkg, config)
            Diagnostics.onAnalysis("OCR", pkg, roots.size, lines, parsed)
        }

        if (parsed == null) {
            scheduleHide()
            return
        }
        hideJob?.cancel()
        hideJob = null

        // Déduplication (prix seul : l'OCR peut lire les km légèrement différemment d'une image à l'autre).
        val signature = "${parsed.sourcePackage}|${parsed.grossPriceEuros}"
        if (signature == lastOfferSignature) return
        lastOfferSignature = signature

        val settings = settingsRepository.getSettings()
        val offer = parsed.withEstimatedTimes(settings.avgSpeedKmh)
        val metrics = engine.compute(offer, settings)
        overlay.show(OfferDisplay.from(offer, metrics))
        if (settings.soundEnabled) sound.play(metrics.score)

        runCatching { rideRepository.save(offer, metrics) }
            .onFailure { Log.w(TAG, "Échec sauvegarde course", it) }
    }

    /** Racines des fenêtres des apps VTC, la plus haute d'abord. */
    private fun collectRoots(): List<AccessibilityNodeInfo> {
        val fromWindows = runCatching {
            windows.mapNotNull { it.root }
                .filter { it.packageName?.toString() in Platforms.packages }
        }.getOrDefault(emptyList())
        if (fromWindows.isNotEmpty()) return fromWindows
        return listOfNotNull(rootInActiveWindow?.takeIf { it.packageName?.toString() in Platforms.packages })
    }

    private fun scheduleHide() {
        if (hideJob?.isActive == true) return
        hideJob = scope.launch {
            delay(HIDE_DELAY_MS)
            lastOfferSignature = null
            overlay.hide()
        }
    }

    private fun showNextDemo() {
        val offer = OfferDisplay.demoOffers[demoIndex % OfferDisplay.demoOffers.size]
        demoIndex++
        scope.launch {
            val settings = settingsRepository.getSettings()
            val metrics = engine.compute(offer, settings)
            overlay.show(OfferDisplay.from(offer, metrics))
            if (settings.soundEnabled) sound.play(metrics.score)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrompu.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance !== this) return
        instance = null
        runCatching { unregisterReceiver(actionReceiver) }
        overlay.hide()
        bubble.hide()
        sound.release()
        ocr.release()
        ActiveNotification.cancel(this)
        scope.cancel()
    }
}
