package com.drivee.copilote.service.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.drivee.copilote.data.config.PackageRules
import com.drivee.copilote.data.config.ParserConfig
import com.drivee.copilote.domain.model.RideOffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ⭐ PARSER — Extraction ROBUSTE d'une [RideOffer] depuis l'arborescence
 * d'[AccessibilityNodeInfo] de l'application VTC.
 *
 * Stratégie :
 *   1. Parcours récursif (BFS borné) de l'arbre -> collecte de tous les textes
 *      (getText + contentDescription) et, si dispo, association resource-id -> texte.
 *   2. Détection d'ancre d'offre (offerAnchorPatterns) pour éviter les faux positifs.
 *   3. Extraction champ par champ via config dynamique (resource-id prioritaire,
 *      puis Regex de secours).
 *   4. Heuristiques de repli quand les libellés (approche/course) sont absents :
 *      la plus PETITE distance/durée = approche, la plus GRANDE = course.
 *
 * Toutes les étapes sont protégées (try/catch) : le parsing NE DOIT JAMAIS
 * faire planter le service d'accessibilité.
 */
@Singleton
class RideParser @Inject constructor() {

    companion object {
        private const val TAG = "RideParser"
        private const val MAX_NODES = 1500         // garde-fou perf
        private const val DEBUG_DUMP = false       // passer à true pour logger les textes
        private val NUMBER_REGEX = Regex("(\\d+(?:[.,]\\d{1,2})?)")
    }

    /** Résultat interne du parcours d'arbre. */
    internal data class Harvest(
        val texts: MutableList<String> = mutableListOf(),
        val byViewId: MutableMap<String, String> = mutableMapOf()
    )

    fun parse(root: AccessibilityNodeInfo?, pkg: String, config: ParserConfig): RideOffer? =
        if (root == null) null else parseRoots(listOf(root), pkg, config)

    /**
     * Analyse plusieurs fenêtres (la plus haute d'abord) : la carte d'offre est souvent
     * une fenêtre séparée au-dessus de la carte. On tente chaque fenêtre seule
     * (évite de mélanger avec "Gains du jour" de l'écran d'accueil), puis la fusion.
     */
    fun parseRoots(roots: List<AccessibilityNodeInfo>, pkg: String, config: ParserConfig): RideOffer? {
        val harvests = roots.mapNotNull { root ->
            runCatching { harvestTree(root) }
                .onFailure { Log.w(TAG, "Échec du parcours de l'arbre", it) }
                .getOrNull()
        }
        if (harvests.isEmpty()) return null
        for (h in harvests) parseHarvest(h, pkg, config)?.let { return it }
        if (harvests.size == 1) return null
        val merged = Harvest()
        harvests.forEach { merged.texts += it.texts; merged.byViewId.putAll(it.byViewId) }
        return parseHarvest(merged, pkg, config)
    }

    /**
     * Extraction PURE (sans Android) à partir des textes collectés — testable en JVM.
     * [texts] : tous les textes visibles ; [byViewId] : resource-id -> texte.
     */
    fun parseTexts(
        texts: List<String>,
        pkg: String,
        config: ParserConfig,
        byViewId: Map<String, String> = emptyMap()
    ): RideOffer? = parseHarvest(
        Harvest(texts.toMutableList(), byViewId.toMutableMap()), pkg, config
    )

    private fun parseHarvest(harvest: Harvest, pkg: String, config: ParserConfig): RideOffer? {
        val rules = config.rulesFor(pkg) ?: return null
        if (harvest.texts.isEmpty()) return null
        val joined = harvest.texts.joinToString("\n")

        if (DEBUG_DUMP) Log.d(TAG, "Textes captés ($pkg):\n$joined")

        // 2) L'écran contient-il réellement une offre ?
        if (!looksLikeOffer(joined, rules)) return null

        // 3) Extraction.
        val price = extractPrice(harvest, rules)
            ?: return null // sans prix, pas d'offre exploitable

        var approachKm = extractNumber(joined, rules.approachDistancePatterns)
        var approachMin = extractNumber(joined, rules.approachTimePatterns)
        var rideKm = extractNumber(joined, rules.rideDistancePatterns)
        var rideMin = extractNumber(joined, rules.rideTimePatterns)

        // 4) Repli heuristique sur toutes les valeurs "X km" / "X min" de l'écran :
        //    la plus petite = approche, la plus grande = course ; complète les champs manquants.
        val kmFallback = allNumbersFollowedBy(joined, "km")
        val minFallback = allNumbersFollowedBy(joined, "min")
        fillFromFallback(approachKm, rideKm, kmFallback).let { approachKm = it.first; rideKm = it.second }
        fillFromFallback(approachMin, rideMin, minFallback).let { approachMin = it.first; rideMin = it.second }

        val pickup = extractString(joined, rules.pickupAddressPatterns)
        val destination = extractString(joined, rules.destinationAddressPatterns)

        return RideOffer(
            sourcePackage = pkg,
            grossPriceEuros = price,
            approachDistanceKm = approachKm,
            approachTimeMin = approachMin,
            rideDistanceKm = rideKm,
            rideTimeMin = rideMin,
            pickupAddress = pickup,
            destinationAddress = destination,
            rawTexts = harvest.texts.toList()
        ).takeIf { it.isValid }
    }

