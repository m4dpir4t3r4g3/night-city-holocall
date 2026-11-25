package com.nightcity.holoos.domain.model

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val userSerial: Long,
    val isWorkProfile: Boolean,
) {
    val key: String get() = "$packageName/$activityName#$userSerial"
}

data class HoloContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phoneNumber: String?,
    val photoUri: String?,
    val starred: Boolean,
)

sealed interface ContactsResult {
    data object PermissionRequired : ContactsResult
    data class Loaded(val contacts: List<HoloContact>) : ContactsResult
}

data class BatteryVitals(
    val percent: Int,
    val voltageMv: Int,
    val temperatureC: Float,
    val charging: Boolean,
)

data class MemoryVitals(val totalBytes: Long, val availableBytes: Long) {
    val load: Float get() = if (totalBytes <= 0) 0f else 1f - availableBytes.toFloat() / totalBytes
}

data class SystemVitals(val battery: BatteryVitals, val memory: MemoryVitals)

enum class IconTint { CYAN, YELLOW, DUOTONE }

enum class IconFrame { CHAMFER, HEX }

enum class DockSlot { PHONE, MESSAGES, CAMERA, BROWSER }
