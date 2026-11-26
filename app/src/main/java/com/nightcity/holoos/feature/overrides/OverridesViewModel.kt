package com.nightcity.holoos.feature.overrides

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nightcity.holoos.R
import com.nightcity.holoos.core.ui.UiMessage
import com.nightcity.holoos.data.overrides.KeyboardBackdropExporter
import com.nightcity.holoos.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A sandboxed setting the app cannot change itself, with the deep link that opens it. */
enum class ManualOverride(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val action: String,
    val needsPackageUri: Boolean = false,
) {
    KEYBOARD(R.string.override_keyboard_title, R.string.override_keyboard_body, android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS),
    DISPLAY(R.string.override_display_title, R.string.override_display_body, android.provider.Settings.ACTION_DISPLAY_SETTINGS),
    SOUND(R.string.override_sound_title, R.string.override_sound_body, android.provider.Settings.ACTION_SOUND_SETTINGS),
    BATTERY(R.string.override_battery_title, R.string.override_battery_body, android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    HOME(R.string.override_home_title, R.string.override_home_body, android.provider.Settings.ACTION_HOME_SETTINGS),
    LISTENER(R.string.override_listener_title, R.string.override_listener_body, android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    LOCK_WALLPAPER(R.string.override_lock_wallpaper_title, R.string.override_lock_wallpaper_body, android.content.Intent.ACTION_SET_WALLPAPER),
}

data class OverridesUiState(val completed: Set<String> = emptySet(), val exporting: Boolean = false)

@HiltViewModel
class OverridesViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val backdropExporter: KeyboardBackdropExporter,
) : ViewModel() {

    private val _events = Channel<UiMessage>(Channel.BUFFERED)
    val events: Flow<UiMessage> = _events.receiveAsFlow()

    val state: StateFlow<OverridesUiState> = preferences.preferences
        .map { OverridesUiState(completed = it.completedOverrides) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverridesUiState())

    fun setDone(item: ManualOverride, done: Boolean) = viewModelScope.launch {
        preferences.setOverrideDone(item.name, done)
    }

    fun exportBackdrop() = viewModelScope.launch {
        backdropExporter.export()
            .onSuccess { _events.send(UiMessage(R.string.overrides_backdrop_saved)) }
            .onFailure { _events.send(UiMessage(R.string.overrides_backdrop_failed, listOf(it.message ?: "?"))) }
    }
}
