package com.cym.inventory

import android.content.Context

private const val PREFS_NAME = "cym_settings"
private const val KEY_DARK_MODE = "darkMode"

/** Manual light/dark override chosen from the in-app toggle; null means "follow the system" (not chosen yet). */
internal object ThemePrefs {
    fun getOverride(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_DARK_MODE)) prefs.getBoolean(KEY_DARK_MODE, false) else null
    }

    fun setOverride(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK_MODE, dark).apply()
    }
}
