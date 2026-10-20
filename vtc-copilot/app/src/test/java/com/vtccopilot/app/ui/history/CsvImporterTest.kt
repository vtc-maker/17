package com.vtccopilot.app.ui.history

import com.vtccopilot.app.data.local.entity.RideStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvImporterTest {

    private val header = "\uFEFFDate;Plateforme;Prix brut (€);Distance approche (km);Temps approche (min);" +
        "Distance course (km);Temps course (min);€/km net;€/h net;Bénéfice net (€);Score;€/km brut;€/h brut;Ratio seuil;Statut"

    @Test
    fun `relit un export complet VTC Copilot`() {
        val csv = "$header\r\n" +
            "19/10/2026 20:57;Uber;36,48;2,10;5,00;35,80;40,00;0,70;30,00;25,00;Mauvais;0,96;48,64;0,96;Pas prise\r\n" +
            "19/10/2026 20:52;Bolt;13,68;1,00;3,00;9,00;15,00;;;;Super;1,37;45,60;1,37;Prise\r\n"

        val r = CsvImporter.parse(csv)

        assertEquals(0, r.skipped)
        assertEquals(2, r.rides.size)
        val uber = r.rides[0]
        assertEquals("com.ubercab.driver", uber.sourcePackage)
        assertEquals(36.48, uber.grossPriceEuros!!, 0.001)
        assertEquals(37.9, uber.approachDistanceKm!! + uber.rideDistanceKm!!, 0.001)
        assertEquals("RED", uber.score)
        assertEquals(RideStatus.REJECTED, uber.status)
        assertEquals(0.96, uber.thresholdRatio!!, 0.001)
        assertEquals(RideStatus.ACCEPTED, r.rides[1].status)
        assertEquals("ee.mtakso.driver", r.rides[1].sourcePackage)
    }

    @Test
    fun `ancien export sans statut - courses considerees prises, lignes illisibles comptees`() {
        val csv = "Date;Plateforme;Prix brut (€);Distance approche (km);Temps approche (min);Distance course (km);Temps course (min);€/km net;€/h net;Bénéfice net (€);Score\n" +
            "18/10/2026 10:40;Heetch;12,62;1,0;4;8,0;16;;;;Moyen\n" +
            "pas une date;Uber;10,00;;;;;;;;\n"

        val r = CsvImporter.parse(csv)

        assertEquals(1, r.rides.size)
        assertEquals(1, r.skipped)
        assertEquals(RideStatus.ACCEPTED, r.rides[0].status)
        assertEquals(12.62 / 9.0, r.rides[0].grossPricePerKmEuros!!, 0.001) // recalculé
    }
}
