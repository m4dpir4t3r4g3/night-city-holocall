package com.nightcity.holoos.data.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE encoder for 16-bit mono PCM. */
object WavEncoder {
    const val HEADER_SIZE = 44

    fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM chunk size
        buffer.putShort(1) // audio format = PCM
        buffer.putShort(1) // channels
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2) // byte rate
        buffer.putShort(2) // block align
        buffer.putShort(16) // bits per sample
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
