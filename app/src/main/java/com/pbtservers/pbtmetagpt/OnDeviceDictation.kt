package com.pbtservers.pbtmetagpt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale

/** Dictado completo offline de Android. Nunca crea un reconocedor de red. */
class OnDeviceDictation(private val context: Context) {
    fun available() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    suspend fun capture(language: String = "es-ES", speechEnded: () -> Unit = {}, ready: () -> Unit = {}, speechStarted: () -> Unit = {}, silenceMs: Int = 1700): String = withContext(Dispatchers.Main.immediate) {
        if (!available()) throw HostedFailure("El reconocedor offline de Android no está instalado.")
        val gate = (context.applicationContext as PbtApplication).microphone
        val lease = gate.acquire(MicrophoneOwner.ANDROID_LOCAL) ?: throw HostedFailure("El micrófono está ocupado.")
        val result = CompletableDeferred<String>()
        var recognizer: SpeechRecognizer? = null
        try {
            val active = SpeechRecognizer.createOnDeviceSpeechRecognizer(context); recognizer = active
            active.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { ready() }
                override fun onBeginningOfSpeech() { speechStarted() }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { speechEnded() }
                override fun onError(error: Int) {
                    if (!result.isCompleted) result.completeExceptionally(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NoSpeechDetected()
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> HostedFailure("Falta el modelo local del idioma elegido. Instala español de España en Android o selecciona Vosk español.")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> HostedFailure("Android ha denegado el permiso de micrófono al reconocedor local.")
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> HostedFailure("El reconocedor local de Android está ocupado. Detén la otra escucha y reintenta.")
                        SpeechRecognizer.ERROR_AUDIO -> HostedFailure("Android no ha podido capturar audio. Revisa el micrófono y la ruta Bluetooth.")
                        else -> HostedFailure("No se entendió el dictado local. Repite la pregunta o selecciona otro motor autorizado.")
                    })
                }
                override fun onResults(results: Bundle?) {
                    val phrase = DictationHypotheses.best(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty(),
                        results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES))
                    if (!result.isCompleted) {
                        if (phrase.isBlank()) result.completeExceptionally(HostedFailure("No se detectó una frase completa."))
                        else result.complete(phrase)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            active.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(language).toLanguageTag())
                if (android.os.Build.VERSION.SDK_INT >= 34) putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs.toLong())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs.toLong())
            })
            withTimeout(50_000) { result.await() }
        } finally {
            try { recognizer?.cancel() } finally { try { recognizer?.destroy() } finally { gate.release(lease) } }
        }
    }
}
