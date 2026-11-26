package com.nightcity.holoos.domain.logic

import com.nightcity.holoos.domain.model.AudioCue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stops cue spam: banking apps often post 2-3 notifications per transaction and
 * progress-style updates re-post the same key.
 */
@Singleton
class CueThrottle @Inject constructor() {
    private val lastByKey = LinkedHashMap<String, Long>()
    private val lastByCue = HashMap<AudioCue, Long>()

    @Synchronized
    fun shouldFire(cue: AudioCue, key: String, onlyAlertOnce: Boolean, nowMs: Long): Boolean {
        val keySeen = lastByKey[key]
        if (keySeen != null && (onlyAlertOnce || nowMs - keySeen < SAME_KEY_WINDOW_MS)) return false
        val cueSeen = lastByCue[cue]
        if (cueSeen != null && nowMs - cueSeen < SAME_CUE_WINDOW_MS) return false
        lastByKey[key] = nowMs
        lastByCue[cue] = nowMs
        if (lastByKey.size > MAX_KEYS) lastByKey.remove(lastByKey.keys.first())
        return true
    }

    companion object {
        const val SAME_KEY_WINDOW_MS = 10_000L
        const val SAME_CUE_WINDOW_MS = 2_500L
        private const val MAX_KEYS = 200
    }
}
