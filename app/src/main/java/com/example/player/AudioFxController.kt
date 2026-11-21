package com.example.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.example.data.model.EqPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioFxState(
    val isAttached: Boolean = false,
    val eqEnabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val bandLevelsMillibels: List<Int> = listOf(0, 0, 0, 0, 0),
    val minLevelMillibels: Int = -1500,
    val maxLevelMillibels: Int = 1500,
    val boostPercent: Int = 0
)

class AudioFxController {

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private val _state = MutableStateFlow(AudioFxState())
    val state: StateFlow<AudioFxState> = _state.asStateFlow()

    @Synchronized
    fun attachToSession(
        audioSessionId: Int,
        initialEqEnabled: Boolean,
        initialPreset: EqPreset,
        initialBands: List<Int>,
        initialBoostPercent: Int
    ) {
        if (audioSessionId <= 0) return
        if (audioSessionId == currentSessionId && equalizer != null) {
            applyConfig(initialEqEnabled, initialPreset, initialBands, initialBoostPercent)
            return
        }

        release()
        currentSessionId = audioSessionId

        var minMb = -1500
        var maxMb = 1500
        var freqs = listOf(60, 230, 910, 3600, 14000)

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            val range = eq.bandLevelRange
            if (range != null && range.size == 2) {
                minMb = range[0].toInt()
                maxMb = range[1].toInt()
            }
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(0)
            if (numBands > 0) {
                freqs = (0 until minOf(numBands, 5)).map { band ->
                    (eq.getCenterFreq(band.toShort()) / 1000).coerceAtLeast(30)
                }
            }
            equalizer = eq
        }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
        }

        _state.value = _state.value.copy(
            isAttached = equalizer != null || loudnessEnhancer != null,
            bandFrequenciesHz = freqs,
            minLevelMillibels = minMb,
            maxLevelMillibels = maxMb
        )

        applyConfig(initialEqEnabled, initialPreset, initialBands, initialBoostPercent)
    }

    @Synchronized
    fun applyConfig(
        eqEnabled: Boolean,
        preset: EqPreset,
        bandLevels: List<Int>,
        boostPercent: Int
    ) {
        val current = _state.value
        val clampedBoost = boostPercent.coerceIn(0, 200)
        val effectiveBands = if (preset == EqPreset.CUSTOM) {
            bandLevels.take(5).ifEmpty { listOf(0, 0, 0, 0, 0) }
        } else {
            preset.bandOffsetsMillibels
        }.map { it.coerceIn(current.minLevelMillibels, current.maxLevelMillibels) }

        runCatching {
            equalizer?.let { eq ->
                eq.enabled = eqEnabled
                val bandCount = minOf(eq.numberOfBands.toInt(), effectiveBands.size)
                for (i in 0 until bandCount) {
                    eq.setBandLevel(i.toShort(), effectiveBands[i].toShort())
                }
            }
        }

        runCatching {
            loudnessEnhancer?.let { booster ->
                if (clampedBoost > 0) {
                    // 0..200% maps smoothly to 0..1500 mB (up to +15 dB clean boost)
                    val targetGainMb = (clampedBoost * 7.5f).toInt().coerceIn(0, 1500)
                    booster.setTargetGain(targetGainMb)
                    booster.enabled = true
                } else {
                    booster.setTargetGain(0)
                    booster.enabled = false
                }
            }
        }

        _state.value = current.copy(
            eqEnabled = eqEnabled,
            preset = preset,
            bandLevelsMillibels = effectiveBands,
            boostPercent = clampedBoost
        )
    }

    @Synchronized
    fun setBandLevel(bandIndex: Int, levelMillibels: Int) {
        val current = _state.value
        if (bandIndex !in current.bandLevelsMillibels.indices) return
        val clamped = levelMillibels.coerceIn(current.minLevelMillibels, current.maxLevelMillibels)
        val updated = current.bandLevelsMillibels.toMutableList().apply {
            this[bandIndex] = clamped
        }
        applyConfig(
            eqEnabled = true,
            preset = EqPreset.CUSTOM,
            bandLevels = updated,
            boostPercent = current.boostPercent
        )
    }

    @Synchronized
    fun setBoostPercent(boostPercent: Int) {
        val current = _state.value
        applyConfig(
            eqEnabled = current.eqEnabled,
            preset = current.preset,
            bandLevels = current.bandLevelsMillibels,
            boostPercent = boostPercent
        )
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        loudnessEnhancer = null
        currentSessionId = 0
        _state.value = _state.value.copy(isAttached = false)
    }
}
