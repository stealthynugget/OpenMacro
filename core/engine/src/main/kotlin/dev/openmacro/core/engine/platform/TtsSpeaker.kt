package dev.openmacro.core.engine.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.openmacro.core.engine.perform.ActionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Lazily-initialised text-to-speech, optionally blocking until the utterance finishes. */
internal class TtsSpeaker(private val app: Context) {
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val counter = AtomicInteger(0)

    private suspend fun engine(): TextToSpeech? {
        engine?.let { return it }
        val signal = CompletableDeferred<Boolean>()
        ready = signal
        val tts = TextToSpeech(app) { status -> signal.complete(status == TextToSpeech.SUCCESS) }
        engine = tts
        val ok = withTimeoutOrNull(5_000) { signal.await() } ?: false
        return if (ok) tts else null
    }

    fun stop() {
        runCatching { engine?.stop() }
    }

    suspend fun speak(text: String, waitToFinish: Boolean): ActionOutcome {
        if (text.isBlank()) return ActionOutcome.Success
        val tts = engine() ?: return ActionOutcome.Failed("Text-to-speech unavailable")
        val id = "om-${counter.incrementAndGet()}"
        if (!waitToFinish) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
            return ActionOutcome.Success
        }
        val done = suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(false)
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        }
        return if (done) ActionOutcome.Success else ActionOutcome.Failed("Speech failed")
    }
}
