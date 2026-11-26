package com.nightcity.holoos.data.audio

import com.nightcity.holoos.domain.model.AudioCue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Renders the built-in Holo-OS cues: original sound designs in a gritty cyberpunk style
 * (detuned saws, metallic FM, bit-crush, stutter and tape-stop edits). Each cue is used
 * until you import or bundle your own file for that slot. Pure Kotlin, deterministic.
 */
class CueSynthesizer @Inject constructor() {

    fun render(cue: AudioCue): ShortArray = when (cue) {
        AudioCue.HOLOCALL_RINGTONE -> holocall()
        AudioCue.NOTIFICATION_CHIRP -> chirp()
        AudioCue.EDDIE_TRANSFER -> eddies()
        AudioCue.NEW_GIG -> gig()
        AudioCue.RELIC_MALFUNCTION -> malfunction()
        AudioCue.NEURAL_LINK -> neuralLink()
        AudioCue.APARTMENT_ALARM -> alarm()
    }.master().toPcm16()

    // --- cue designs -------------------------------------------------------------------

    /** Driving 125 BPM loop: pulse bass, power-chord stabs, FM "ring" trill, glitch fill. */
    private fun holocall(): FloatArray {
        val step = 120.0 // 16th note at 125 BPM
        val mix = Mix(step * 32 + 400)

        val bass = listOf(33, 33, 45, 33, 36, 33, 31, 33, 29, 29, 41, 29, 31, 31, 43, 31)
        bass.forEachIndexed { i, note ->
            val b = tone(Wave.PULSE, step * 2 - 8, midiHz(note.toDouble()))
                .lowpass(2400.0, 160.0, q = 1.5, curve = 0.5)
                .drive(2.2)
                .pluck(2.0, step * 2.2)
            mix.add(i * step * 2, b, 0.55f)
        }

        listOf(0, 3, 6, 10, 12, 16, 19, 22, 26, 28).forEach { s ->
            val notes = when {
                s < 16 -> listOf(57.0, 64.0, 69.0)
                s < 24 -> listOf(53.0, 60.0, 65.0)
                else -> listOf(55.0, 62.0, 67.0)
            }
            val stab = chord(step * 1.6, notes, seed = s)
                .lowpass(5200.0, 650.0, q = 1.2, curve = 0.6)
                .pluck(2.0, step * 1.8)
            mix.add(s * step, stab, 0.3f)
        }

        for (bar in 0..1) {
            val start = bar * 16 * step
            repeat(3) { r ->
                mix.add(start + r * 70.0, ping(88.0, 150.0, ratio = 2.0, index = 1.8), 0.3f)
                mix.add(start + r * 70.0 + 35.0, ping(93.0, 150.0, ratio = 2.0, index = 1.8), 0.26f)
            }
            mix.add(start + 8 * step, ping(84.0, 320.0), 0.28f)
            mix.add(start + 9 * step, ping(91.0, 320.0), 0.24f)
        }

        for (s in 0 until 32) {
            if (s % 4 == 0) mix.add(s * step, kick(), 0.45f)
            if (s % 4 == 2) mix.add(s * step, hat(s), 0.16f) else if (s % 2 == 1) mix.add(s * step, hat(s, 18.0), 0.06f)
        }

        mix.process { it.stutter(atMs = 28 * step, sliceMs = step / 2, repeats = 6, decay = 0.85f) }
        return mix.data
    }

    /** Two crushed FM blips with a noise tick and a short stutter. */
    private fun chirp(): FloatArray {
        val mix = Mix(220.0)
        mix.add(0.0, noise(12.0, 3).highpass(6000.0).pluck(0.2, 12.0), 0.35f)
        mix.add(0.0, fm(70.0, midiHz(93.0), 2.0, 3.0, 40.0, carrierEnd = midiHz(100.0)).pluck(1.0, 70.0).crush(8, 2), 0.5f)
        mix.add(75.0, fm(110.0, midiHz(100.0), 2.0, 2.0, 50.0).pluck(1.0, 110.0).crush(8, 2), 0.5f)
        mix.process { it.stutter(75.0, 18.0, 3, decay = 0.8f).echo(95.0, 0.3f, 0.22f, tailMs = 260.0) }
        return mix.data
    }

    /** Data burst into a metallic two-note "credits received" chime. */
    private fun eddies(): FloatArray {
        val mix = Mix(900.0)
        mix.add(0.0, dataBurst(seed = 7, count = 8, stepMs = 13.0, lowNote = 84, highNote = 100), 0.22f)
        mix.add(105.0, tone(Wave.SINE, 140.0, 120.0, 50.0, curve = 0.4).pluck(1.0, 140.0), 0.6f)
        mix.add(110.0, ping(83.0, 260.0, ratio = 3.5, index = 2.6), 0.45f)
        mix.add(190.0, ping(90.0, 650.0, ratio = 3.5, index = 2.2), 0.5f)
        mix.add(190.0, tone(Wave.SINE, 650.0, midiHz(102.0)).pluck(1.0, 500.0), 0.08f)
        mix.process { it.echo(135.0, 0.35f, 0.3f, tailMs = 400.0) }
        return mix.data
    }

