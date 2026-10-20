package com.vtccopilot.app.service.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.vtccopilot.app.domain.model.ProfitabilityScore
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Son du verdict, synthétisé à la volée (aucun fichier audio) :
 *  - SUPER   : deux notes montantes, claires
 *  - MOYEN   : une note neutre
 *  - MAUVAIS : deux notes descendantes, graves
 *
 * Joué comme un guidage GPS (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) : la musique
 * est baissée brièvement puis revient. Volume = volume "multimédia/navigation" du téléphone.
 */
class VerdictSound(context: Context) {

    companion object {
        private const val TAG = "VerdictSound"
        private const val RATE = 44_100

        /** (fréquence Hz, durée ms) pour chaque verdict. */
        fun notesFor(score: ProfitabilityScore): List<Pair<Double, Int>> = when (score) {
            ProfitabilityScore.GREEN -> listOf(880.0 to 110, 1318.5 to 220)
            ProfitabilityScore.ORANGE -> listOf(784.0 to 200)
            ProfitabilityScore.RED -> listOf(392.0 to 150, 261.6 to 260)
            ProfitabilityScore.UNKNOWN -> emptyList()
        }

        /** PCM 16 bits mono : sinus + harmonique douce, attaque 8 ms, décroissance exponentielle. */
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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val executor = Executors.newSingleThreadExecutor()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(score: ProfitabilityScore) {
        val notes = notesFor(score)
        if (notes.isEmpty()) return
        executor.execute {
            runCatching { playPcm(synthesize(notes), notes.sumOf { it.second + 30 }) }
                .onFailure { Log.w(TAG, "Lecture du son impossible", it) }
        }
    }

    private fun playPcm(pcm: ShortArray, durationMs: Int) {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audioManager.requestAudioFocus(focus)

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep(durationMs + 80L)
        } finally {
            track.release()
            audioManager.abandonAudioFocusRequest(focus)
        }
    }

    fun release() {
        executor.shutdownNow()
    }
}
