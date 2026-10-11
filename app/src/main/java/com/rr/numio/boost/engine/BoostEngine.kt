package com.rr.numio.boost.engine

import android.media.audiofx.LoudnessEnhancer

/**
 * Applies LoudnessEnhancer gain.
 *
 * Two paths, used together:
 *  1. Session 0 (the global output mix). Deprecated by Android but still honoured on most
 *     phones, including Samsung. This is what makes the boost work for every app.
 *  2. Per-app sessions that music/video players announce with
 *     ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION. Used where session 0 is ignored.
 */
class BoostEngine {
    private var global: LoudnessEnhancer? = null
    private val sessions = mutableMapOf<Int, LoudnessEnhancer>()
    private var gainMb = 0

    /** False if this phone refused an effect on the global mix. */
    var globalAvailable = true
        private set

    fun start(gain: Int) {
        gainMb = gain
        if (global == null) {
            global = try {
                LoudnessEnhancer(0).also { globalAvailable = true }
            } catch (e: Throwable) {
                globalAvailable = false
                null
            }
        }
        applyAll()
    }

    fun setGain(gain: Int) {
        gainMb = gain
        applyAll()
    }

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0 || sessions.containsKey(sessionId)) return
        try {
            val fx = LoudnessEnhancer(sessionId)
            sessions[sessionId] = fx
            apply(fx)
        } catch (_: Throwable) {
            // Player's session already gone or effect not allowed; nothing to do.
        }
    }

    fun detachSession(sessionId: Int) {
        sessions.remove(sessionId)?.let { safeRelease(it) }
    }

    fun release() {
        global?.let { safeRelease(it) }
        global = null
        sessions.values.forEach { safeRelease(it) }
        sessions.clear()
    }

    private fun applyAll() {
        global?.let { apply(it) }
        sessions.values.forEach { apply(it) }
    }

    private fun apply(fx: LoudnessEnhancer) {
        try {
            fx.setTargetGain(gainMb)
            fx.enabled = gainMb > 0
        } catch (_: Throwable) {
        }
    }

    private fun safeRelease(fx: LoudnessEnhancer) {
        try {
            fx.enabled = false
            fx.release()
        } catch (_: Throwable) {
        }
    }
}
