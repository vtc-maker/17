package com.drivee.copilote.data.prefs

import android.content.Context
import com.drivee.copilote.domain.model.ProfitabilityScore

/**
 * Couleurs personnalisables (pop-up SUPER / MOYEN / MAUVAIS + anneau de la bulle).
 * Stockées en SharedPreferences : lues aussi bien par le service que par l'interface.
 */
object AppColors {

    enum class Slot(val key: String, val label: String, val default: Int) {
        SUPER("c_super", "SUPER", 0xFF4ADE80.toInt()),
        MOYEN("c_moyen", "MOYEN", 0xFFF5A524.toInt()),
        MAUVAIS("c_mauvais", "MAUVAIS", 0xFFF26B6B.toInt()),
        BUBBLE("c_bubble", "Anneau de la bulle", 0xFF4ADE80.toInt())
    }

    const val UNKNOWN = 0xFF9AA4B2.toInt()

    /** Palette proposée dans les réglages. */
    val palette: List<Int> = listOf(
        0xFF4ADE80, 0xFF10B981, 0xFF22D3EE, 0xFF60A5FA, 0xFFA78BFA,
        0xFFF472B6, 0xFFFACC15, 0xFFF5A524, 0xFFFB923C, 0xFFF26B6B,
        0xFFEF4444, 0xFFE5E7EB
    ).map { it.toInt() }

    private fun prefs(c: Context) = c.getSharedPreferences("drivee_colors", Context.MODE_PRIVATE)

    fun get(c: Context, slot: Slot): Int = prefs(c).getInt(slot.key, slot.default)

    fun set(c: Context, slot: Slot, color: Int) = prefs(c).edit().putInt(slot.key, color).apply()

    fun reset(c: Context) = prefs(c).edit().clear().apply()

    fun forScore(c: Context, score: ProfitabilityScore): Int = when (score) {
        ProfitabilityScore.GREEN -> get(c, Slot.SUPER)
        ProfitabilityScore.ORANGE -> get(c, Slot.MOYEN)
        ProfitabilityScore.RED -> get(c, Slot.MAUVAIS)
        ProfitabilityScore.UNKNOWN -> UNKNOWN
    }
}
