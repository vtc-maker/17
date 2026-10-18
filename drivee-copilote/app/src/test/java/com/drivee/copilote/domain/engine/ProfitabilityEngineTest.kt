package com.drivee.copilote.domain.engine

import com.drivee.copilote.domain.model.ProfitabilityScore
import com.drivee.copilote.domain.model.RideOffer
import com.drivee.copilote.domain.model.VehicleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires JVM du moteur de rentabilité (logique pure, aucune dép. Android).
 */
class ProfitabilityEngineTest {

    private val engine = ProfitabilityEngine()

    @Test
    fun `course rentable classee SUPER`() {
        val settings = VehicleSettings(
            costPerKmEuros = 0.20,
            platformCommissionPercent = 0.0,
            socialChargesPercent = 22.0,
            priceIsNetOfCommission = true
        )
        // 25 € pour 20 km total et 30 min total.
        val offer = RideOffer(
            sourcePackage = "com.ubercab.driver",
            grossPriceEuros = 25.0,
            approachDistanceKm = 5.0,
            approachTimeMin = 10.0,
            rideDistanceKm = 15.0,
            rideTimeMin = 20.0
        )

        val m = engine.compute(offer, settings)

        // revenu net = 25 * (1-0.22) - 20*0.20 = 19.5 - 4 = 15.5 €
        // €/h net = 15.5 / (30/60) = 31.0 €/h
        assertEquals(31.0, m.netHourlyRateEuros!!, 0.01)
        assertEquals(50.0, m.grossHourlyRateEuros!!, 0.01)   // 25 / 0.5h
        assertEquals(1.25, m.grossPricePerKmEuros!!, 0.01)   // 25 / 20km
        assertEquals(ProfitabilityScore.GREEN, m.score)
    }

    @Test
    fun `prix brut avec commission classe MAUVAIS`() {
        val settings = VehicleSettings(
            costPerKmEuros = 0.30,
            platformCommissionPercent = 25.0,
            socialChargesPercent = 22.0,
            priceIsNetOfCommission = false   // le prix affiché est BRUT
        )
        val offer = RideOffer(
            sourcePackage = "ee.mtakso.driver",
            grossPriceEuros = 8.0,
            approachDistanceKm = 4.0,
            approachTimeMin = 12.0,
            rideDistanceKm = 6.0,
            rideTimeMin = 18.0
        )

        val m = engine.compute(offer, settings)

        // encaissé = 8 * 0.75 = 6.0 ; charges = 6*0.22 = 1.32 ; véhicule = 10*0.30 = 3.0
        // net = 6 - 1.32 - 3 = 1.68 € ; €/h = 1.68 / (30/60) = 3.36
        assertEquals(3.36, m.netHourlyRateEuros!!, 0.01)
        assertEquals(ProfitabilityScore.RED, m.score)
    }

    @Test
    fun `sans prix aucune metrique et score UNKNOWN`() {
        val m = engine.compute(
            RideOffer(sourcePackage = "com.ubercab.driver", grossPriceEuros = null),
            VehicleSettings()
        )
        assertNull(m.netHourlyRateEuros)
        assertEquals(ProfitabilityScore.UNKNOWN, m.score)
    }

    @Test
    fun `distance nulle ne provoque pas de division par zero`() {
        val m = engine.compute(
            RideOffer(
                sourcePackage = "com.ubercab.driver",
                grossPriceEuros = 10.0,
                rideTimeMin = 15.0
                // aucune distance -> €/km doit être null, pas d'exception
            ),
            VehicleSettings()
        )
        assertNull(m.grossPricePerKmEuros)
        assertTrue(m.netHourlyRateEuros != null)
    }

    // --- Exemples des captures fournies (seuils par défaut : 1,00 €/km et 30 €/h) ---

    private fun popup(price: Double, km: Double, min: Double) = engine.compute(
        RideOffer(sourcePackage = "x", grossPriceEuros = price, rideDistanceKm = km, rideTimeMin = min),
        VehicleSettings()
    )

    @Test
    fun `Uber 16 euros 9 km 13 min = 1,78 euro par km, 74 euros par h, SUPER`() {
        val m = popup(16.0, 9.0, 13.0)
        assertEquals(1.78, m.grossPricePerKmEuros!!, 0.01)
        assertEquals(73.85, m.grossHourlyRateEuros!!, 0.01)
        assertEquals(ProfitabilityScore.GREEN, m.score)
        assertEquals("SUPER", m.score.label)
    }

    @Test
    fun `Heetch 14 euros 15 km 26 min = 0,93 euro par km, 32 euros par h, MOYEN`() {
        val m = popup(14.0, 15.0, 26.0)
        assertEquals(0.93, m.grossPricePerKmEuros!!, 0.01)
        assertEquals(32.31, m.grossHourlyRateEuros!!, 0.01)
        assertEquals(ProfitabilityScore.ORANGE, m.score) // €/km sous le seuil, €/h au-dessus -> le pire
        assertEquals(0.93, m.thresholdRatio!!, 0.01)
    }

    @Test
    fun `Bolt 10 euros 14 km 24 min = 0,71 euro par km, 25 euros par h, MAUVAIS`() {
        val m = popup(10.0, 14.0, 24.0)
        assertEquals(0.71, m.grossPricePerKmEuros!!, 0.01)
        assertEquals(25.0, m.grossHourlyRateEuros!!, 0.01)
        assertEquals(ProfitabilityScore.RED, m.score)
        assertEquals("MAUVAIS", m.score.label)
    }

    @Test
    fun `bon au km mais mauvais a l'heure donne MAUVAIS`() {
        // 20 € / 10 km = 2 €/km (super) mais 20 € / 60 min = 20 €/h (< 80 % de 30)
        assertEquals(ProfitabilityScore.RED, popup(20.0, 10.0, 60.0).score)
    }
}
