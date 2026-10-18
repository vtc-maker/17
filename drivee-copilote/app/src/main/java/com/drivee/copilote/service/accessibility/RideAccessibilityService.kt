package com.drivee.copilote.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.drivee.copilote.data.repository.RideRepository
import com.drivee.copilote.data.repository.SettingsRepository
import com.drivee.copilote.domain.engine.ProfitabilityEngine
import com.drivee.copilote.domain.model.RideOffer
import com.drivee.copilote.service.overlay.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SERVICE D'ACCESSIBILITÉ — "scraper" d'écran.
 *
 * Écoute les événements des apps VTC (voir accessibility_service_config.xml),
 * lit l'arborescence des vues, délègue l'extraction à [RideParser], calcule la
 * rentabilité via [ProfitabilityEngine], puis met à jour l'overlay et l'historique.
 *
 * Points de robustesse :
 *  - throttling : on ignore les événements trop rapprochés.
 *  - déduplication : on n'affiche/enregistre pas 2× la même offre.
 *  - tout est protégé par try/catch (le service ne doit jamais crasher).
 */
@AndroidEntryPoint
class RideAccessibilityService : AccessibilityService() {

    @Inject lateinit var parser: RideParser
    @Inject lateinit var engine: ProfitabilityEngine
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var rideRepository: RideRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastEventAtMs = 0L
    private var lastOfferSignature: String? = null

    companion object {
        private const val TAG = "RideA11yService"
        private const val THROTTLE_MS = 400L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service d'accessibilité connecté.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return

        // On ne traite que les 2 types d'événements pertinents.
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        // Throttling.
        val now = System.currentTimeMillis()
        if (now - lastEventAtMs < THROTTLE_MS) return
        lastEventAtMs = now

        scope.launch {
            try {
                handleScreen(pkg)
            } catch (t: Throwable) {
                Log.w(TAG, "Erreur de traitement d'écran", t)
            }
        }
    }

    private suspend fun handleScreen(pkg: String) {
        val root = rootInActiveWindow ?: return
        val config = settingsRepository.getParserConfig()

        val offer: RideOffer? = parser.parse(root, pkg, config)

        if (offer == null || !offer.isValid) {
            // Plus d'offre valide à l'écran -> on masque l'overlay.
            OverlayService.hide(this)
            lastOfferSignature = null
            return
        }

        // Déduplication : même prix + mêmes distances -> on ne recalcule pas.
        val signature = "${offer.grossPriceEuros}|${offer.totalDistanceKm}|${offer.totalTimeMin}"
        if (signature == lastOfferSignature) return
        lastOfferSignature = signature

        val settings = settingsRepository.getSettings()
        val metrics = engine.compute(offer, settings)

        // 1) Overlay (thread principal géré dans le service overlay).
        OverlayService.show(this, metrics)

        // 2) Historique local.
        runCatching { rideRepository.save(offer, metrics) }
            .onFailure { Log.w(TAG, "Échec sauvegarde course", it) }
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrompu.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Service d'accessibilité désactivé par l'utilisateur : on coupe tout (vignette + notification).
        OverlayService.stop(this)
        scope.cancel()
        return super.onUnbind(intent)
    }
}
