package com.drivee.copilote.service.accessibility

import com.drivee.copilote.service.accessibility.AcceptanceTracker.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcceptanceTrackerTest {

    private var now = 0L
    private val tracker = AcceptanceTracker<String>(clock = { now })

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
        assertNull(tracker.offerVisible("a", "A"))
        tracker.offerGone()
        now = 1_000
        val r = tracker.onScreen(pickup)!!
        assertEquals("A", r.item)
        // déjà décidée : jamais comptée deux fois
        assertNull(tracker.offerVisible("a", "A"))
    }

    @Test
    fun `nouvelle offre pendant l'attente = la precedente est a valider`() {
        tracker.offerVisible("a", "A")
        tracker.offerGone()
        val r = tracker.offerVisible("b", "B")!!
        assertEquals("A", r.item)
        assertEquals(Decision.PENDING, r.decision)
    }

    @Test
    fun `lecture remplacee alors que l'offre est visible = ignoree`() {
        tracker.offerVisible("a99", "mauvaise lecture")
        assertNull(tracker.offerVisible("a12", "bonne lecture"))
        assertEquals("bonne lecture", tracker.onClick("Accepter")!!.item)
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
}
