package com.nightcity.holoos.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Process-wide scope for work that must outlive a screen (power cues, notification cues). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
