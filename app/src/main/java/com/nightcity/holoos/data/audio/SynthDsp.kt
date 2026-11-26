package com.nightcity.holoos.data.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.random.Random

// Small offline DSP toolkit used to render the built-in cues: band-limited oscillators,
// supersaw, 2-operator FM, a zero-delay-feedback state-variable filter, drive, bitcrush,
// echo and glitch edits (stutter, dropouts, tape-stop). Pure Kotlin so it runs on the JVM
// in unit tests as well as on-device. Everything is seeded, so renders are deterministic.

internal const val SR = 44_100

internal fun len(ms: Double): Int = (SR * ms / 1000.0).roundToInt().coerceAtLeast(1)

internal fun midiHz(note: Double): Double = 440.0 * 2.0.pow((note - 69.0) / 12.0)

internal enum class Wave { SINE, TRIANGLE, SAW, SQUARE, PULSE }

private fun polyBlep(t: Double, dt: Double): Double = when {
    t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
    t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
    else -> 0.0
}

private fun waveSample(wave: Wave, p: Double, dt: Double): Double = when (wave) {
    Wave.SINE -> sin(2 * PI * p)
    Wave.TRIANGLE -> 4 * abs(p - 0.5) - 1
    Wave.SAW -> 2 * p - 1 - polyBlep(p, dt)
    Wave.SQUARE -> (if (p < 0.5) 1.0 else -1.0) + polyBlep(p, dt) - polyBlep((p + 0.5) % 1.0, dt)
    // 25% duty, DC removed.
    Wave.PULSE -> ((if (p < 0.25) 1.0 else -1.0) + polyBlep(p, dt) - polyBlep((p + 0.75) % 1.0, dt) + 0.5) * 0.8
}

// --- generators -------------------------------------------------------------------------

/** Band-limited oscillator with an exponential glide from [f0] to [f1] and optional vibrato. */
internal fun tone(
    wave: Wave,
    ms: Double,
    f0: Double,
    f1: Double = f0,
    curve: Double = 1.0,
    phase: Double = 0.0,
    vibHz: Double = 0.0,
    vibCents: Double = 0.0,
): FloatArray {
    val n = len(ms)
    val out = FloatArray(n)
    val ratio = f1 / f0
    var p = phase
    for (i in 0 until n) {
        var f = f0 * ratio.pow((i.toDouble() / n).pow(curve))
        if (vibCents != 0.0) f *= 2.0.pow(vibCents / 1200.0 * sin(2 * PI * vibHz * i / SR))
        val dt = (f / SR).coerceAtMost(0.45)
        p += dt
        p -= floor(p)
        out[i] = waveSample(wave, p, dt).toFloat()
    }
    return out
}

/** Detuned saw stack: the wide, aggressive lead/pad sound. */
internal fun superSaw(
    ms: Double,
    f0: Double,
    f1: Double = f0,
    voices: Int = 7,
    detuneCents: Double = 22.0,
    seed: Int = 1,
): FloatArray {
    val rnd = Random(seed)
    val out = FloatArray(len(ms))
    for (v in 0 until voices) {
        val spread = if (voices == 1) 0.0 else v.toDouble() / (voices - 1) * 2 - 1
        val k = 2.0.pow(spread * detuneCents / 1200.0)
        val layer = tone(Wave.SAW, ms, f0 * k, f1 * k, phase = rnd.nextDouble())
        for (i in out.indices) out[i] += layer[i]
    }
    return out.gain(1f / sqrt(voices.toFloat()))
}

/** Supersaw chord on several MIDI notes. */
internal fun chord(ms: Double, notes: List<Double>, voices: Int = 5, detuneCents: Double = 16.0, seed: Int = 1): FloatArray {
    val out = FloatArray(len(ms))
    notes.forEachIndexed { i, note ->
        val layer = superSaw(ms, midiHz(note), voices = voices, detuneCents = detuneCents, seed = seed * 31 + i)
        for (j in out.indices) out[j] += layer[j]
    }
    return out.gain(1f / notes.size)
}

/**
 * Two-operator FM. Non-integer [ratio]s (e.g. 3.5, 1.41) give metallic, inharmonic pings;
 * the modulation index decays so hits start bright and settle into a pure tone.
 */
