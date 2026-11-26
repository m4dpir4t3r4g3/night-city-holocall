package com.nightcity.holoos.di

import com.nightcity.holoos.data.apps.LauncherAppsRepository
import com.nightcity.holoos.data.audio.AndroidAudioEnvironment
import com.nightcity.holoos.data.audio.AndroidHapticsEngine
import com.nightcity.holoos.data.audio.AndroidSystemSoundRepository
import com.nightcity.holoos.data.audio.AudioCueRepositoryImpl
import com.nightcity.holoos.data.audio.MediaPlayerCuePlayer
import com.nightcity.holoos.data.contacts.AndroidContactsRepository
import com.nightcity.holoos.data.permissions.AndroidPermissionRepository
import com.nightcity.holoos.data.preferences.DataStorePreferencesRepository
import com.nightcity.holoos.data.system.AndroidSystemVitalsRepository
import com.nightcity.holoos.domain.repository.AppRepository
import com.nightcity.holoos.domain.repository.AudioCueRepository
import com.nightcity.holoos.domain.repository.AudioEnvironment
import com.nightcity.holoos.domain.repository.ContactsRepository
import com.nightcity.holoos.domain.repository.CuePlayer
import com.nightcity.holoos.domain.repository.HapticsEngine
import com.nightcity.holoos.domain.repository.PermissionRepository
import com.nightcity.holoos.domain.repository.PreferencesRepository
import com.nightcity.holoos.domain.repository.SystemSoundRepository
import com.nightcity.holoos.domain.repository.SystemVitalsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindPreferences(impl: DataStorePreferencesRepository): PreferencesRepository

    @Binds @Singleton
    abstract fun bindAudioCues(impl: AudioCueRepositoryImpl): AudioCueRepository

    @Binds @Singleton
    abstract fun bindSystemSounds(impl: AndroidSystemSoundRepository): SystemSoundRepository

    @Binds @Singleton
    abstract fun bindCuePlayer(impl: MediaPlayerCuePlayer): CuePlayer

    @Binds @Singleton
    abstract fun bindHaptics(impl: AndroidHapticsEngine): HapticsEngine

    @Binds @Singleton
    abstract fun bindAudioEnvironment(impl: AndroidAudioEnvironment): AudioEnvironment

    @Binds @Singleton
    abstract fun bindPermissions(impl: AndroidPermissionRepository): PermissionRepository

    @Binds @Singleton
    abstract fun bindApps(impl: LauncherAppsRepository): AppRepository

    @Binds @Singleton
    abstract fun bindVitals(impl: AndroidSystemVitalsRepository): SystemVitalsRepository

    @Binds @Singleton
    abstract fun bindContacts(impl: AndroidContactsRepository): ContactsRepository
}
