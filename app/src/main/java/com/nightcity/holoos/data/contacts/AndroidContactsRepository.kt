package com.nightcity.holoos.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.domain.model.ContactsResult
import com.nightcity.holoos.domain.model.HoloContact
import com.nightcity.holoos.domain.repository.ContactsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Holocall roster: starred contacts first, then alphabetical; one row per contact. */
@Singleton
class AndroidContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ContactsRepository {

    override fun observeContacts(limit: Int): Flow<ContactsResult> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        trySend(Unit)
        val registered = hasPermission() && runCatching {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }.conflate().map {
        if (hasPermission()) ContactsResult.Loaded(query(limit)) else ContactsResult.PermissionRequired
    }.flowOn(io)

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun query(limit: Int): List<HoloContact> {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.STARRED,
        )
        val result = LinkedHashMap<Long, HoloContact>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${Phone.STARRED} DESC, ${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    val id = c.getLong(0)
                    if (id in result) continue
                    result[id] = HoloContact(
                        id = id,
                        lookupKey = c.getString(1).orEmpty(),
                        name = c.getString(2) ?: c.getString(3).orEmpty(),
                        phoneNumber = c.getString(3),
                        photoUri = c.getString(4),
                        starred = c.getInt(5) == 1,
                    )
                }
            }
        }
        return result.values.toList()
    }
}