internal fun fm(
    ms: Double,
    carrier: Double,
    ratio: Double,
    index: Double,
    indexDecayMs: Double = 1e9,
    carrierEnd: Double = carrier,
): FloatArray {
    val n = len(ms)
    val out = FloatArray(n)
    val glide = carrierEnd / carrier
    val idxK = 1000.0 / (SR * indexDecayMs)
    var pc = 0.0
    var pm = 0.0
    for (i in 0 until n) {
        val fc = carrier * glide.pow(i.toDouble() / n)
        pc += fc / SR
        pm += fc * ratio / SR
        pc -= floor(pc)
        pm -= floor(pm)
        out[i] = sin(2 * PI * pc + index * exp(-i * idxK) * sin(2 * PI * pm)).toFloat()
    }
    return out
}

internal fun noise(ms: Double, seed: Int): FloatArray {
    val rnd = Random(seed)
    return FloatArray(len(ms)) { rnd.nextFloat() * 2f - 1f }
}

// --- envelopes & dynamics ---------------------------------------------------------------

/** Linear attack then exponential decay reaching -60 dB after [decayMs]. */
internal fun FloatArray.pluck(attackMs: Double, decayMs: Double): FloatArray {
    val a = len(attackMs)
    val k = ln(1000.0) / len(decayMs)
    for (i in indices) {
        val env = if (i < a) i.toDouble() / a else exp(-(i - a) * k)
        this[i] *= env.toFloat()
    }
    return this
}

internal fun FloatArray.adsr(attackMs: Double, decayMs: Double, sustain: Double, releaseMs: Double): FloatArray {
    val a = len(attackMs)
    val d = len(decayMs)
    val r = len(releaseMs)
    for (i in indices) {
        val ad = when {
            i < a -> i.toDouble() / a
            i < a + d -> 1.0 - (1.0 - sustain) * (i - a) / d
            else -> sustain
        }
        this[i] *= (ad * min(1.0, (size - i).toDouble() / r)).toFloat()
    }
    return this
}

internal fun FloatArray.fade(inMs: Double = 1.0, outMs: Double = 5.0): FloatArray {
    val a = len(inMs)
    val r = len(outMs)
    for (i in indices) this[i] *= min(1.0, min(i.toDouble() / a, (size - i).toDouble() / r)).toFloat()
    return this
}

internal fun FloatArray.gain(g: Float): FloatArray {
    for (i in indices) this[i] *= g
    return this
}

internal fun FloatArray.normalizePeak(target: Float = 1f): FloatArray {
    val peak = maxOf { abs(it) }
    return if (peak > 0f) gain(target / peak) else this
}

/** Soft-clip saturation; higher [amount] = more grit. */
internal fun FloatArray.drive(amount: Double): FloatArray {
    val norm = tanh(amount)
    for (i in indices) this[i] = (tanh(this[i] * amount) / norm).toFloat()
    return this
}

/** Bit-depth and sample-rate reduction. */
internal fun FloatArray.crush(bits: Int, hold: Int = 1): FloatArray {
    val levels = (1 shl (bits - 1)).toFloat()
    var held = 0f
    for (i in indices) {
        if (i % hold == 0) held = (this[i] * levels).roundToInt() / levels
        this[i] = held
    }
    return this
}

// --- filters ----------------------------------------------------------------------------

internal enum class FilterMode { LOW, HIGH, BAND }

/** Zero-delay-feedback state-variable filter (stable at any cutoff) with an exponential sweep. */
internal fun FloatArray.filter(mode: FilterMode, from: Double, to: Double = from, q: Double = 0.707, curve: Double = 1.0): FloatArray {
    val k = 1.0 / q
    val ratio = to / from
    var ic1 = 0.0
    var ic2 = 0.0
    for (i in indices) {
        val fc = (from * ratio.pow((i.toDouble() / size).pow(curve))).coerceIn(10.0, SR * 0.45)
        val g = tan(PI * fc / SR)
        val a1 = 1.0 / (1.0 + g * (g + k))
        val a2 = g * a1
        val a3 = g * a2
        val v0 = this[i].toDouble()
        val v3 = v0 - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        this[i] = when (mode) {
            FilterMode.LOW -> v2
            FilterMode.HIGH -> v0 - k * v1 - v2
            FilterMode.BAND -> v1
        }.toFloat()
    }
    return this
}

internal fun FloatArray.lowpass(from: Double, to: Double = from, q: Double = 0.707, curve: Double = 1.0) =
    filter(FilterMode.LOW, from, to, q, curve)

