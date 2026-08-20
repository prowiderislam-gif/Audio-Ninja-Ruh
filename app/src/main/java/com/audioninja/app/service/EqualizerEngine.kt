package com.audioninja.app.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Wraps Android's built-in Equalizer and BassBoost audio effects for a single
 * audio session. Create one per MediaPlayer session, apply saved settings,
 * and release it when that player is done — these are lightweight OS-level
 * effects, not custom audio processing, so they work reliably across devices.
 */
class EqualizerEngine(audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    val numberOfBands: Int
    val bandFrequencies: List<Int>
    val levelRangeMb: IntArray

    init {
        var bands = 0
        var freqs = listOf<Int>()
        var range = intArrayOf(-1500, 1500)
        try {
            val eq = Equalizer(0, audioSessionId)
            bands = eq.numberOfBands.toInt()
            freqs = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            range = eq.bandLevelRange
            equalizer = eq
        } catch (_: Exception) { }
        try {
            bassBoost = BassBoost(0, audioSessionId)
        } catch (_: Exception) { }

        numberOfBands = bands
        bandFrequencies = freqs
        levelRangeMb = range
    }

    fun setEnabled(enabled: Boolean) {
        try { equalizer?.enabled = enabled } catch (_: Exception) { }
        try { bassBoost?.enabled = enabled } catch (_: Exception) { }
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        try { equalizer?.setBandLevel(band.toShort(), levelMb.toShort()) } catch (_: Exception) { }
    }

    fun applyBandLevels(levels: List<Int>) {
        levels.forEachIndexed { index, level ->
            setBandLevel(index, level)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        try { bassBoost?.setStrength(strength.toShort()) } catch (_: Exception) { }
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) { }
        try { bassBoost?.release() } catch (_: Exception) { }
        equalizer = null
        bassBoost = null
    }
}
