package com.nightcity.holoos.data

import com.nightcity.holoos.data.audio.CueSynthesizer
import com.nightcity.holoos.data.audio.WavEncoder
import com.nightcity.holoos.domain.model.AudioCue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class CueSynthesizerTest {
    private val synth = CueSynthesizer()

    @Test fun everyCueRendersAudibleNonClippingAudio() {
        AudioCue.entries.forEach { cue ->
            val pcm = synth.render(cue)
            val seconds = pcm.size / CueSynthesizer.SAMPLE_RATE.toFloat()
            assertTrue("$cue too short: $seconds s", seconds > 0.15f)
            assertTrue("$cue too long: $seconds s", seconds < 6f)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$cue silent", peak > 10_000)
            assertTrue("$cue clips", peak < Short.MAX_VALUE)
            val rms = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
            assertTrue("$cue too quiet overall (rms $rms)", rms > 1_500)
        }
    }

    @Test fun cuesHaveNoDcOffset() {
        AudioCue.entries.forEach { cue ->
            val pcm = synth.render(cue)
            val mean = pcm.sumOf { it.toDouble() } / pcm.size
            assertTrue("$cue DC offset $mean", abs(mean) < 400)
        }
    }

    @Test fun renderingIsDeterministic() {
        AudioCue.entries.forEach { cue -> assertArrayEquals("$cue", synth.render(cue), synth.render(cue)) }
    }

    @Test fun wavHeaderIsValid() {
        val samples = ShortArray(100) { (it * 100).toShort() }
        val bytes = WavEncoder.encode(samples, 22_050)
        assertEquals(WavEncoder.HEADER_SIZE + 200, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("data", String(bytes, 36, 4))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 200, buf.getInt(4))
        assertEquals(22_050, buf.getInt(24))
        assertEquals(200, buf.getInt(40))
        assertEquals(100.toShort(), buf.getShort(46))
    }
}