    // ---------------------------------------------------------------------
    //  Parcours de l'arbre (itératif pour éviter les StackOverflow).
    // ---------------------------------------------------------------------
    private fun harvestTree(root: AccessibilityNodeInfo): Harvest {
        val harvest = Harvest()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()
            val viewId = runCatching { node.viewIdResourceName }.getOrNull()

            if (!text.isNullOrEmpty()) {
                harvest.texts.add(text)
                if (viewId != null) harvest.byViewId[viewId] = text
            }
            if (!desc.isNullOrEmpty() && desc != text) {
                harvest.texts.add(desc)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return harvest
    }

    // ---------------------------------------------------------------------
    //  Extraction du prix : resource-id prioritaire, puis Regex.
    // ---------------------------------------------------------------------
    private fun extractPrice(harvest: Harvest, rules: PackageRules): Double? {
        for (id in rules.priceViewIds) {
            harvest.byViewId[id]?.let { raw ->
                parseNumber(NUMBER_REGEX.find(raw)?.groupValues?.getOrNull(1))?.let { return it }
            }
        }
        val joined = harvest.texts.joinToString("\n")
        return extractNumber(joined, rules.pricePatterns)
    }

    private fun extractNumber(text: String, patterns: List<String>): Double? {
        for (p in patterns) {
            val match = runCatching { Regex(p).find(text) }.getOrNull() ?: continue
            val value = match.groupValues.getOrNull(1)
            parseNumber(value)?.let { return it }
        }
        return null
    }

    private fun extractString(text: String, patterns: List<String>): String? {
        for (p in patterns) {
            val match = runCatching { Regex(p).find(text) }.getOrNull() ?: continue
            val value = match.groupValues.getOrNull(1)?.trim()
            if (!value.isNullOrEmpty()) return value.take(120)
        }
        return null
    }

    private fun looksLikeOffer(text: String, rules: PackageRules): Boolean {
        if (rules.offerAnchorPatterns.isEmpty()) return true
        return rules.offerAnchorPatterns.any { p ->
            runCatching { Regex(p).containsMatchIn(text) }.getOrDefault(false)
        }
    }

    /**
     * Complète (approche, course) à partir des valeurs trouvées sans libellé.
     *  - rien de connu : 1 valeur = course ; 2+ valeurs = min approche / max course.
     *  - un seul connu : l'autre = la plus grande (course) / plus petite (approche) restante.
     */
    private fun fillFromFallback(approach: Double?, ride: Double?, values: List<Double>): Pair<Double?, Double?> {
        if (values.isEmpty() || (approach != null && ride != null)) return approach to ride
        return when {
            approach == null && ride == null ->
                if (values.size >= 2) values.min() to values.max() else null to values.first()
            ride == null -> approach to values.filter { it != approach }.maxOrNull()
            else -> values.filter { it < ride }.minOrNull() to ride
        }
    }

    /** Renvoie tous les nombres suivis d'une unité (ex: "5,2 km" -> 5.2). */
    private fun allNumbersFollowedBy(text: String, unit: String): List<Double> {
        val regex = Regex("(\\d+(?:[.,]\\d{1,2})?)[\\s\\u00A0\\u202F]*$unit", RegexOption.IGNORE_CASE)
        return regex.findAll(text).mapNotNull { parseNumber(it.groupValues[1]) }.toList()
    }

    /** Convertit "12,50" ou "12.50" -> 12.5 (décimale FR tolérée). */
    private fun parseNumber(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().replace(',', '.').toDoubleOrNull()
    }
}