    /** Tense distorted stab, stutter, then a high ping: a new job just dropped. */
    private fun gig(): FloatArray {
        val mix = Mix(1200.0)
        mix.add(
            0.0,
            chord(380.0, listOf(45.0, 52.0, 57.0), seed = 11).lowpass(3400.0, 260.0, q = 1.6, curve = 0.5).drive(3.0).pluck(2.0, 380.0),
            0.5f,
        )
        mix.add(0.0, tone(Wave.SINE, 420.0, 58.0, 52.0).pluck(3.0, 420.0), 0.35f)
        mix.add(0.0, ping(81.0, 520.0, ratio = 1.41, index = 3.0), 0.3f)
        mix.add(
            360.0,
            chord(190.0, listOf(48.0, 55.0, 60.0), seed = 12).lowpass(2800.0, 400.0, q = 1.4, curve = 0.5).drive(3.0).pluck(2.0, 200.0),
            0.45f,
        )
        mix.add(690.0, ping(88.0, 480.0, ratio = 3.5, index = 2.0), 0.35f)
        mix.process { it.stutter(360.0, 45.0, 3, decay = 0.85f).echo(170.0, 0.3f, 0.22f, tailMs = 300.0) }
        return mix.data
    }

    /** System failure: sub drop, warbling square, crushed noise, dropouts, tape-stop. */
    private fun malfunction(): FloatArray {
        val rnd = Random(77)
        val mix = Mix(1700.0)
        mix.add(0.0, tone(Wave.SINE, 700.0, 85.0, 28.0, curve = 0.6).pluck(4.0, 700.0), 0.7f)

        var at = 0.0
        var base = 220.0
        val intervals = listOf(1.0, 0.75, 1.5, 0.5, 1.33)
        while (at < 1250.0) {
            val segMs = 50.0 + rnd.nextInt(110)
            val f = base * intervals[rnd.nextInt(intervals.size)]
            val seg = tone(Wave.SQUARE, segMs, f, f * (0.8 + rnd.nextDouble() * 0.3), vibHz = 30.0, vibCents = 60.0).fade(1.0, 4.0)
            mix.add(at, seg, 0.4f)
            at += segMs
            base *= 0.93
        }
        repeat(6) { i ->
            val burst = noise(30.0 + rnd.nextInt(60), 100 + i).bandpass(800.0 + rnd.nextInt(3000), q = 2.0).pluck(1.0, 80.0)
            mix.add(rnd.nextDouble() * 1300.0, burst, 0.6f)
        }
        mix.add(280.0, fm(420.0, 1400.0, 1.01, 9.0, carrierEnd = 260.0).pluck(5.0, 420.0), 0.22f)

        mix.process {
            it.drive(4.0)
                .crush(bits = 5, hold = 5)
                .lowpass(5000.0)
                .dropouts(count = 5, minMs = 15.0, maxMs = 60.0, seed = 9)
                .stutter(520.0, 35.0, 4, decay = 0.9f)
                .stutter(1050.0, 25.0, 5, decay = 0.85f)
                .tapeStop(520.0)
        }
        return mix.data
    }

    /** Resonant supersaw rise with data chatter, locking into a bright FM chord. */
    private fun neuralLink(): FloatArray {
        val mix = Mix(1300.0)
        mix.add(
            0.0,
            superSaw(520.0, midiHz(40.0), midiHz(52.0)).lowpass(180.0, 7000.0, q = 2.2, curve = 1.6).adsr(30.0, 0.0, 1.0, 60.0),
            0.4f,
        )
        mix.add(0.0, dataBurst(seed = 21, count = 18, stepMs = 26.0, lowNote = 76, highNote = 98, rising = true), 0.16f)
        mix.add(500.0, tone(Wave.SINE, 160.0, 110.0, 45.0, curve = 0.4).pluck(1.0, 160.0), 0.6f)
        listOf(76.0, 83.0, 88.0, 95.0).forEachIndexed { i, n ->
            mix.add(500.0 + i * 18.0, ping(n, 700.0, ratio = 2.0, index = 1.4), 0.24f)
        }
        mix.process { it.echo(120.0, 0.3f, 0.22f, tailMs = 250.0) }
        return mix.data
    }

