package com.aira.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aira.app.data.model.CommandSource
import java.util.Locale

class VoiceCommandManager(
    private val context: Context,
    private val callback: Callback,
) : RecognitionListener {
    interface Callback {
        fun onListeningStateChanged(isListening: Boolean)
        fun onTranscriptReady(text: String, source: CommandSource)
        fun onVoiceHint(message: String)
        fun onVoiceError(message: String)
    }

    private val restartHandler = Handler(Looper.getMainLooper())
    private val wakeWordDetector = WakeWordDetector()
    private val recognizer: SpeechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(this)
        }
    }

    private var continuousMode = false
    private var currentSessionManual = false
    private var userStopRequested = false
    private var active = false

    val isListening: Boolean
        get() = active

    fun setContinuousMode(enabled: Boolean) {
        continuousMode = enabled
        if (!enabled) {
            wakeWordDetector.clear()
        }
    }

    fun startManualListening() {
        currentSessionManual = true
        startListeningSession()
    }

    fun startContinuousListening() {
        continuousMode = true
        currentSessionManual = false
        startListeningSession()
    }

    fun stopListening() {
        userStopRequested = true
        restartHandler.removeCallbacksAndMessages(null)
        wakeWordDetector.clear()
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            if (active) {
                recognizer.stopListening()
            } else {
                recognizer.cancel()
            }
        }
        active = false
        callback.onListeningStateChanged(false)
    }

    fun destroy() {
        restartHandler.removeCallbacksAndMessages(null)
        wakeWordDetector.clear()
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            recognizer.destroy()
        }
    }

    private fun startListeningSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onVoiceError("Speech recognition is not available on this device.")
            return
        }

        userStopRequested = false
        restartHandler.removeCallbacksAndMessages(null)
        active = true
        callback.onListeningStateChanged(true)
        recognizer.cancel()
        recognizer.startListening(buildRecognizerIntent())
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

    private fun scheduleRestart(delayMillis: Long = 450L) {
        if (!continuousMode || userStopRequested) return
        restartHandler.removeCallbacksAndMessages(null)
        restartHandler.postDelayed({
            currentSessionManual = false
            startListeningSession()
        }, delayMillis)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        active = false
        callback.onListeningStateChanged(false)
    }

    override fun onError(error: Int) {
        active = false
        callback.onListeningStateChanged(false)

        if (userStopRequested) {
            userStopRequested = false
            return
        }

        if (continuousMode && error in recoverableErrors) {
            scheduleRestart()
            return
        }

        callback.onVoiceError(messageForError(error))
    }

    override fun onResults(results: Bundle?) {
        active = false
        callback.onListeningStateChanged(false)

        val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

        val outcome = wakeWordDetector.process(
            transcript = transcript,
            manualTrigger = currentSessionManual,
        )
        currentSessionManual = false

        when (outcome) {
            is WakeWordOutcome.Actionable -> {
                callback.onTranscriptReady(outcome.command, outcome.source)
                scheduleRestart(700L)
            }

            WakeWordOutcome.Armed -> {
                callback.onVoiceHint("Wake phrase heard. Speak the command now.")
                scheduleRestart(300L)
            }

            WakeWordOutcome.Ignored -> {
                if (continuousMode) {
                    callback.onVoiceHint("Waiting for \"Hey Aira\".")
                    scheduleRestart()
                }
            }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun messageForError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio could not be captured."
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognizer was interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "Speech recognition needs a working network connection."

        SpeechRecognizer.ERROR_NO_MATCH -> "Aira did not catch that phrase."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service is unavailable."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
        else -> "Voice recognition failed with error code $error."
    }

    companion object {
        private val recoverableErrors = setOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        )
    }
}
