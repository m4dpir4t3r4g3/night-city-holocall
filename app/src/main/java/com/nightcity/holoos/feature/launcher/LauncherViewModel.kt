package com.nightcity.holoos.feature.launcher

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nightcity.holoos.data.apps.DockResolver
import com.nightcity.holoos.data.apps.IconRepository
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.ContactsResult
import com.nightcity.holoos.domain.model.DockSlot
import com.nightcity.holoos.domain.model.HoloContact
import com.nightcity.holoos.domain.model.IconFrame
import com.nightcity.holoos.domain.model.IconTint
import com.nightcity.holoos.domain.model.SystemVitals
import com.nightcity.holoos.domain.repository.AppRepository
import com.nightcity.holoos.domain.repository.ContactsRepository
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.repository.SystemVitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IconStyle(val tint: IconTint = IconTint.CYAN, val frame: IconFrame = IconFrame.CHAMFER)

data class DockItem(val slot: DockSlot, val packageName: String?)

data class LauncherUiState(
    val vitals: SystemVitals? = null,
    val contacts: ContactsResult = ContactsResult.Loaded(emptyList()),
    val apps: List<AppEntry> = emptyList(),
    val totalApps: Int = 0,
    val query: String = "",
    val drawerOpen: Boolean = false,
    val iconStyle: IconStyle = IconStyle(),
    val dock: List<DockItem> = DockSlot.entries.map { DockItem(it, null) },
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LauncherViewModel @Inject constructor(
    vitalsRepository: SystemVitalsRepository,
    contactsRepository: ContactsRepository,
    private val apps: AppRepository,
    private val icons: IconRepository,
    private val dockResolver: DockResolver,
    private val preferences: PreferencesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val drawerOpen = MutableStateFlow(false)
    private val contactsRefresh = MutableStateFlow(0)
    private val dock = MutableStateFlow(DockSlot.entries.map { DockItem(it, null) })

    private val contacts = contactsRefresh.flatMapLatest { contactsRepository.observeContacts() }
    private val iconStyle = preferences.preferences.map { IconStyle(it.iconTint, it.iconFrame) }
    private val allApps = apps.observeApps()

    private val filteredApps = combine(allApps, query) { list, q ->
        val trimmed = q.trim()
        (if (trimmed.isEmpty()) list else list.filter { it.label.contains(trimmed, ignoreCase = true) }) to list.size
    }

    val state: StateFlow<LauncherUiState> = combine(
        vitalsRepository.observeVitals().map<SystemVitals, SystemVitals?> { it },
        contacts,
        filteredApps,
        combine(query, drawerOpen, iconStyle, dock) { q, open, style, d -> UiFlags(q, open, style, d) },
    ) { vitals, contactsResult, (appList, total), flags ->
        LauncherUiState(
            vitals = vitals,
            contacts = contactsResult,
            apps = appList,
            totalApps = total,
            query = flags.query,
            drawerOpen = flags.drawerOpen,
            iconStyle = flags.style,
            dock = flags.dock,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    private data class UiFlags(val query: String, val drawerOpen: Boolean, val style: IconStyle, val dock: List<DockItem>)

    fun onResume() {
        contactsRefresh.update { it + 1 } // pick up a freshly granted contacts permission
        viewModelScope.launch(Dispatchers.IO) { dock.value = resolveDock() } // default apps may have changed
    }

    fun onHomePressed() {
        drawerOpen.value = false
        query.value = ""
    }

    fun openDrawer() { drawerOpen.value = true }

    /** @return true if the drawer was open and is now closed. */
    fun closeDrawer(): Boolean {
        if (!drawerOpen.value) return false
        drawerOpen.value = false
        query.value = ""
        return true
    }

    fun setQuery(value: String) { query.value = value }

    fun launch(entry: AppEntry): Boolean = apps.launch(entry).also { if (it) closeDrawer() }
    fun openAppInfo(entry: AppEntry) = apps.openAppInfo(entry)
    fun uninstall(entry: AppEntry) = apps.uninstall(entry)

    fun setIconStyle(style: IconStyle) = viewModelScope.launch {
        preferences.setIconStyle(style.tint, style.frame)
    }

    fun dockIntent(slot: DockSlot): Intent = dockResolver.launchIntent(slot)

    suspend fun appIcon(entry: AppEntry, tint: IconTint): Bitmap? = icons.appIcon(entry, tint)
    suspend fun packageIcon(packageName: String, tint: IconTint): Bitmap? = icons.packageIcon(packageName, tint)
    suspend fun contactPhoto(contact: HoloContact, tint: IconTint): Bitmap? =
        contact.photoUri?.let { icons.contactPhoto(it, tint) }

    private fun resolveDock() = DockSlot.entries.map { DockItem(it, dockResolver.defaultPackage(it)) }
}
