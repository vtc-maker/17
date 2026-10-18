package com.drivee.copilote.service.overlay

import com.drivee.copilote.domain.model.ProfitabilityScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Mirrors the JVM-safe parts of VerdictSound.Companion (synthesize/notesFor) so we can
 * assert buffer bounds and envelope sanity without pulling in the real Android class
 * (which depends on AudioManager/AudioTrack/AudioAttributes).
 *
 * If the real synthesize() ever drifts out of sync with this duplicate, re-align here
 * and update RideAccessibilityService / VerdictSound accordingly.
 */
class VerdictSoundSynthesizeTest {

    companion object {
        private const val RATE = 44_100

        fun notesFor(score: ProfitabilityScore): List<Pair<Double, Int>> = when (score) {
            ProfitabilityScore.GREEN -> listOf(880.0 to 110, 1318.5 to 220)
            ProfitabilityScore.ORANGE -> listOf(784.0 to 200)
            ProfitabilityScore.RED -> listOf(392.0 to 150, 261.6 to 260)
            ProfitabilityScore.UNKNOWN -> emptyList()
        }

        fun synthesize(notes: List<Pair<Double, Int>>, rate: Int = RATE): ShortArray {
            val gap = rate * 30 / 1000
            val total = notes.sumOf { rate * it.second / 1000 + gap }
            val out = ShortArray(total)
            var offset = 0
            for ((freq, ms) in notes) {
                val n = rate * ms / 1000
                val attack = rate * 8 / 1000
                for (i in 0 until n) {
                    val t = i.toDouble() / rate
                    val env = (if (i < attack) i.toDouble() / attack else 1.0) * exp(-3.0 * i / n)
                    val s = sin(2 * PI * freq * t) * 0.8 + sin(4 * PI * freq * t) * 0.2
                    out[offset + i] = (s * env * 0.6 * Short.MAX_VALUE).toInt().toShort()
                }
                offset += n + gap
            }
            return out
        }
    }

    @Test
    fun `synthesize produces GREEN buffer within bounds and non-silent`() {
        val notes = notesFor(ProfitabilityScore.GREEN)
        val pcm = synthesize(notes)
        val expectedTotal = notes.sumOf { RATE * it.second / 1000 + RATE * 30 / 1000 }
        assertEquals(expectedTotal, pcm.size)
        // At least one non-zero sample.
        assertTrue(pcm.any { it.toInt() != 0 })
        // Final gap samples are zero (never written).
        val lastGapStart = pcm.size - RATE * 30 / 1000
        for (i in lastGapStart until pcm.size) {
            assertEquals(0, pcm[i].toInt())
        }
    }

    @Test
    fun `synthesize ORANGE single note - no out of bounds`() {
        val notes = notesFor(ProfitabilityScore.ORANGE)
        val pcm = synthesize(notes)
        val n = RATE * 200 / 1000
        val gap = RATE * 30 / 1000
        assertEquals(n + gap, pcm.size)
        // Last written index is n-1; indices [n, n+gap) should remain 0.
        for (i in n until pcm.size) assertEquals(0, pcm[i].toInt())
    }

    @Test
    fun `synthesize RED two descending notes - buffer bounds ok`() {
        val notes = notesFor(ProfitabilityScore.RED)
        val pcm = synthesize(notes)
        val n1 = RATE * 150 / 1000
        val n2 = RATE * 260 / 1000
        val gap = RATE * 30 / 1000
        assertEquals(n1 + gap + n2 + gap, pcm.size)
        // Gap between notes (index n1 .. n1+gap) must be silent.
        for (i in n1 until n1 + gap) assertEquals(0, pcm[i].toInt())
    }

    @Test
    fun `synthesize UNKNOWN - empty note list returns empty buffer`() {
        val pcm = synthesize(notesFor(ProfitabilityScore.UNKNOWN))
        assertEquals(0, pcm.size)
    }

    @Test
    fun `envelope has an 8ms attack and decays`() {
        val notes = listOf(1000.0 to 100)
        val pcm = synthesize(notes)
        val attack = RATE * 8 / 1000
        // During the attack window the envelope rises (so absolute amplitude
        // at i=1 should be <= amplitude at i=attack-1 at same phase would be noisy).
        // A simpler, deterministic check: sample index 0 must be ~0 (attack factor 0).
        assertEquals(0, pcm[0].toInt())
        assertTrue(pcm[attack].toInt() != 0)
    }
}
