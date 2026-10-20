package com.vtccopilot.app.service.accessibility

import com.vtccopilot.app.data.config.DefaultParserConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM du parser sur des textes RÉALISTES tels que remontés par l'accessibilité.
 * (La lecture de l'arbre AccessibilityNodeInfo n'est pas testée ici — elle nécessite un appareil.)
 */
class RideParserTest {

    private val parser = RideParser()
    private val config = DefaultParserConfig.config

    @Test
    fun `offre Uber complete avec libelles approche et course`() {
        val texts = listOf(
            "Nouvelle course",
            "UberX",
            "14,50 €",
            "4,2 km · 9 min jusqu'au client",
            "12,8 km · 22 min de trajet",
            "Prise en charge : 12 rue de Rivoli, Paris",
            "Destination : Gare de Lyon, Paris",
            "Accepter"
        )

        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(14.5, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(4.2, offer.approachDistanceKm!!, 0.001)
        assertEquals(9.0, offer.approachTimeMin!!, 0.001)
        assertEquals(12.8, offer.rideDistanceKm!!, 0.001)
        assertEquals(22.0, offer.rideTimeMin!!, 0.001)
        assertEquals(17.0, offer.totalDistanceKm!!, 0.001)
        assertEquals(31.0, offer.totalTimeMin!!, 0.001)
        assertEquals("12 rue de Rivoli, Paris", offer.pickupAddress)
        assertEquals("Gare de Lyon, Paris", offer.destinationAddress)
    }

    @Test
    fun `offre Bolt sans libelles - repli heuristique min=approche max=course`() {
        val texts = listOf(
            "Nouvelle demande",
            "€ 9,80",
            "3 min · 1,1 km",
            "15 min · 7,4 km",
            "Accepter"
        )

        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)

        assertNotNull(offer)
        assertEquals(9.8, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(1.1, offer.approachDistanceKm!!, 0.001)
        assertEquals(7.4, offer.rideDistanceKm!!, 0.001)
        assertEquals(3.0, offer.approachTimeMin!!, 0.001)
        assertEquals(15.0, offer.rideTimeMin!!, 0.001)
    }

    @Test
    fun `le resource-id du prix est prioritaire sur la regex`() {
        val texts = listOf("Accepter", "Bonus 2,00 €", "18,00 €")
        val byViewId = mapOf("com.ubercab.driver:id/fare_amount" to "18,00 €")

        val offer = parser.parseTexts(
            texts + "5 km · 10 min", "com.ubercab.driver", config, byViewId
        )

        assertNotNull(offer)
        assertEquals(18.0, offer!!.grossPriceEuros!!, 0.001)
    }

    @Test
    fun `ecran Gains sans bouton Accepter n'est PAS une offre`() {
        val texts = listOf("Vos gains", "Aujourd'hui", "245,30 €", "12 courses", "6 h 30 min")

        assertNull(parser.parseTexts(texts, "com.ubercab.driver", config))
    }

    @Test
    fun `package inconnu renvoie null`() {
        assertNull(parser.parseTexts(listOf("Accepter", "10 €", "5 km"), "com.autre.app", config))
    }

    @Test
    fun `prix sans distance ni duree est invalide`() {
        assertNull(parser.parseTexts(listOf("Accepter", "10,00 €"), "com.ubercab.driver", config))
    }

    @Test
    fun `decimale avec point et espace insecable sont toleres`() {
        val texts = listOf("Accept", "12.5\u00A0€", "6.3\u00A0km · 14\u00A0min")

        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(12.5, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(6.3, offer.totalDistanceKm!!, 0.001)
        assertEquals(14.0, offer.totalTimeMin!!, 0.001)
    }

    @Test
    fun `format Uber FR - libelle avant la valeur et km entre parentheses`() {
        val texts = listOf(
            "UberX",
            "12,40 €",
            "4 min (1,2 km) de distance",
            "Trajet de 18 min (9,4 km)",
            "Appuyez pour accepter"
        )

        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(12.4, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(1.2, offer.approachDistanceKm!!, 0.001)
        assertEquals(4.0, offer.approachTimeMin!!, 0.001)
        assertEquals(9.4, offer.rideDistanceKm!!, 0.001)
        assertEquals(18.0, offer.rideTimeMin!!, 0.001)
    }

    @Test
    fun `offre Heetch reconnue`() {
        val texts = listOf("Nouvelle course", "14,00 €", "6 min · 3 km", "20 min · 12 km", "Accepter")

        val offer = parser.parseTexts(texts, "com.heetch.driver", config)

        assertNotNull(offer)
        assertEquals(14.0, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(15.0, offer.totalDistanceKm!!, 0.001)
        assertEquals(26.0, offer.totalTimeMin!!, 0.001)
    }

    @Test
    fun `capture reelle Uber - a moins de 1 min (0 km) puis Course de 24 min (8,1 km)`() {
        val texts = listOf(
            "UberX", "17,51 €", "5,00", "Hors frais de service",
            "à <1 min (0 km)", "Rue de la Montagne 55, Bruxelles",
            "Course de 24 min (8.1 km)", "Av. du Manoir 23, Uccle",
            "Accepter"
        )

        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(17.51, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(0.0, offer.approachDistanceKm!!, 0.001)
        assertEquals(1.0, offer.approachTimeMin!!, 0.001)
        assertEquals(8.1, offer.rideDistanceKm!!, 0.001)
        assertEquals(24.0, offer.rideTimeMin!!, 0.001)
    }

    @Test
    fun `Bolt - bonus +3,11 euros proche des km ignore, prix Net TTC retenu`() {
        val texts = listOf(
            "Refuser", "4 min", "1.2 km", "+3.11€", "Nice Ville • 10.2 km",
            "Bolt • Karim", "4.9", "13.11€ · Net, TTC", "Accepter"
        )
        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)
        assertEquals(13.11, offer!!.grossPriceEuros!!, 0.001)
    }

    @Test
    fun `capture reelle Bolt - approche en metres, course sans duree`() {
        val texts = listOf(
            "Refuser", "<1 min", "135 m",
            "Zone Accueil - Entrée, Parc Astérix",
            "Gare de Gagny, Gagny • 35.5 km",
            "Green • Amira", "5.0", "1.2x forte demande", "Sur l'app",
            "35.51€ · Net, TTC", "Accepter"
        )

        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)

        assertNotNull(offer)
        assertEquals(35.51, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(0.135, offer.approachDistanceKm!!, 0.0001)
        assertEquals(1.0, offer.approachTimeMin!!, 0.001)
        assertEquals(35.5, offer.rideDistanceKm!!, 0.001)
        assertNull(offer.rideTimeMin) // Bolt n'affiche pas la durée de course

        // Estimation à 35 km/h : 35.5 km -> ~60.9 min de course
        val est = offer.withEstimatedTimes(35.0)
        assertEquals(60.86, est.rideTimeMin!!, 0.01)
        assertEquals(true, est.timeEstimated)
    }

    @Test
    fun `OCR Uber - gains 0,00 euro en haut ignores, prix de l'offre retenu`() {
        val ocrLines = listOf(
            "18:25", "0,00€", "Vous êtes en ligne.",
            "Comfort", "Exclusivité", "39,71 €", "4,81", "Montant net de frais",
            "11 min (4.5 km)", "Terminal 1, Terminal 1 - D4, 06206, Nice",
            "Course de 28.1 km", "52 Bd d'Italie, 98000, Monaco", "Accepter"
        )

        val offer = parser.parseTexts(ocrLines, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(39.71, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(32.6, offer.totalDistanceKm!!, 0.001)
    }

    @Test
    fun `video Uber Nice-Monaco - approche avec duree, course avec seulement les km`() {
        val texts = listOf(
            "Comfort", "Exclusivité", "39,71 €", "4,81", "Montant net de frais",
            "11 min (4.5 km)", "Terminal 1, Terminal 1 - D4, 06206, Nice",
            "Course de 28.1 km", "52 Bd d'Italie, 98000, Monaco", "Accepter"
        )

        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)

        assertNotNull(offer)
        assertEquals(39.71, offer!!.grossPriceEuros!!, 0.001)
        assertEquals(4.5, offer.approachDistanceKm!!, 0.001)
        assertEquals(11.0, offer.approachTimeMin!!, 0.001)
        assertEquals(28.1, offer.rideDistanceKm!!, 0.001)
        assertNull(offer.rideTimeMin)
    }

    @Test
    fun `les minutes ne sont pas lues comme des metres`() {
        val offer = parser.parseTexts(listOf("Accepter", "9,80 €", "3 min · 1,1 km", "15 min · 7,4 km"), "ee.mtakso.driver", config)
        assertEquals(8.5, offer!!.totalDistanceKm!!, 0.001)
    }

    @Test
    fun `km sans espace accepte`() {
        val offer = parser.parseTexts(listOf("Accepter", "9,80€", "7,4km", "15min"), "ee.mtakso.driver", config)

        assertNotNull(offer)
        assertEquals(7.4, offer!!.totalDistanceKm!!, 0.001)
        assertEquals(15.0, offer.totalTimeMin!!, 0.001)
    }
}
