package com.nightcity.holoos.domain.repository

import com.nightcity.holoos.domain.model.AlertMode
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CalibrationState
import com.nightcity.holoos.domain.model.ContactsResult
import com.nightcity.holoos.domain.model.CuePlayback
import com.nightcity.holoos.domain.model.CueSlot
import com.nightcity.holoos.domain.model.CueSource
import com.nightcity.holoos.domain.model.HapticPattern
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.model.IconFrame
import com.nightcity.holoos.domain.model.IconTint
import com.nightcity.holoos.domain.model.SystemSoundSlot
import com.nightcity.holoos.domain.model.SystemVitals
import kotlinx.coroutines.flow.Flow

// URIs cross the domain boundary as Strings so this layer stays free of android.* types.

interface AudioCueRepository {
    fun observeSlots(): Flow<List<CueSlot>>

    /** Copies the picked document into app storage and makes it the cue's source. */
    suspend fun importCustomCue(cue: AudioCue, documentUri: String): Result<CueSource.Custom>

    suspend fun clearCustomCue(cue: AudioCue)

    /** A URI MediaPlayer can open. Always resolves (falls back to the synth placeholder). */
    suspend fun playableUri(cue: AudioCue): String

    /** Writes the cue into MediaStore.Audio with ringtone/notification/alarm flags. */
    suspend fun exportToMediaStore(cue: AudioCue): Result<String>
}

interface SystemSoundRepository {
    fun canWriteSystemSettings(): Boolean
    suspend fun setDefaultSound(slot: SystemSoundSlot, mediaUri: String): Result<Unit>
}

interface CuePlayer {
    suspend fun play(uri: String, playback: CuePlayback)
    fun stopPreview()
}

interface HapticsEngine {
    fun play(pattern: HapticPattern)
}

interface AudioEnvironment {
    fun currentMode(): AlertMode
}

interface PreferencesRepository {
    val preferences: Flow<HoloPreferences>
    suspend fun setSetupCompleted(completed: Boolean)
    suspend fun setToggle(toggle: HoloToggle, enabled: Boolean)
    suspend fun setFinancePackages(explicit: Set<String>, blocked: Set<String>)
    suspend fun setGigKeywords(keywords: Set<String>)
    suspend fun setIconStyle(tint: IconTint, frame: IconFrame)
    suspend fun setOverrideDone(id: String, done: Boolean)
    suspend fun setCustomCue(cue: AudioCue, uri: String, displayName: String)
    suspend fun clearCustomCue(cue: AudioCue)
}

interface PermissionRepository {
    /** Permission changes have no callbacks; callers re-query on resume / activity result. */
    fun currentState(): CalibrationState
}

interface AppRepository {
    fun observeApps(): Flow<List<AppEntry>>
    fun launch(entry: AppEntry): Boolean
    fun openAppInfo(entry: AppEntry)
    fun uninstall(entry: AppEntry)
}

interface SystemVitalsRepository {
    fun observeVitals(): Flow<SystemVitals>
}

interface ContactsRepository {
    fun observeContacts(limit: Int = 24): Flow<ContactsResult>
}
