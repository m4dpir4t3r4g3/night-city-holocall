package com.nightcity.holoos.data.audio

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.SystemSoundSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A cue's audio bytes plus the metadata MediaStore needs. */
class CueAsset(
    val title: String,
    val baseName: String,
    val extension: String,
    val mimeType: String,
    val open: () -> InputStream,
)

/**
 * Copies a cue into the shared audio collection (Ringtones/, Notifications/ or Alarms/) with
 * the matching IS_RINGTONE / IS_NOTIFICATION / IS_ALARM flags, so it shows up in the system
 * sound pickers and can be set via RingtoneManager. Uses scoped storage (no permission needed
 * on API 29+ for files this app owns).
 */
@Singleton
class MediaStoreAudioExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun export(cue: AudioCue, asset: CueAsset): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${directoryFor(cue.systemSlot)}/$FOLDER/"
        val displayName = "${asset.baseName}.${asset.extension}"

        removeStaleSiblings(resolver, collection, relativePath, asset.baseName, keep = displayName)

        // Overwrite our existing row in place so a currently-selected default keeps working.
        findExisting(resolver, collection, relativePath, displayName)?.let { existing ->
            try {
                resolver.openOutputStream(existing, "wt")?.use { out -> asset.open().use { it.copyTo(out) } }
                    ?: throw FileNotFoundException("No output stream for $existing")
                resolver.update(existing, flags(cue).apply { put(MediaStore.Audio.Media.TITLE, asset.title) }, null, null)
                return existing
            } catch (_: SecurityException) {
                // Row belongs to a previous install of the app; insert a fresh copy below.
            } catch (_: FileNotFoundException) {
            }
        }

        val values = flags(cue).apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.TITLE, asset.title)
            put(MediaStore.Audio.Media.MIME_TYPE, asset.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed for $displayName")
        try {
            resolver.openOutputStream(uri)?.use { out -> asset.open().use { it.copyTo(out) } }
                ?: error("No output stream for $uri")
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    private fun flags(cue: AudioCue) = ContentValues().apply {
        put(MediaStore.Audio.Media.IS_RINGTONE, cue.systemSlot == SystemSoundSlot.RINGTONE)
        put(MediaStore.Audio.Media.IS_NOTIFICATION, cue.systemSlot == SystemSoundSlot.NOTIFICATION || cue.systemSlot == null)
        put(MediaStore.Audio.Media.IS_ALARM, cue.systemSlot == SystemSoundSlot.ALARM)
        put(MediaStore.Audio.Media.IS_MUSIC, false)
    }

    private fun findExisting(resolver: ContentResolver, collection: Uri, relativePath: String, displayName: String): Uri? =
        resolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
            arrayOf(relativePath, displayName),
            null,
        )?.use { c -> if (c.moveToFirst()) Uri.withAppendedPath(collection, c.getLong(0).toString()) else null }

    /** Deletes our older copies of the same cue with a different extension (e.g. .wav -> .ogg). */
    private fun removeStaleSiblings(resolver: ContentResolver, collection: Uri, relativePath: String, baseName: String, keep: String) {
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
                "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
                arrayOf(relativePath, "$baseName.%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) != keep) {
                        runCatching { resolver.delete(Uri.withAppendedPath(collection, c.getLong(0).toString()), null, null) }
                    }
                }
            }
        }
    }

    private fun directoryFor(slot: SystemSoundSlot?): String = when (slot) {
        SystemSoundSlot.RINGTONE -> Environment.DIRECTORY_RINGTONES
        SystemSoundSlot.ALARM -> Environment.DIRECTORY_ALARMS
        SystemSoundSlot.NOTIFICATION, null -> Environment.DIRECTORY_NOTIFICATIONS
    }

    private companion object {
        const val FOLDER = "NightCityHoloOS"
    }
}
