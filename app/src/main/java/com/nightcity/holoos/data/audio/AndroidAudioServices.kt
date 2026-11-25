package com.nightcity.holoos.data.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import com.nightcity.holoos.di.IoDispatcher
import com.nightcity.holoos.di.MainDispatcher
import com.nightcity.holoos.domain.model.AlertMode
import com.nightcity.holoos.domain.model.CuePlayback
import com.nightcity.holoos.domain.model.HapticPattern
import com.nightcity.holoos.domain.model.SystemSoundSlot
import com.nightcity.holoos.domain.repository.AudioEnvironment
import com.nightcity.holoos.domain.repository.CuePlayer
import com.nightcity.holoos.domain.repository.HapticsEngine
import com.nightcity.holoos.domain.repository.SystemSoundRepository
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HoloAudio"

/** Applies defaults via RingtoneManager. Requires the WRITE_SETTINGS special access. */
@Singleton
class AndroidSystemSoundRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SystemSoundRepository {

    override fun canWriteSystemSettings(): Boolean = Settings.System.canWrite(context)

    override suspend fun setDefaultSound(slot: SystemSoundSlot, mediaUri: String): Result<Unit> = withContext(io) {
        runCatching {
            check(canWriteSystemSettings()) { "WRITE_SETTINGS not granted" }
            val type = when (slot) {
                SystemSoundSlot.RINGTONE -> RingtoneManager.TYPE_RINGTONE
                SystemSoundSlot.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
                SystemSoundSlot.ALARM -> RingtoneManager.TYPE_ALARM
            }
            RingtoneManager.setActualDefaultRingtoneUri(context, type, mediaUri.toUri())
        }
    }
}

/** Fire-and-forget MediaPlayer playback. One preview at a time; event cues may overlap. */
@Singleton
class MediaPlayerCuePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @MainDispatcher private val main: CoroutineDispatcher,
) : CuePlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = mutableSetOf<MediaPlayer>()
    private var preview: MediaPlayer? = null

    override suspend fun play(uri: String, playback: CuePlayback) = withContext(main) {
        val player = MediaPlayer()
        val usage = if (playback == CuePlayback.PREVIEW) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_EVENT
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        try {
            player.setDataSource(context, uri.toUri())
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open cue $uri", e)
            player.release()
            return@withContext
        }
        if (playback == CuePlayback.PREVIEW) {
            stopPreviewInternal()
            preview = player
        }
        active += player
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { release(it) }
        player.setOnErrorListener { mp, what, extra ->
            Log.w(TAG, "MediaPlayer error $what/$extra for $uri")
            release(mp)
            true
        }
        player.prepareAsync()
    }

    override fun stopPreview() {
        // All MediaPlayer bookkeeping happens on the main thread.
        mainHandler.post { stopPreviewInternal() }
    }

    private fun stopPreviewInternal() {
        preview?.let { release(it) }
        preview = null
    }

    private fun release(player: MediaPlayer) {
        if (preview === player) preview = null
        active -= player
        runCatching { player.reset() }
        player.release()
    }
}

/** Sharp, staccato waveforms; falls back to on/off timing on devices without amplitude control. */
@Singleton
class AndroidHapticsEngine @Inject constructor(
    @ApplicationContext context: Context,
) : HapticsEngine {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    override fun play(pattern: HapticPattern) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(pattern.timings, -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
    }
}

@Singleton
class AndroidAudioEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioEnvironment {
    override fun currentMode(): AlertMode {
        val nm = context.getSystemService(NotificationManager::class.java)
        val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            return AlertMode.SILENT // Do Not Disturb
        }
        return when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> AlertMode.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> AlertMode.SILENT
            else -> AlertMode.NORMAL
        }
    }
}
