package com.vtccopilot.app.ui.onboarding

/**
 * Texte unique de la Prominent Disclosure exigée par Google Play, réutilisé
 * par l'écran d'accueil (onboarding) ET par la boîte de dialogue du tableau de bord.
 * Doit être affiché AVANT toute redirection vers les réglages d'accessibilité.
 */
object DisclosureText {

    const val TITLE = "VTC Copilot utilise le service d'accessibilité"

    const val INTRO =
        "Pour vous aider à décider en une seconde si une course est rentable, VTC Copilot " +
            "a besoin de lire à l'écran les informations de l'offre affichée par votre " +
            "application VTC (Uber, Bolt, Heetch) : prix proposé, distances et durées d'approche et de " +
            "course, zones de départ et de destination."

    val WE_DO = listOf(
        "Nous lisons uniquement les écrans des applications VTC sélectionnées.",
        "Nous calculons localement la rentabilité (€/km, €/h) et l'affichons dans une pop-up, " +
            "pour limiter la manipulation du téléphone en conduisant."
    )

    val WE_DONT = listOf(
        "Aucune donnée personnelle n'est collectée.",
        "Nous ne lisons pas vos mots de passe, messages ou autres applications.",
        "Aucune donnée d'écran n'est envoyée sur Internet : tout reste sur votre appareil."
    )

    const val FOOTER =
        "Vous pouvez désactiver ce service à tout moment dans Réglages > Accessibilité."
}
