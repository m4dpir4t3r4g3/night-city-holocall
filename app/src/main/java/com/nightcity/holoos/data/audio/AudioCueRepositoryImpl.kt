package com.nightcity.holoos.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.TypedValue
import android.webkit.MimeTypeMap
import com.nightcity.holoos.core.resources.titleRes
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CueSlot
import com.nightcity.holoos.domain.model.CueSource
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.repository.AudioCueRepository
import com.nightcity.holoos.domain.repository.PreferencesRepository
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves each cue through the fallback chain:
 *   1. user import (copied into filesDir/custom_cues)
 *   2. bundled res/raw/<rawResourceName>.*
 *   3. on-device synthesized placeholder
 */
@Singleton
class AudioCueRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
    private val synth: SynthCueCache,
    private val exporter: MediaStoreAudioExporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AudioCueRepository {

    private val customDir: File get() = File(context.filesDir, "custom_cues").apply { mkdirs() }

    override fun observeSlots(): Flow<List<CueSlot>> = preferences.preferences
        .map { prefs -> AudioCue.entries.map { CueSlot(it, resolveSource(it, prefs)) } }
        .distinctUntilChanged()

    override suspend fun importCustomCue(cue: AudioCue, documentUri: String): Result<CueSource.Custom> = withContext(io) {
        runCatching {
            val source = documentUri.toUri()
            val displayName = queryDisplayName(source) ?: "${cue.rawResourceName}.audio"
            val ext = displayName.substringAfterLast('.', "").lowercase().ifBlank {
                context.contentResolver.getType(source)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "ogg"
            }
            customDir.listFiles { f -> f.name.startsWith(cue.rawResourceName + ".") }?.forEach { it.delete() }
            val target = File(customDir, "${cue.rawResourceName}.$ext")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot read $displayName")
            require(target.length() > 0) { "$displayName is empty" }
            val uri = Uri.fromFile(target).toString()
            preferences.setCustomCue(cue, uri, displayName)
            CueSource.Custom(uri, displayName)
        }
    }

    override suspend fun clearCustomCue(cue: AudioCue) = withContext(io) {
        customDir.listFiles { f -> f.name.startsWith(cue.rawResourceName + ".") }?.forEach { it.delete() }
        preferences.clearCustomCue(cue)
    }

    override suspend fun playableUri(cue: AudioCue): String = withContext(io) {
        customFile(cue, preferences.preferences.first())?.let { return@withContext Uri.fromFile(it).toString() }
        bundledResId(cue).takeIf { it != 0 }?.let { return@withContext "android.resource://${context.packageName}/$it" }
        Uri.fromFile(synth.fileFor(cue)).toString()
    }

    override suspend fun exportToMediaStore(cue: AudioCue): Result<String> = withContext(io) {
        runCatching { exporter.export(cue, assetFor(cue)).toString() }
    }

    private suspend fun assetFor(cue: AudioCue): CueAsset {
        val title = "Holo-OS " + context.getString(cue.titleRes)
        val base = cue.rawResourceName
        customFile(cue, preferences.preferences.first())?.let { file ->
            val ext = file.extension.lowercase()
            return CueAsset(title, base, ext, mimeFor(ext)) { file.inputStream() }
        }
        val resId = bundledResId(cue)
        if (resId != 0) {
            val ext = bundledFileName(resId)?.substringAfterLast('.', "ogg")?.lowercase() ?: "ogg"
            return CueAsset(title, base, ext, mimeFor(ext)) { context.resources.openRawResource(resId) }
        }
        val file = synth.fileFor(cue)
        return CueAsset(title, base, "wav", "audio/x-wav") { file.inputStream() }
    }

    private fun resolveSource(cue: AudioCue, prefs: HoloPreferences): CueSource {
        prefs.customCues[cue]?.let { custom ->
            if (customFile(cue, prefs) != null) return CueSource.Custom(custom.uri, custom.displayName)
        }
        val resId = bundledResId(cue)
        if (resId != 0) return CueSource.Bundled(bundledFileName(resId) ?: cue.rawResourceName)
        return CueSource.Synthesized
    }

    private fun customFile(cue: AudioCue, prefs: HoloPreferences): File? =
        prefs.customCues[cue]?.uri?.let { it.toUri().path }?.let(::File)?.takeIf { it.exists() && it.length() > 0 }

    /** Looked up by name so a missing file never breaks the build (see res/raw/keep.xml). */
    @SuppressLint("DiscouragedApi")
    private fun bundledResId(cue: AudioCue): Int =
        context.resources.getIdentifier(cue.rawResourceName, "raw", context.packageName)

    private fun bundledFileName(resId: Int): String? = runCatching {
        val value = TypedValue()
        context.resources.getValue(resId, value, true)
        value.string?.toString()?.substringAfterLast('/')
    }.getOrNull()

    private fun mimeFor(ext: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "ogg" -> "audio/ogg"
            "wav" -> "audio/x-wav"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            else -> "audio/mpeg"
        }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
