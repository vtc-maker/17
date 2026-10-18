package com.drivee.copilote.data.config

import com.drivee.copilote.domain.model.Platforms

/**
 * Configuration d'extraction PAR DÉFAUT (marché français : virgule décimale, "€", "km", "min").
 *
 * Les patterns sont volontairement SOUPLES pour tolérer les variations d'UI :
 *  - point OU virgule comme séparateur décimal
 *  - espace normal / insécable / fine (ou aucun espace) avant "€" / "km" / "min"
 *  - libellé AVANT ou APRÈS la valeur ("Trajet de 18 min" / "18 min de trajet")
 *  - insensible à la casse via (?i)
 *
 * ⚠️ À AFFINER sur appareil réel en observant les textes remontés
 *    (passer DEBUG_DUMP à true dans RideParser puis lire Logcat, filtre "RideParser").
 */
object DefaultParserConfig {

    private const val NUM = "(\\d+(?:[.,]\\d{1,2})?)"
    private const val SP = "[\\s\\u00A0\\u202F]*"
    private const val GAP = "[^\\n]{0,40}?"

    private const val APPROACH = "(?:client|passager|prise en charge|pickup|away|approche|distance|de vous)"
    private const val RIDE = "(?:trajet|course|trip|destination|dropoff|dépose|voyage)"

    private val price = listOf("(?i)$NUM$SP€", "(?i)€$SP$NUM")

    private fun after(unit: String, keyword: String) = "(?i)$NUM$SP$unit$GAP$keyword"
    private fun before(unit: String, keyword: String) = "(?i)$keyword$GAP$NUM$SP$unit"

    private fun rules(priceIds: List<String>, anchors: List<String>) = PackageRules(
        priceViewIds = priceIds,
        pricePatterns = price,
        approachDistancePatterns = listOf(after("km", APPROACH)),
        approachTimePatterns = listOf(after("min", APPROACH)),
        rideDistancePatterns = listOf(after("km", RIDE), before("km", RIDE)),
        rideTimePatterns = listOf(after("min", RIDE), before("min", RIDE)),
        pickupAddressPatterns = listOf("(?i)(?:prise en charge|pickup|départ)$SP[:\\-]$SP(.+)"),
        destinationAddressPatterns = listOf("(?i)(?:destination|dropoff|dépose|arrivée)$SP[:\\-]$SP(.+)"),
        // Ancre = libellés propres à la CARTE D'OFFRE (bouton Accepter…). Le seul "€" est exclu :
        // il déclencherait l'analyse sur l'écran "Gains".
        offerAnchorPatterns = anchors
    )

    private val commonAnchors = listOf(
        "(?i)accepter", "(?i)\\baccept", "(?i)refuser", "(?i)décliner", "(?i)ignorer"
    )

    val config = ParserConfig(
        version = 2,
        packages = mapOf(
            Platforms.UBER to rules(
                priceIds = listOf("com.ubercab.driver:id/fare_amount", "com.ubercab.driver:id/product_fare"),
                anchors = commonAnchors + listOf("(?i)nouvelle course", "(?i)uberx", "(?i)comfort", "(?i)green")
            ),
            Platforms.BOLT to rules(
                priceIds = listOf("ee.mtakso.driver:id/priceText", "ee.mtakso.driver:id/order_price"),
                anchors = commonAnchors + listOf("(?i)nouvelle demande", "(?i)nouvelle commande")
            ),
            Platforms.HEETCH to rules(
                priceIds = emptyList(),
                anchors = commonAnchors + listOf("(?i)nouvelle course", "(?i)nouvelle demande")
            )
        )
    )
}
