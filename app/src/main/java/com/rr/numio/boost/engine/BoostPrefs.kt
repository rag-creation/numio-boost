package com.rr.numio.boost.engine

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around SharedPreferences so the service, tile and UI agree on the settings. */
class BoostPrefs(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("boost", Context.MODE_PRIVATE)

    /** Boost slider position, 0..100. */
    var boostPercent: Int
        get() = prefs.getInt(KEY_PERCENT, 30)
        set(value) = prefs.edit().putInt(KEY_PERCENT, value.coerceIn(0, 100)).apply()

    /** When true the slider goes up to [HIGH_MAX_MB] instead of [NORMAL_MAX_MB]. */
    var highBoost: Boolean
        get() = prefs.getBoolean(KEY_HIGH, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH, value).apply()

    /** Target gain in millibels (1 dB = 100 mB) for the current settings. */
    fun gainMb(): Int {
        val max = if (highBoost) HIGH_MAX_MB else NORMAL_MAX_MB
        return boostPercent * max / 100
    }

    companion object {
        const val KEY_PERCENT = "boost_percent"
        const val KEY_HIGH = "high_boost"

        /** +12 dB: loud, but most phone speakers stay clean. */
        const val NORMAL_MAX_MB = 1200

        /** +24 dB: unlocked behind a warning. Expect distortion. */
        const val HIGH_MAX_MB = 2400
    }
}
