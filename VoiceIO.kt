package com.aicaller.app.ai

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Wraps on-device SpeechRecognizer for listening to the caller.
 *
 * IMPORTANT LIMITATION: SpeechRecognizer normally listens to the phone's mic input,
 * which during an active call is routed through the telecom audio path, not a plain
 * app mic stream. Reliable capture of the *other party's* voice during a call
 * typically requires either (a) the device OEM allowing VOICE_CALL audio source
 * access (restricted on many devices/Android versions), or (b) routing call audio
 * through your own InCallService audio endpoint. Test thoroughly on target devices;
 * behavior varies significantly by manufacturer.
 */
class SpeechListener(private val context: Context) {

    fun listenOnce(): kotlinx.coroutines.flow.Flow<String> = callbackFlow {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) trySend(text)
                close()
            }
            override fun onError(error: Int) { close() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer.setRecognitionListener(listener)
        val intent = Intent_recognizer()
        recognizer.startListening(intent)

        awaitClose { recognizer.destroy() }
    }

    private fun Intent_recognizer() = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
}

/** Wraps Android TextToSpeech so we can `await` an utterance finishing before listening again. */
class VoiceSpeaker(context: Context) {
    private var ready = false
    private val tts = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }

    suspend fun speak(text: String) = suspendCoroutine<Unit> { cont ->
        val id = "utt_${System.currentTimeMillis()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) cont.resume(Unit)
            }
            @Deprecated("deprecated in API")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) cont.resume(Unit)
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun shutdown() = tts.shutdown()
}
