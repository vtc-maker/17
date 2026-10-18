package com.drivee.copilote.data.config

/**
 * Configuration d'extraction PAR DÉFAUT (marché français : virgule décimale, "€", "km", "min").
 *
 * Les patterns sont volontairement SOUPLES pour tolérer les variations d'UI :
 *  - accepte le point OU la virgule comme séparateur décimal : [.,]
 *  - accepte l'espace insécable et l'espace fine avant "€" / "km" : [\s\u00A0\u202F]*
 *  - insensible à la casse via (?i)
 *
 * ⚠️ Ce sont des exemples réalistes mais à AFFINER sur appareil réel en observant
 *    les textes réellement remontés (activer le log DEBUG dans RideParser).
 */
object DefaultParserConfig {

    // Nombre décimal FR/EN : "12", "12,50", "12.5"
    private const val NUM = "(\\d+(?:[.,]\\d{1,2})?)"
    private const val SP = "[\\s\\u00A0\\u202F]*"

    val config = ParserConfig(
        version = 1,
        packages = mapOf(
            "com.ubercab.driver" to PackageRules(
                priceViewIds = listOf(
                    "com.ubercab.driver:id/fare_amount",
                    "com.ubercab.driver:id/product_fare"
                ),
                pricePatterns = listOf(
                    "(?i)$NUM$SP€",
                    "(?i)€$SP$NUM"
                ),
                // "5,2 km · 8 min de trajet jusqu'au client" (approche)
                approachDistancePatterns = listOf(
                    "(?i)$NUM$SP km[^.\\n]*?(?:client|prise en charge|pickup|away|d'approche)"
                ),
                approachTimePatterns = listOf(
                    "(?i)$NUM$SP min[^.\\n]*?(?:client|prise en charge|pickup|away|d'approche)"
                ),
                // "18,4 km · 25 min de course" (trajet)
                rideDistancePatterns = listOf(
                    "(?i)$NUM$SP km[^.\\n]*?(?:trajet|course|trip|destination|dropoff)"
                ),
                rideTimePatterns = listOf(
                    "(?i)$NUM$SP min[^.\\n]*?(?:trajet|course|trip|destination|dropoff)"
                ),
                pickupAddressPatterns = listOf(
                    "(?i)(?:prise en charge|pickup)$SP[:\\-]?$SP(.+)"
                ),
                destinationAddressPatterns = listOf(
                    "(?i)(?:destination|dropoff|dépose)$SP[:\\-]?$SP(.+)"
                ),
                // Ancre = libellés propres à la CARTE D'OFFRE (bouton Accepter/Refuser…).
                // Le seul symbole "€" est volontairement exclu : il déclencherait l'analyse
                // sur l'écran "Gains" et créerait de fausses courses dans l'historique.
                offerAnchorPatterns = listOf(
                    "(?i)accepter", "(?i)accept", "(?i)refuser", "(?i)décliner",
                    "(?i)nouvelle course", "(?i)uberx", "(?i)comfort", "(?i)green"
                )
            ),
            "ee.mtakso.driver" to PackageRules(
                priceViewIds = listOf(
                    "ee.mtakso.driver:id/priceText",
                    "ee.mtakso.driver:id/order_price"
                ),
                pricePatterns = listOf(
                    "(?i)$NUM$SP€",
                    "(?i)€$SP$NUM"
                ),
                approachDistancePatterns = listOf(
                    "(?i)$NUM$SP km[^.\\n]*?(?:client|prise en charge|pickup|approche)"
                ),
                approachTimePatterns = listOf(
                    "(?i)$NUM$SP min[^.\\n]*?(?:client|prise en charge|pickup|approche)"
                ),
                rideDistancePatterns = listOf(
                    "(?i)$NUM$SP km[^.\\n]*?(?:trajet|course|destination|dépose)"
                ),
                rideTimePatterns = listOf(
                    "(?i)$NUM$SP min[^.\\n]*?(?:trajet|course|destination|dépose)"
                ),
                pickupAddressPatterns = listOf(
                    "(?i)(?:prise en charge|départ)$SP[:\\-]?$SP(.+)"
                ),
                destinationAddressPatterns = listOf(
                    "(?i)(?:destination|dépose)$SP[:\\-]?$SP(.+)"
                ),
                offerAnchorPatterns = listOf(
                    "(?i)accepter", "(?i)accept", "(?i)refuser", "(?i)décliner",
                    "(?i)nouvelle demande", "(?i)nouvelle commande"
                )
            )
        )
    )
}
