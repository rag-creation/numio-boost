package com.rr.numio.boost.engine

import android.media.audiofx.LoudnessEnhancer

/** What the boost is actually doing right now, shown in the app. */
data class BoostStatus(
    /** The effect on the global output mix exists and this app controls it. */
    val globalActive: Boolean = false,
    /** The global effect exists but another app (or the system) holds control of it. */
    val globalBlocked: Boolean = false,
    /** Number of player sessions boosted individually (used only when global isn't active). */
    val appSessions: Int = 0,
)

/**
 * Applies LoudnessEnhancer gain.
 *
 * Normally the boost runs on session 0, the global output mix, so every app gets louder.
 * Only when that isn't possible (the phone refuses it, or another app has control of it)
 * do we fall back to boosting the sessions that music/video players announce.
 *
 * The two paths are never used at the same time: stacking them boosts the same audio twice,
 * which drives the limiter hard and pushes vocals down under the music.
 */
class BoostEngine(private val onStatus: (BoostStatus) -> Unit) {
    private var global: LoudnessEnhancer? = null
    private val knownSessions = mutableSetOf<Int>()
    private val sessionFx = mutableMapOf<Int, LoudnessEnhancer>()
    private var gainMb = 0

    fun start(gain: Int) {
        gainMb = gain
        if (global == null) {
            global = try {
                LoudnessEnhancer(0).apply {
                    setControlStatusListener { _, _ -> sync() }
                }
            } catch (_: Throwable) {
                null
            }
        }
        sync()
    }

    fun setGain(gain: Int) {
        gainMb = gain
        sync()
    }

    fun attachSession(sessionId: Int) {
        if (sessionId <= 0) return
        knownSessions += sessionId
        sync()
    }

    fun detachSession(sessionId: Int) {
        knownSessions -= sessionId
        sessionFx.remove(sessionId)?.let { safeRelease(it) }
        report()
    }

    fun release() {
        global?.let { safeRelease(it) }
        global = null
        sessionFx.values.forEach { safeRelease(it) }
        sessionFx.clear()
        knownSessions.clear()
    }

    /** Apply the gain to whichever path is usable, and tear down the other. */
    private fun sync() {
        val g = global
        val globalWorks = g != null && hasControl(g) && apply(g)
        if (globalWorks) {
            sessionFx.values.forEach { safeRelease(it) }
            sessionFx.clear()
        } else {
            for (id in knownSessions.toList()) {
                val fx = sessionFx[id] ?: try {
                    LoudnessEnhancer(id).also { sessionFx[id] = it }
                } catch (_: Throwable) {
                    // Player's session already gone or effect not allowed.
                    knownSessions -= id
                    continue
                }
                apply(fx)
            }
        }
        report()
    }

    private fun report() {
        val g = global
        val active = g != null && hasControl(g)
        onStatus(
            BoostStatus(
                globalActive = active,
                globalBlocked = g != null && !active,
                appSessions = if (active) 0 else sessionFx.size,
            )
        )
    }

    private fun hasControl(fx: LoudnessEnhancer): Boolean =
        try { fx.hasControl() } catch (_: Throwable) { false }

    /** Returns false if the phone rejected the change. */
    private fun apply(fx: LoudnessEnhancer): Boolean = try {
        fx.setTargetGain(gainMb)
        fx.enabled = gainMb > 0
        true
    } catch (_: Throwable) {
        false
    }

    private fun safeRelease(fx: LoudnessEnhancer) {
        try {
            fx.enabled = false
            fx.release()
        } catch (_: Throwable) {
        }
    }
}
