package com.drivee.copilote.service.accessibility

import com.drivee.copilote.data.config.DefaultParserConfig
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
}
