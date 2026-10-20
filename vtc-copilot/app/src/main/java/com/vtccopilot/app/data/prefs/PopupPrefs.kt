package com.vtccopilot.app.data.prefs

import android.content.Context
import com.vtccopilot.app.domain.model.ProfitabilityScore
import com.vtccopilot.app.domain.model.VerdictLabel

/** Mise en page de la pop-up d'offre. */
enum class PopupLayout(val label: String) {
    COMPLET("Complet"),
    COMPACT("Compact"),
    XXL("Verdict XXL")
}

/** Style visuel de la pop-up (couleurs fixes : la pop-up est toujours sur fond sombre / papier). */
enum class PopupTheme(
    val label: String,
    val bg: Int,
    val stroke: Int,
    val strokeDp: Float,
    /** Bordure de la couleur du verdict (Néon). */
    val strokeIsVerdict: Boolean,
    val radiusDp: Float,
    val text: Int,
    val textSoft: Int,
    /** Intensité du halo lumineux (0 = aucun). */
    val glow: Float,
    val track: Int,
    val ring: Int,
    val tick: Int,
    val small: Int,
    /** Assombrit la couleur du verdict (fond clair du style Rétro). */
    val darkenVerdict: Boolean
) {
    ACTUEL("Actuel", 0xF2111513.toInt(), 0x26FFFFFF, 1f, false, 26f, 0xFFF2F4F5.toInt(), 0xFFE3E6E8.toInt(),
        1f, 0xFF252A28.toInt(), 0xFF0C0F0E.toInt(), 0xFFE6E9EC.toInt(), 0xFFD5D9DD.toInt(), false),
    CLASSE("Classe", 0xF5101012.toInt(), 0x99C9A961.toInt(), 1f, false, 14f, 0xFFF5EFE0.toInt(), 0xFFCFC6B0.toInt(),
        0f, 0xFF2A2721.toInt(), 0xFF101012.toInt(), 0xFFC9A961.toInt(), 0xFFC9A961.toInt(), false),
    NEON("Néon", 0xF2050611.toInt(), 0xFFFFFFFF.toInt(), 2f, true, 22f, 0xFFFFFFFF.toInt(), 0xFFB8C4FF.toInt(),
        2f, 0xFF151833.toInt(), 0xFF050611.toInt(), 0xFFFFFFFF.toInt(), 0xFF9FB0FF.toInt(), false),
    RETRO("Rétro", 0xFAF3E5C4.toInt(), 0xFF2B2118.toInt(), 3f, false, 4f, 0xFF2B2118.toInt(), 0xFF5A4A38.toInt(),
        0f, 0xFFD9C79E.toInt(), 0xFF2B2118.toInt(), 0xFF2B2118.toInt(), 0xFF5A4A38.toInt(), true);

    /** Couleur du verdict adaptée au style. */
    fun verdictColor(color: Int): Int {
        if (!darkenVerdict) return color
        val f = 0.62f
        return (0xFF shl 24) or
            (((color shr 16 and 0xFF) * f).toInt() shl 16) or
            (((color shr 8 and 0xFF) * f).toInt() shl 8) or
            ((color and 0xFF) * f).toInt()
    }
}

/**
 * Réglages de la pop-up : textes des verdicts, mise en page et style.
 * SharedPreferences : lus par le service (pop-up) comme par l'interface (historique).
 */
object PopupPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("vtccopilot_popup", Context.MODE_PRIVATE)

    fun defaultLabel(score: ProfitabilityScore): String = score.label

    /** Texte affiché pour un verdict (texte personnalisé, sinon SUPER / MOYEN / MAUVAIS). */
    fun label(c: Context, score: ProfitabilityScore): String {
        if (score == ProfitabilityScore.UNKNOWN) return score.label
        return prefs(c).getString("label_${score.name}", null)?.takeIf { it.isNotBlank() } ?: score.label
    }

    /** Variante pour l'historique (score stocké sous forme de texte : GREEN / ORANGE / RED). */
    fun label(c: Context, scoreName: String): String =
        label(c, ProfitabilityScore.entries.firstOrNull { it.name == scoreName } ?: ProfitabilityScore.UNKNOWN)

    /** Texte saisi (vide = texte par défaut). */
    fun customLabel(c: Context, score: ProfitabilityScore): String =
        prefs(c).getString("label_${score.name}", "") ?: ""

    fun setLabel(c: Context, score: ProfitabilityScore, text: String) =
        prefs(c).edit().putString("label_${score.name}", VerdictLabel.sanitize(text).trim()).apply()

    fun layout(c: Context): PopupLayout =
        PopupLayout.entries.firstOrNull { it.name == prefs(c).getString("layout", null) } ?: PopupLayout.COMPLET

    fun setLayout(c: Context, l: PopupLayout) = prefs(c).edit().putString("layout", l.name).apply()

    fun theme(c: Context): PopupTheme =
        PopupTheme.entries.firstOrNull { it.name == prefs(c).getString("theme", null) } ?: PopupTheme.ACTUEL

    fun setTheme(c: Context, t: PopupTheme) = prefs(c).edit().putString("theme", t.name).apply()

    fun resetLabels(c: Context) = prefs(c).edit().apply {
        ProfitabilityScore.entries.forEach { remove("label_${it.name}") }
    }.apply()
}