    /** 140 BPM wake-up loop: crushed alarm beeps, kick, pumping bass, bar-2 riser. */
    private fun alarm(): FloatArray {
        val step = 107.0 // 16th note at 140 BPM
        val steps = 32
        val mix = Mix(step * steps + 300)
        for (s in 0 until steps) {
            val t = s * step
            if (s % 4 == 0) mix.add(t, kick(), 0.75f)
            if (s % 4 == 0 || s % 4 == 2) {
                val note = if (s >= 16 && s % 8 >= 4) 86.0 else 81.0
                val a = tone(Wave.SQUARE, step * 0.8, midiHz(note))
                val b = tone(Wave.SQUARE, step * 0.8, midiHz(note + 0.12))
                for (i in a.indices) a[i] = (a[i] + b[i]) * 0.5f
                mix.add(t, a.highpass(500.0).crush(7, 2).adsr(2.0, 20.0, 0.7, 15.0), 0.33f)
            }
            if (s % 2 == 1) mix.add(t, hat(s), 0.08f)
        }
        val bass = tone(Wave.SAW, step * steps, midiHz(33.0)).lowpass(700.0, q = 1.2)
        val beat = len(step * 4)
        for (i in bass.indices) {
            val phase = (i % beat).toDouble() / beat
            bass[i] *= (0.25 + 0.75 * min(1.0, phase * 3)).toFloat() // sidechain-style pump
        }
        mix.add(0.0, bass.fade(2.0, 40.0), 0.4f)
        mix.add(
            step * 16,
            superSaw(step * 16, midiHz(57.0), midiHz(69.0)).lowpass(500.0, 6000.0, q = 1.5, curve = 1.5).adsr(400.0, 0.0, 1.0, 30.0),
            0.22f,
        )
        return mix.data
    }

    // --- instruments -------------------------------------------------------------------

    private fun kick() = tone(Wave.SINE, 160.0, 150.0, 42.0, curve = 0.35).pluck(1.0, 160.0)

    private fun hat(seed: Int, ms: Double = 35.0) = noise(ms, seed).highpass(7500.0).pluck(0.5, ms)

    /** Metallic FM ping; ratio 3.5 = inharmonic chime, 2.0 = clean bell. */
    private fun ping(note: Double, decayMs: Double, ratio: Double = 3.5, index: Double = 2.5) =
        fm(decayMs, midiHz(note), ratio, index, indexDecayMs = decayMs * 0.3).pluck(1.0, decayMs)

    /** Rapid crushed square blips at random pitches: "data transfer" chatter. */
    private fun dataBurst(seed: Int, count: Int, stepMs: Double, lowNote: Int, highNote: Int, rising: Boolean = false): FloatArray {
        val rnd = Random(seed)
        val notes = List(count) { rnd.nextInt(lowNote, highNote) }.let { if (rising) it.sorted() else it }
        val mix = Mix(count * stepMs + 20)
        notes.forEachIndexed { i, n ->
            mix.add(i * stepMs, tone(Wave.SQUARE, stepMs * 0.8, midiHz(n.toDouble())).pluck(0.3, stepMs), 1f)
        }
        return mix.data.crush(6, 3).highpass(1200.0)
    }

    // --- master bus --------------------------------------------------------------------

    /**
     * Loudness-matches every cue (speaker-weighted RMS of the audible part) so a sparse ringtone and a
     * wall-of-noise glitch play at similar volume, then a soft-knee limiter rounds peaks
     * into a -0.9 dBFS ceiling. The 16 kHz low-pass tames bit-crush stair-steps that would
     * otherwise overshoot between samples on the phone's DAC.
     */
    private fun FloatArray.master(): FloatArray {
        val x = highpass(28.0).lowpass(16_000.0).trimTail()
        // Peaky material (the ringtone's pings and stabs) hits the limiter before its average
        // level comes up, so converge in a few gain -> limit passes.
        repeat(MAX_LOUDNESS_PASSES) {
            val rms = speakerRms(x)
            if (rms <= 0.0) return x
            val g = (TARGET_RMS / rms).coerceIn(0.25, 1.5)
            if (abs(g - 1.0) < 0.06) return x // within ~0.5 dB
            x.gain(g.toFloat())
            softLimit(x)
        }
        return x
    }

    /**
     * RMS of the audible part as a phone speaker hears it: content below ~200 Hz barely
     * reproduces, so it shouldn't count towards how loud the cue is.
     */
    private fun speakerRms(x: FloatArray): Double {
        val heard = x.copyOf().highpass(200.0)
        val peak = heard.maxOf { abs(it) }
        var sum = 0.0
        var count = 0
        for (v in heard) if (abs(v) > peak * 0.03f) { sum += v.toDouble() * v; count++ }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    /** Transparent below the knee; rounds anything above into the ceiling. */
    private fun softLimit(x: FloatArray) {
        val range = CEILING - KNEE
        for (i in x.indices) {
            val a = abs(x[i])
            if (a > KNEE) x[i] = sign(x[i]) * (KNEE + range * tanh((a - KNEE) / range))
        }
    }

    private fun FloatArray.toPcm16(): ShortArray =
        ShortArray(size) { i -> (this[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort() }

    companion object {
        const val SAMPLE_RATE = SR
        private const val TARGET_RMS = 0.22 // about -13 dBFS
        private const val KNEE = 0.6f
        private const val CEILING = 0.9f
        private const val MAX_LOUDNESS_PASSES = 6
    }
}
