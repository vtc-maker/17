package com.vtccopilot.app.data.config

import kotlinx.serialization.Serializable

/**
 * Configuration DYNAMIQUE d'extraction (règle d'or : ne rien coder en dur).
 *
 * Elle est sérialisable (kotlinx.serialization) : elle peut être embarquée
 * (voir [DefaultParserConfig]) ET/OU téléchargée depuis un serveur distant afin
 * de corriger l'extraction si Uber/Bolt changent leur interface — sans republier
 * l'application.
 *
 * Chaque champ est décrit par une liste de patterns Regex (essayés dans l'ordre).
 * Le premier groupe capturant (`group(1)`) est la valeur extraite.
 */
@Serializable
data class ParserConfig(
    val version: Int = 1,
    val packages: Map<String, PackageRules> = emptyMap()
) {
    fun rulesFor(pkg: String): PackageRules? = packages[pkg]
}

@Serializable
data class PackageRules(
    /** resource-id à privilégier si présents (ex: "com.ubercab.driver:id/fare"). */
    val priceViewIds: List<String> = emptyList(),

    /** Patterns Regex de secours, appliqués sur le texte agrégé de l'écran. */
    val pricePatterns: List<String> = emptyList(),
    val approachDistancePatterns: List<String> = emptyList(),
    val approachTimePatterns: List<String> = emptyList(),
    val rideDistancePatterns: List<String> = emptyList(),
    val rideTimePatterns: List<String> = emptyList(),
    val pickupAddressPatterns: List<String> = emptyList(),
    val destinationAddressPatterns: List<String> = emptyList(),

    /**
     * Motifs signalant qu'une carte d'OFFRE est bien à l'écran (anti faux positifs).
     * Si aucun ne matche, on n'affiche pas d'overlay.
     */
    val offerAnchorPatterns: List<String> = emptyList()
)
