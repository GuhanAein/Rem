package app.rem.ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class VoicePhase { IDLE, LISTENING, DONE, ERROR }

/**
 * Wraps Android's SpeechRecognizer (the phone's own speech service, preferring on-device
 * recognition). Exposes Compose state: the live transcript, a 0..1 mic level and the phase.
 * Must be used from the main thread.
 */
class SpeechController(private val context: Context) : RecognitionListener {
    var phase by mutableStateOf(VoicePhase.IDLE)
        private set
    var transcript by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        error = null
        transcript = ""
        level = 0f
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        r.startListening(recognizerIntent())
        phase = VoicePhase.LISTENING
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    /** Used when the system dialog fallback returns a result. */
    fun acceptExternalResult(text: String) {
        transcript = text
        phase = VoicePhase.DONE
    }

    override fun onReadyForSpeech(params: Bundle?) { phase = VoicePhase.LISTENING }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { level = 0f }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.firstResult()?.let { if (it.isNotBlank()) transcript = it }
    }

    override fun onResults(results: Bundle?) {
        results?.firstResult()?.let { transcript = it }
        level = 0f
        phase = if (transcript.isBlank()) VoicePhase.ERROR.also { error = "I didn't catch that. Tap the mic and try again." } else VoicePhase.DONE
    }

    override fun onError(code: Int) {
        level = 0f
        if (transcript.isNotBlank() && (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            phase = VoicePhase.DONE
            return
        }
        error = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the mic and try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Rem needs microphone access to listen."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs a connection on this phone. Try again, or type instead."
            else -> "Speech recognition isn't available right now."
        }
        phase = VoicePhase.ERROR
    }

    private fun Bundle.firstResult(): String? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    companion object {
        fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I remind you?")
    }
}
