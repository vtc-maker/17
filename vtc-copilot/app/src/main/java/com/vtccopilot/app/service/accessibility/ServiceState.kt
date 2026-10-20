package com.vtccopilot.app.service.accessibility

import android.content.Context

/** État persistant du service : pause et affichage de la bulle flottante. */
object ServiceState {
    private fun prefs(c: Context) = c.getSharedPreferences("vtccopilot_state", Context.MODE_PRIVATE)

    fun isPaused(c: Context): Boolean = prefs(c).getBoolean("paused", false)
    fun setPaused(c: Context, paused: Boolean) = prefs(c).edit().putBoolean("paused", paused).apply()

    fun isBubbleEnabled(c: Context): Boolean = prefs(c).getBoolean("bubble", true)
    fun setBubbleEnabled(c: Context, enabled: Boolean) = prefs(c).edit().putBoolean("bubble", enabled).apply()
}
