package com.vtccopilot.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictLabelTest {

    @Test
    fun `un emoji compte pour un caractere`() {
        assertEquals(7, VerdictLabel.length("🔥 TOP 🔥"))
        assertEquals(2, VerdictLabel.emojiCount("🔥 TOP 🔥"))
    }

    @Test
    fun `texte coupe a 12 caracteres`() {
        assertEquals("ABCDEFGHIJKL", VerdictLabel.sanitize("ABCDEFGHIJKLMNOP"))
        assertEquals("🚀 JACKPOT 🚀 ", VerdictLabel.sanitize("🚀 JACKPOT 🚀 !!!"))
    }

    @Test
    fun `troisieme emoji ignore`() {
        assertEquals("🔥🔥 OK", VerdictLabel.sanitize("🔥🔥🔥 OK"))
    }

    @Test
    fun `une seule ligne`() {
        assertEquals("A B", VerdictLabel.sanitize("A\nB"))
    }

    @Test
    fun `texte normal inchange`() {
        assertEquals("Bof", VerdictLabel.sanitize("Bof"))
        assertEquals("À éviter", VerdictLabel.sanitize("À éviter"))
    }
}
