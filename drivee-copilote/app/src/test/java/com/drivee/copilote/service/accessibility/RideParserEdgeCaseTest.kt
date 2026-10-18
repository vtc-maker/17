package com.drivee.copilote.service.accessibility

import com.drivee.copilote.data.config.DefaultParserConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Additional edge-case regression tests for RideParser,
 * focused on the "allMeters" false-positive risks and real-world
 * mixed addresses with numbers.
 */
class RideParserEdgeCaseTest {

    private val parser = RideParser()
    private val config = DefaultParserConfig.config

    @Test
    fun `address containing a street number is NOT parsed as meters`() {
        // "12 rue Mozart" -> digits + space + "r" (no 'm'); safe.
        val texts = listOf(
            "Refuser",
            "<1 min",
            "250 m",
            "12 rue Mozart, Paris",
            "Gare de Lyon, Paris • 7.4 km",
            "35.00€ · Net, TTC",
            "Accepter"
        )
        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)
        assertNotNull(offer)
        // The only genuine meters value is 250 m -> 0.250 km (approach).
        assertEquals(0.250, offer!!.approachDistanceKm!!, 0.0001)
        assertEquals(7.4, offer.rideDistanceKm!!, 0.001)
    }

    @Test
    fun `m² and 'min' are NOT matched as meters`() {
        val texts = listOf(
            "Accepter", "10,00 €",
            "Appart 45 m² à proximité",
            "3 min · 5 km"  // 3 min must NOT be read as 3 m
        )
        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)
        assertNotNull(offer)
        // Only "5 km" counts -> total distance should be 5 km (no meters contribution).
        assertEquals(5.0, offer!!.totalDistanceKm!!, 0.001)
        assertEquals(3.0, offer.totalTimeMin!!, 0.001)
    }

    @Test
    fun `month abbreviation 'mai' is NOT a meter unit`() {
        val texts = listOf(
            "Accepter", "12,00 €",
            "Capturé le 15 mai 2024",
            "4 km · 10 min"
        )
        val offer = parser.parseTexts(texts, "com.ubercab.driver", config)
        assertNotNull(offer)
        assertEquals(4.0, offer!!.totalDistanceKm!!, 0.001)
    }

    @Test
    fun `Uber capture with pickup address containing a number and dotted km`() {
        // Exact text layout the user shared from a real Uber offer screenshot.
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
    fun `Bolt - time estimated when ride duration missing`() {
        val texts = listOf(
            "Refuser", "<1 min", "135 m",
            "Zone Accueil - Entrée, Parc Astérix",
            "Gare de Gagny, Gagny • 35.5 km",
            "35.51€ · Net, TTC", "Accepter"
        )
        val parsed = parser.parseTexts(texts, "ee.mtakso.driver", config)
        assertNotNull(parsed)
        assertNull(parsed!!.rideTimeMin)
        assertEquals(0.135, parsed.approachDistanceKm!!, 0.0001)

        // withEstimatedTimes should fill missing times and mark the offer as estimated.
        val est = parsed.withEstimatedTimes(35.0)
        assertEquals(true, est.timeEstimated)
        assertEquals(60.857, est.rideTimeMin!!, 0.01)
        // Approach time already present (1 min) — must NOT be overwritten.
        assertEquals(1.0, est.approachTimeMin!!, 0.001)
    }

    @Test
    fun `withEstimatedTimes - avgSpeedKmh=0 returns the offer unchanged`() {
        val texts = listOf(
            "Accepter", "20,00 €",
            "135 m", "Zone X",
            "Gare Y • 10 km"
        )
        val parsed = parser.parseTexts(texts, "ee.mtakso.driver", config)!!
        val est = parsed.withEstimatedTimes(0.0)
        assertEquals(parsed, est)
        assertEquals(false, est.timeEstimated)
    }

    @Test
    fun `meters with french decimal comma`() {
        val texts = listOf(
            "Refuser", "<1 min", "450,5 m",
            "Pickup", "Destination • 12 km",
            "25,00€ · Net, TTC", "Accepter"
        )
        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)
        assertNotNull(offer)
        assertEquals(0.4505, offer!!.approachDistanceKm!!, 0.0001)
        assertEquals(12.0, offer.rideDistanceKm!!, 0.001)
    }

    @Test
    fun `meters with thousand not confused when 2 ride distances in km`() {
        // Only one distance in meters, two in km (ambiguous for allMeters alone,
        // but allMeters converts correctly to km and fallback picks min=approach / max=ride).
        val texts = listOf(
            "Refuser", "<1 min", "900 m",
            "Pickup",
            "Destination • 15 km",
            "40,00€ · Net, TTC", "Accepter"
        )
        val offer = parser.parseTexts(texts, "ee.mtakso.driver", config)
        assertNotNull(offer)
        assertEquals(0.9, offer!!.approachDistanceKm!!, 0.001)
        assertEquals(15.0, offer.rideDistanceKm!!, 0.001)
    }
}
