package com.vtccopilot.app.domain.model

import java.text.BreakIterator

/**
 * Texte personnalisé du verdict (à la place de SUPER / MOYEN / MAUVAIS) :
 * [MAX_CHARS] caractères visibles maximum (un emoji compte pour 1) dont [MAX_EMOJIS] emojis.
 */
object VerdictLabel {
    const val MAX_CHARS = 12
    const val MAX_EMOJIS = 2

    /** Découpe en caractères "visibles" (graphèmes) : 🔥, 👍🏽 ou ❤️ = 1 caractère. */
    fun graphemes(s: String): List<String> {
        val it = BreakIterator.getCharacterInstance().apply { setText(s) }
        val out = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            out += s.substring(start, end)
            start = end
            end = it.next()
        }
        return out
    }

    fun isEmoji(g: String): Boolean {
        val cp = g.codePointAt(0)
        return cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2300..0x23FF
    }

    fun length(s: String): Int = graphemes(s).size

    fun emojiCount(s: String): Int = graphemes(s).count(::isEmoji)

    /** Une seule ligne, 12 caractères max, 2 emojis max (les suivants sont ignorés). */
    fun sanitize(input: String): String {
        val sb = StringBuilder()
        var chars = 0
        var emojis = 0
        for (g in graphemes(input.replace('\n', ' ').replace('\r', ' '))) {
            if (chars >= MAX_CHARS) break
            if (isEmoji(g)) {
                if (emojis >= MAX_EMOJIS) continue
                emojis++
            }
            sb.append(g)
            chars++
        }
        return sb.toString()
    }
}
