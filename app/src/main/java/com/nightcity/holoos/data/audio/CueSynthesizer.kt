package com.nightcity.holoos.data.audio

import com.nightcity.holoos.domain.model.AudioCue
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates original placeholder tones for every slot so the app is fully functional before
 * you drop real audio into res/raw. Pure Kotlin (no Android types) for unit testing.
 */
class CueSynthesizer @Inject constructor() {

    enum class Wave { SINE, SQUARE, SAW, TRIANGLE }

    fun render(cue: AudioCue): ShortArray = when (cue) {
        AudioCue.HOLOCALL_RINGTONE -> holocall()
        AudioCue.NOTIFICATION_CHIRP -> chirp()
        AudioCue.EDDIE_TRANSFER -> eddies()
        AudioCue.NEW_GIG -> gig()
        AudioCue.RELIC_MALFUNCTION -> malfunction()
        AudioCue.NEURAL_LINK -> neuralLink()
        AudioCue.APARTMENT_ALARM -> alarm()
    }.toPcm16()

    // --- cue designs -------------------------------------------------------------------

    private fun holocall() = track {
        repeat(2) {
            repeat(3) { tone(659.3, 70, Wave.SQUARE, 0.32); tone(987.8, 70, Wave.SQUARE, 0.32) }
            silence(140)
            repeat(3) { tone(784.0, 70, Wave.SQUARE, 0.32); tone(1174.7, 70, Wave.SQUARE, 0.32) }
            silence(420)
        }
    }.mix(track { tone(110.0, 2600, Wave.SINE, 0.18, attackMs = 40, releaseMs = 400) })

    private fun chirp() = track {
        sweep(1200.0, 2400.0, 90, Wave.SAW, 0.35)
        silence(30)
        tone(2800.0, 60, Wave.SINE, 0.45, releaseMs = 40)
    }

    private fun eddies() = track {
        tone(987.8, 70, Wave.TRIANGLE, 0.5, releaseMs = 20)
        tone(1318.5, 420, Wave.TRIANGLE, 0.5, releaseMs = 380)
    }.mix(track { silence(70); tone(1324.0, 420, Wave.SINE, 0.25, releaseMs = 380) })

    private fun gig() = track {
        tone(880.0, 110, Wave.SAW, 0.3, releaseMs = 60)
        tone(659.3, 110, Wave.SAW, 0.3, releaseMs = 60)
        tone(523.3, 160, Wave.SAW, 0.3, releaseMs = 60)
        repeat(3) { tone(523.3, 30, Wave.SQUARE, 0.28); silence(25) }
        tone(1046.5, 260, Wave.TRIANGLE, 0.35, releaseMs = 220)
    }

    private fun malfunction() = track {
        val rnd = Random(77)
        var f = 220.0
        repeat(9) {
            val len = 60 + rnd.nextInt(90)
            sweep(f, f * 0.82, len, Wave.SQUARE, 0.34)
            if (rnd.nextBoolean()) noise(25 + rnd.nextInt(40), 0.22) else silence(20 + rnd.nextInt(40))
            f *= 0.86
        }
        sweep(90.0, 45.0, 380, Wave.SAW, 0.4, releaseMs = 200)
    }.bitcrush(levels = 24)

    private fun neuralLink() = track {
        sweep(300.0, 1800.0, 380, Wave.SINE, 0.4)
        silence(40)
        tone(1046.5, 55, Wave.SQUARE, 0.25)
        silence(25)
        tone(1318.5, 55, Wave.SQUARE, 0.25)
        silence(25)
        tone(1568.0, 240, Wave.TRIANGLE, 0.4, releaseMs = 200)
    }

    private fun alarm() = track {
        repeat(3) {
            repeat(4) { tone(880.0, 90, Wave.SQUARE, 0.3); silence(60) }
            silence(120)
            tone(587.3, 480, Wave.SAW, 0.28, attackMs = 60, releaseMs = 200)
            silence(260)
        }
    }

    // --- tiny synth engine -------------------------------------------------------------

    private class Track(val sampleRate: Int) {
        val data = ArrayList<Float>(sampleRate * 3)

        private fun samples(ms: Int) = (sampleRate * ms / 1000.0).roundToInt()

        fun silence(ms: Int) = repeat(samples(ms)) { data += 0f }

        fun tone(freq: Double, ms: Int, wave: Wave, vol: Double, attackMs: Int = 4, releaseMs: Int = 12) =
            sweep(freq, freq, ms, wave, vol, attackMs, releaseMs)

        fun sweep(
            from: Double,
            to: Double,
            ms: Int,
            wave: Wave,
            vol: Double,
            attackMs: Int = 4,
            releaseMs: Int = 12,
        ) {
            val n = samples(ms)
            val a = samples(attackMs).coerceAtLeast(1)
            val r = samples(releaseMs).coerceAtLeast(1)
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val f = from * exp(kotlin.math.ln(to / from) * t) // exponential glide
                phase += f / sampleRate
                val env = minOf(1.0, i.toDouble() / a, (n - i).toDouble() / r)
                data += (osc(wave, phase) * vol * env).toFloat()
            }
        }

        fun noise(ms: Int, vol: Double) {
            val rnd = Random(ms)
            repeat(samples(ms)) { data += ((rnd.nextFloat() * 2f - 1f) * vol).toFloat() }
        }

        private fun osc(wave: Wave, phase: Double): Double {
            val p = phase - floor(phase)
            return when (wave) {
                Wave.SINE -> sin(2 * PI * p)
                Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
                Wave.SAW -> 2 * p - 1
                Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
            }
        }
    }

    private fun track(block: Track.() -> Unit): FloatArray = Track(SAMPLE_RATE).apply(block).data.toFloatArray()

    private fun FloatArray.mix(other: FloatArray): FloatArray =
        FloatArray(maxOf(size, other.size)) { i -> getOrElse(i) { 0f } + other.getOrElse(i) { 0f } }

    private fun FloatArray.bitcrush(levels: Int): FloatArray =
        FloatArray(size) { i -> (this[i] * levels).roundToInt().toFloat() / levels }

    private fun FloatArray.toPcm16(): ShortArray {
        // Normalize every cue to -1 dBFS so quiet designs are as loud as busy ones.
        val peak = maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        return ShortArray(size) { i -> ((this[i] / peak) * 0.9f * Short.MAX_VALUE).roundToInt().toShort() }
    }

    companion object {
        const val SAMPLE_RATE = 22_050
    }
}
