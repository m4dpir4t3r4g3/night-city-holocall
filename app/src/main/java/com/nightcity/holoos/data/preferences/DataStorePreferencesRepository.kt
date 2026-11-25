package com.nightcity.holoos.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nightcity.holoos.domain.model.AudioCue
import com.nightcity.holoos.domain.model.CustomCue
import com.nightcity.holoos.domain.model.HoloPreferences
import com.nightcity.holoos.domain.model.HoloToggle
import com.nightcity.holoos.domain.model.IconFrame
import com.nightcity.holoos.domain.model.IconTint
import com.nightcity.holoos.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.holoDataStore: DataStore<Preferences> by preferencesDataStore(name = "holo_prefs")

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PreferencesRepository {

    private val store = context.holoDataStore

    override val preferences: Flow<HoloPreferences> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toModel() }
        .distinctUntilChanged()

    override suspend fun setSetupCompleted(completed: Boolean) {
        store.edit { it[SETUP_COMPLETED] = completed }
    }

    override suspend fun setToggle(toggle: HoloToggle, enabled: Boolean) {
        store.edit { it[toggleKey(toggle)] = enabled }
    }

    override suspend fun setFinancePackages(explicit: Set<String>, blocked: Set<String>) {
        store.edit {
            it[FINANCE_PACKAGES] = explicit
            it[FINANCE_BLOCKED] = blocked
        }
    }

    override suspend fun setGigKeywords(keywords: Set<String>) {
        store.edit { it[GIG_KEYWORDS] = keywords }
    }

    override suspend fun setIconStyle(tint: IconTint, frame: IconFrame) {
        store.edit {
            it[ICON_TINT] = tint.name
            it[ICON_FRAME] = frame.name
        }
    }

    override suspend fun setOverrideDone(id: String, done: Boolean) {
        store.edit {
            val current = it[OVERRIDES_DONE].orEmpty()
            it[OVERRIDES_DONE] = if (done) current + id else current - id
        }
    }

    override suspend fun setCustomCue(cue: AudioCue, uri: String, displayName: String) {
        store.edit {
            it[cueUriKey(cue)] = uri
            it[cueNameKey(cue)] = displayName
        }
    }

    override suspend fun clearCustomCue(cue: AudioCue) {
        store.edit {
            it.remove(cueUriKey(cue))
            it.remove(cueNameKey(cue))
        }
    }

    private fun Preferences.toModel(): HoloPreferences = HoloPreferences(
        setupCompleted = this[SETUP_COMPLETED] ?: false,
        toggles = HoloToggle.entries.associateWith { this[toggleKey(it)] ?: it.default },
        financePackages = this[FINANCE_PACKAGES].orEmpty(),
        blockedFinancePackages = this[FINANCE_BLOCKED].orEmpty(),
        gigKeywords = this[GIG_KEYWORDS].orEmpty(),
        iconTint = this[ICON_TINT]?.let { runCatching { IconTint.valueOf(it) }.getOrNull() } ?: IconTint.CYAN,
        iconFrame = this[ICON_FRAME]?.let { runCatching { IconFrame.valueOf(it) }.getOrNull() } ?: IconFrame.CHAMFER,
        completedOverrides = this[OVERRIDES_DONE].orEmpty(),
        customCues = AudioCue.entries.mapNotNull { cue ->
            this[cueUriKey(cue)]?.let { uri -> cue to CustomCue(uri, this[cueNameKey(cue)] ?: cue.rawResourceName) }
        }.toMap(),
    )

    private companion object {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val FINANCE_PACKAGES = stringSetPreferencesKey("finance_packages")
        val FINANCE_BLOCKED = stringSetPreferencesKey("finance_blocked")
        val GIG_KEYWORDS = stringSetPreferencesKey("gig_keywords")
        val ICON_TINT = stringPreferencesKey("icon_tint")
        val ICON_FRAME = stringPreferencesKey("icon_frame")
        val OVERRIDES_DONE = stringSetPreferencesKey("overrides_done")

        fun toggleKey(toggle: HoloToggle) = booleanPreferencesKey("toggle_${toggle.name.lowercase()}")
        fun cueUriKey(cue: AudioCue) = stringPreferencesKey("cue_uri_${cue.name.lowercase()}")
        fun cueNameKey(cue: AudioCue) = stringPreferencesKey("cue_name_${cue.name.lowercase()}")
    }
}
