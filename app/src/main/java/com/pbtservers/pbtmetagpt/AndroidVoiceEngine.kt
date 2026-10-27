package com.pbtservers.pbtmetagpt

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AndroidVoice(val engine: String, val name: String, val language: String)

class AndroidVoiceEngine(private val context: Context) {
    private suspend fun open(packageName: String): TextToSpeech = withContext(Dispatchers.Main.immediate) {
        val available = context.packageManager.queryIntentServices(android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        require(available.any { it.serviceInfo.packageName == packageName }) { "El motor TTS seleccionado no está instalado." }
        suspendCancellableCoroutine { continuation ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context, { result ->
                val instance = tts
                if (!continuation.isActive) instance?.shutdown()
                else if (result == TextToSpeech.SUCCESS && instance != null) continuation.resume(instance)
                else { instance?.shutdown(); continuation.resumeWithException(IllegalStateException("El motor Android no pudo inicializarse.")) }
            }, packageName)
            continuation.invokeOnCancellation { tts?.shutdown() }
        }
    }
    suspend fun voices(): List<AndroidVoice> = withTimeout(15000) {
        val packages = context.packageManager.queryIntentServices(android.content.Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).map { it.serviceInfo.packageName }.distinct()
        val result = mutableListOf<AndroidVoice>()
        for (name in packages) {
            val tts = try { open(name) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { continue }
            try { tts.voices.orEmpty().filter { !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty() }
                .forEach { result += AndroidVoice(name, it.name, it.locale.toLanguageTag()) } }
            finally { tts.shutdown() }
        }
        result
    }
    suspend fun synthesize(profile: AssistantProfile, text: String): PcmAudio = withTimeout(60000) {
        require(text.length in 1..4000 && !SecretPatterns.contains(text)) { "Texto no permitido para síntesis." }
        val tts = open(profile.ttsPackage)
        val file = File.createTempFile("voz-", ".wav", context.cacheDir)
        try {
            val voice = tts.voices.orEmpty().firstOrNull { it.name == profile.ttsVoice && !it.isNetworkConnectionRequired && "notInstalled" !in it.features.orEmpty() }
            require(voice != null) { "La voz local seleccionada no está instalada o requiere red." }
            require(tts.setVoice(voice) == TextToSpeech.SUCCESS && tts.setSpeechRate(profile.speed.toFloat()) == TextToSpeech.SUCCESS && tts.setPitch(profile.pitch.toFloat()) == TextToSpeech.SUCCESS) { "El motor no aceptó la voz, velocidad o tono." }
            val completed = CompletableDeferred<Unit>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { completed.complete(Unit) }
                @Deprecated("API Android") override fun onError(utteranceId: String?) { completed.completeExceptionally(IllegalStateException("El motor Android rechazó la síntesis.")) }
                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
            })
            require(tts.synthesizeToFile(text, Bundle(), file, java.util.UUID.randomUUID().toString()) == TextToSpeech.SUCCESS) { "No se pudo solicitar síntesis al motor Android." }
            completed.await()
            withContext(Dispatchers.IO) { require(file.length() <= 16777216); VoiceSynthesisContract.wav(file.readBytes()) }
        } finally { tts.stop(); tts.shutdown(); file.delete() }
    }
}
