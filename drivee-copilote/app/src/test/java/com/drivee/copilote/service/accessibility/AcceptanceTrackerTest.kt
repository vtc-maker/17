package com.drivee.copilote.service.accessibility

import com.drivee.copilote.service.accessibility.AcceptanceTracker.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptanceTrackerTest {

    private var now = 0L
    private val tracker = AcceptanceTracker<String>(clock = { now })

    /** Lecture simulée "prix@km" : même offre si même km (comme dans le service). */
    private val ocrTracker = AcceptanceTracker<Pair<Double, Double>>(
        clock = { now },
        sameOffer = { a, b -> kotlin.math.abs(a.second - b.second) <= maxOf(0.6, 0.06 * maxOf(a.second, b.second)) }
    )

    private val home = listOf("Accueil", "Vous êtes en ligne.", "PASSER HORS LIGNE", "0,00€")
    private val pickup = listOf("Récupérer Amira", "Rue de la Montagne 55", "Appeler")

    @Test
    fun `ecran de prise en charge apres l'offre = ACCEPTEE`() {
        tracker.offerVisible("a", "A")
        now = 1_000; tracker.offerGone()
        now = 2_000
        assertEquals(Decision.ACCEPTED, tracker.onScreen(pickup)!!.decision)
    }

    @Test
    fun `retour a l'accueil apres l'offre = REFUSEE`() {
        tracker.offerVisible("a", "A")
        now = 1_000; tracker.offerGone()
        now = 1_500
        assertNull(tracker.onScreen(home)) // délai de grâce
        now = 4_000
        assertEquals(Decision.REJECTED, tracker.onScreen(home)!!.decision)
    }

    @Test
    fun `sans preuve apres 30 s = A VALIDER`() {
        tracker.offerVisible("a", "A")
        tracker.offerGone()
        now = 31_000
        assertEquals(Decision.PENDING, tracker.onScreen(listOf("carte"))!!.decision)
    }

    @Test
    fun `meme offre relue apres une lecture ratee = pas de doublon`() {
        tracker.offerVisible("a", "A")
        tracker.offerGone()
        assertTrue(tracker.offerVisible("a", "A").isEmpty())
        tracker.offerGone()
        now = 1_000
        val r = tracker.onScreen(pickup)!!
        assertEquals("A", r.item)
        // déjà décidée : jamais comptée deux fois
        assertTrue(tracker.offerVisible("a", "A").isEmpty())
        assertNull(tracker.onClick("Accepter"))
    }

    @Test
    fun `nouvelle offre pendant l'attente = la precedente est a valider`() {
        tracker.offerVisible("a", "A")
        tracker.offerGone()
        val r = tracker.offerVisible("b", "B").single()
        assertEquals("A", r.item)
        assertEquals(Decision.PENDING, r.decision)
    }

    @Test
    fun `clic Accepter ou Refuser = decision immediate`() {
        tracker.offerVisible("a", "A")
        assertEquals(Decision.ACCEPTED, tracker.onClick("Accepter")!!.decision)
        tracker.offerVisible("b", "B")
        assertEquals(Decision.REJECTED, tracker.onClick("Refuser")!!.decision)
        assertNull(tracker.onClick("Accepter")) // plus rien en attente
    }

    @Test
    fun `les textes de la carte d'offre ne declenchent pas l'acceptation`() {
        tracker.offerVisible("a", "A")
        tracker.offerGone()
        now = 500
        assertNull(tracker.onScreen(listOf("Comfort", "39,71 €", "11 min (4.5 km)", "Course de 28.1 km", "Accepter", "Refuser", "1 arrêt", "Répondre")))
    }

    // --- Bug signalé : 1 seule offre (10,03 €, 10,5 km) enregistrée 4 fois avec des prix différents ---

    @Test
    fun `une offre lue avec 4 prix differents = UNE seule course, prix majoritaire`() {
        val readings = listOf(10.03 to 10.5, 10.55 to 10.6, 10.03 to 10.5, 96.0 to 10.5, 10.03 to 10.5, 6.0 to 10.5)
        val results = mutableListOf<AcceptanceTracker.Result<Pair<Double, Double>>>()
        readings.forEachIndexed { i, r ->
            results += ocrTracker.offerVisible("${r.first}", r)
            if (i % 2 == 1) ocrTracker.offerGone() // lectures ratées intermittentes
        }
        ocrTracker.offerGone()
        now = 40_000
        results += listOfNotNull(ocrTracker.onScreen(listOf("carte")))

        assertEquals(1, results.size)
        assertEquals(10.03, results.single().item.first, 0.001)
        // relue juste après : déjà décidée, pas de nouvelle ligne
        assertTrue(ocrTracker.isKnown(10.55 to 10.5))
        assertTrue(ocrTracker.offerVisible("10.55", 10.55 to 10.5).isEmpty())
    }

    @Test
    fun `une vraie nouvelle offre (autre distance) reste distincte`() {
        ocrTracker.offerVisible("10.03", 10.03 to 10.5)
        assertFalse(ocrTracker.isKnown(14.0 to 15.0))
        val r = ocrTracker.offerVisible("14.0", 14.0 to 15.0).single()
        assertEquals(10.03, r.item.first, 0.001)
        assertEquals(Decision.PENDING, r.decision)
    }
}
