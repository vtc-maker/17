package com.drivee.copilote.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.drivee.copilote.data.repository.RideRepository
import com.drivee.copilote.data.repository.SettingsRepository
import com.drivee.copilote.domain.engine.ProfitabilityEngine
import com.drivee.copilote.domain.model.Platforms
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
 * Ce service est lié par le système dès que l'utilisateur l'active : il tourne donc
 * en permanence en arrière-plan, sans foreground service. La pop-up est une fenêtre
 * TYPE_ACCESSIBILITY_OVERLAY gérée par [OfferOverlay].
 *
 * Robustesse :
 *  - anti-rebond (debounce) : on analyse l'écran 250 ms après le DERNIER événement
 *    (on ne rate jamais l'état final de la carte d'offre) ;
 *  - parcours de TOUTES les fenêtres de l'app VTC (la carte d'offre est souvent une
 *    fenêtre séparée) ;
 *  - masquage différé (1,2 s) quand l'offre disparaît, pour éviter le clignotement ;
 *  - déduplication et try/catch partout : le service ne doit jamais crasher.
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

    private var analyzeJob: Job? = null
    private var hideJob: Job? = null
    private var lastOfferSignature: String? = null
    private var demoIndex = 0

    companion object {
        private const val TAG = "RideA11yService"
        private const val DEBOUNCE_MS = 250L
        private const val HIDE_DELAY_MS = 1_200L

        @Volatile
        private var instance: RideAccessibilityService? = null

        /** Le service est-il réellement lié et actif (pas seulement coché dans les réglages) ? */
        val isRunning: Boolean get() = instance != null

        /** Affiche une offre d'exemple (Uber, Heetch, Bolt en alternance). false si service inactif. */
        fun showDemo(): Boolean {
            val service = instance ?: return false
            service.showNextDemo()
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ActiveNotification.show(this)
        Log.i(TAG, "Service d'accessibilité connecté.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in Platforms.packages) return

        Diagnostics.onEvent(pkg)

        // Throttle (et NON debounce) : la carte Uber/Bolt se rafraîchit en continu, un debounce
        // serait relancé sans fin et l'écran ne serait jamais analysé. On analyse au plus
        // toutes les 250 ms, en lisant l'écran au moment de l'analyse (état le plus récent).
        if (analyzeJob?.isActive == true) return
        analyzeJob = scope.launch {
            delay(DEBOUNCE_MS)
            try {
                handleScreen()
            } catch (t: Throwable) {
                Log.w(TAG, "Erreur de traitement d'écran", t)
            }
        }
    }

    private suspend fun handleScreen() {
        val roots = collectRoots()
        val pkg = roots.firstOrNull()?.packageName?.toString()
        val parsed = pkg?.let {
            parser.parseRoots(roots.filter { r -> r.packageName?.toString() == it }, it, settingsRepository.getParserConfig())
        }

        Diagnostics.onAnalysis(pkg, roots.size, parser.lastTexts, parsed)

        if (parsed == null) {
            scheduleHide()
            return
        }
        hideJob?.cancel()
        hideJob = null

        // Déduplication : même offre -> rien à refaire.
        val signature = "${parsed.sourcePackage}|${parsed.grossPriceEuros}|${parsed.totalDistanceKm}|${parsed.totalTimeMin}"
        if (signature == lastOfferSignature) return
        lastOfferSignature = signature

        val settings = settingsRepository.getSettings()
        // Bolt n'affiche pas la durée de course : estimation depuis la distance.
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
        if (instance === this) instance = null
        overlay.hide()
        sound.release()
        ActiveNotification.cancel(this)
        scope.cancel()
    }
}
