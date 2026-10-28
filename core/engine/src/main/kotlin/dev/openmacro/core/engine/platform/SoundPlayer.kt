package dev.openmacro.core.engine.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import dev.openmacro.core.engine.perform.ActionOutcome
import dev.openmacro.core.model.SoundChoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Plays a system or custom sound with MediaPlayer, optionally suspending until it finishes. */
internal object SoundPlayer {

    private const val MAX_WAIT_MILLIS = 5 * 60_000L

    suspend fun play(context: Context, choice: SoundChoice, customUri: String, waitToFinish: Boolean): ActionOutcome {
        val uri: Uri = when (choice) {
            SoundChoice.NOTIFICATION -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SoundChoice.RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            SoundChoice.ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            SoundChoice.CUSTOM -> {
                if (customUri.isBlank()) return ActionOutcome.Failed("No sound selected")
                Uri.parse(customUri)
            }
        }
        val usage = when (choice) {
            SoundChoice.ALARM -> AudioAttributes.USAGE_ALARM
            SoundChoice.RINGTONE -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            SoundChoice.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
            SoundChoice.CUSTOM -> AudioAttributes.USAGE_MEDIA
        }

        val finished = CompletableDeferred<Boolean>()
        // MediaPlayer delivers callbacks on the thread that created it, so create it on Main.
        val player = withContext(Dispatchers.Main) {
            try {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(usage).build())
                    setDataSource(context, uri)
                    setOnCompletionListener { finished.complete(true) }
                    setOnErrorListener { _, _, _ -> finished.complete(false); true }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                null
            }
        } ?: return ActionOutcome.Failed("Could not play that sound")

        suspend fun release() = withContext(Dispatchers.Main) { runCatching { player.release() } }

        if (!waitToFinish) {
            // Fire and forget: release the player once playback ends (or after a safety timeout).
            releaseScope.launch {
                withTimeoutOrNull(MAX_WAIT_MILLIS) { finished.await() }
                release()
            }
            return ActionOutcome.Success
        }
        val ok = try {
            withTimeoutOrNull(MAX_WAIT_MILLIS) { finished.await() }
        } finally {
            release()
        }
        return if (ok == true) ActionOutcome.Success else ActionOutcome.Failed("Sound playback failed")
    }

    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