internal fun FloatArray.highpass(cutoff: Double, q: Double = 0.707) = filter(FilterMode.HIGH, cutoff, cutoff, q)

internal fun FloatArray.bandpass(cutoff: Double, q: Double = 1.0) = filter(FilterMode.BAND, cutoff, cutoff, q)

// --- time effects & glitch edits --------------------------------------------------------

/** Feedback delay. Returns a longer buffer when [tailMs] > 0. */
internal fun FloatArray.echo(delayMs: Double, feedback: Float, mix: Float, tailMs: Double = 0.0): FloatArray {
    val d = len(delayMs)
    val out = copyOf(size + if (tailMs > 0) len(tailMs) else 0)
    val line = FloatArray(out.size)
    for (i in out.indices) {
        val dry = if (i < size) this[i] else 0f
        val delayed = if (i >= d) line[i - d] else 0f
        line[i] = dry + delayed * feedback
        out[i] = dry + delayed * mix
    }
    return out
}

/** Classic stutter: the slice at [atMs] plays [repeats] times in a row, each quieter by [decay]. */
internal fun FloatArray.stutter(atMs: Double, sliceMs: Double, repeats: Int, decay: Float = 1f): FloatArray {
    val at = len(atMs).coerceAtMost(size)
    val slice = len(sliceMs).coerceAtMost(size - at)
    if (slice <= 0 || repeats < 2) return this
    val ramp = len(0.5).coerceAtMost(slice / 2)
    val out = FloatArray(size + slice * (repeats - 1))
    copyInto(out, 0, 0, at)
    var pos = at
    var g = 1f
    repeat(repeats) {
        for (j in 0 until slice) {
            val edge = min(j, slice - 1 - j)
            val e = if (edge < ramp) edge.toFloat() / ramp else 1f
            out[pos + j] = this[at + j] * g * e
        }
        pos += slice
        g *= decay
    }
    copyInto(out, pos, at + slice, size)
    return out
}

/** Random signal dropouts, like a failing link. */
internal fun FloatArray.dropouts(count: Int, minMs: Double, maxMs: Double, seed: Int): FloatArray {
    val rnd = Random(seed)
    val ramp = len(0.7)
    repeat(count) {
        val w = len(minMs + rnd.nextDouble() * (maxMs - minMs))
        if (w >= size) return@repeat
        val start = rnd.nextInt(0, size - w)
        for (j in 0 until w) {
            val edge = min(j, w - 1 - j)
            this[start + j] *= if (edge < ramp) 1f - edge.toFloat() / ramp else 0f
        }
    }
    return this
}

/** Tape-stop: the last [lastMs] decelerates to a halt, dragging the pitch down. */
internal fun FloatArray.tapeStop(lastMs: Double): FloatArray {
    val n = len(lastMs).coerceAtMost(size)
    val start = size - n
    val out = copyOf()
    var read = start.toDouble()
    for (i in 0 until n) {
        val speed = 1.0 - i.toDouble() / n
        val idx = read.toInt().coerceIn(0, size - 1)
        val next = (idx + 1).coerceAtMost(size - 1)
        val frac = read - idx
        out[start + i] = ((this[idx] * (1 - frac) + this[next] * frac) * speed).toFloat()
        read += speed
    }
    return out
}

/** Drops trailing near-silence (below -60 dB) left over from decays and echo tails. */
internal fun FloatArray.trimTail(keepMs: Double = 30.0): FloatArray {
    val peak = maxOf { abs(it) }
    if (peak <= 0f) return this
    var last = size - 1
    while (last > 0 && abs(this[last]) < peak * 0.001f) last--
    val end = (last + len(keepMs)).coerceAtMost(size)
    return copyOf(end).fade(0.0, keepMs)
}

/** Mixing canvas: place layers at millisecond offsets, then post-process the whole bus. */
internal class Mix(totalMs: Double) {
    var data = FloatArray(len(totalMs))
        private set

    fun add(atMs: Double, layer: FloatArray, gain: Float = 1f) {
        val off = (SR * atMs / 1000.0).roundToInt()
        val needed = off + layer.size
        if (needed > data.size) data = data.copyOf(needed)
        for (i in layer.indices) data[off + i] += layer[i] * gain
    }

    fun process(block: (FloatArray) -> FloatArray) {
        data = block(data)
    }
}
