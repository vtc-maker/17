package com.drivee.copilote.domain.model

/** Applications VTC chauffeur surveillées (doit rester aligné avec accessibility_service_config.xml). */
object Platforms {
    const val UBER = "com.ubercab.driver"
    const val BOLT = "ee.mtakso.driver"
    const val HEETCH = "com.heetch.driver"

    private val labels = mapOf(UBER to "UBER", BOLT to "BOLT", HEETCH to "HEETCH")

    val packages: Set<String> = labels.keys

    fun label(pkg: String): String = labels[pkg] ?: pkg.substringAfterLast('.').uppercase()
}
