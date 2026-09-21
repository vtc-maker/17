package com.drivee.copilote.data.prefs

import android.content.Context

/**
 * Préférences légères (SharedPreferences) pour l'état d'onboarding.
 * On y mémorise si l'utilisateur a vu et accepté la divulgation (Prominent Disclosure)
 * requise par Google avant toute activation du service d'accessibilité.
 */
object OnboardingPrefs {
    private const val FILE = "onboarding_prefs"
    private const val KEY_DISCLOSURE_ACCEPTED = "disclosure_accepted"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isDisclosureAccepted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DISCLOSURE_ACCEPTED, false)

    fun setDisclosureAccepted(context: Context, accepted: Boolean) {
        prefs(context).edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, accepted).apply()
    }
}
