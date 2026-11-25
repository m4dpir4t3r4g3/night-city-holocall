package com.nightcity.holoos.data.audio

import android.content.Context
import com.nightcity.holoos.domain.model.AudioCue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Renders synthesized placeholder cues to WAV files once and caches them in app storage. */
@Singleton
class SynthCueCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synthesizer: CueSynthesizer,
) {
    @Synchronized
    fun fileFor(cue: AudioCue): File {
        val dir = File(context.filesDir, "synth_cues").apply { mkdirs() }
        val file = File(dir, "${cue.rawResourceName}_v$VERSION.wav")
        if (!file.exists() || file.length() <= WavEncoder.HEADER_SIZE) {
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(WavEncoder.encode(synthesizer.render(cue), CueSynthesizer.SAMPLE_RATE))
            tmp.renameTo(file)
        }
        return file
    }

    private companion object {
        /** Bump when the synth designs change so cached files are regenerated. */
        const val VERSION = 1
    }
}
