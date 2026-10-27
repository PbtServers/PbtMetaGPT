package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Exclusivamente motor on-device. Sin fallback remoto, persistencia ni reconocimiento parcial. */
class LocalWakeListener(private val context: Context, private val scope: CoroutineScope, private val activated: (AssistantProfile) -> Unit) {
    private val mutable = MutableStateFlow("Escucha local desactivada")
    val status = mutable.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var loop: Job? = null
    private var generation = 0
    private var retries = 0
    private var deadline = 0L
    private var profile: AssistantProfile? = null
    private var candidates = emptyList<AssistantProfile>()
    private val gate = ActivationGate()
    private var gateEpoch = 0
    private var testing = false
    var armed = false
        private set

    fun available() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    fun start(selected: AssistantProfile) = start(listOf(selected))
    fun test(selected: AssistantProfile, phrase: ActivationPhrase) {
        start(selected.copy(activationPhrases = listOf(phrase.copy(enabled = true, primary = true))))
        testing = true
    }
    fun start(profiles: List<AssistantProfile>) {
        stop()
        val selected = profiles.firstOrNull() ?: return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { mutable.value = "Falta permiso de micrófono"; return }
        if (!available()) { mutable.value = "No hay reconocedor local instalado. Usa el botón de conversación."; return }
        candidates = profiles.filter { it.language == selected.language && it.activationPhrases.any { phrase -> phrase.enabled } }
        if (candidates.isEmpty()) { mutable.value = "No hay frases activas para este idioma."; return }
        profile = selected.validated(); gateEpoch = gate.arm(candidates); armed = true; retries = 0
        // Ventanas limitadas: SpeechRecognizer no es un detector always-on ni un servicio en segundo plano.
        deadline = android.os.SystemClock.elapsedRealtime() + 120000
        listen()
    }
    private fun listen() {
        if (!armed) return
        if (android.os.SystemClock.elapsedRealtime() >= deadline) { stop(); mutable.value = "Espera finalizada tras dos minutos. Activa otra ventana si la necesitas."; return }
        val current = ++generation
        val selected = profile ?: return
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (generation == current) mutable.value = "Escucha local activa · ${candidates.size} asistentes · idioma ${selected.language} · ruta elegida por Android" }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onResults(results: Bundle?) {
                        if (generation != current || !armed) return
                        if (android.os.SystemClock.elapsedRealtime() >= deadline) { stop(); mutable.value = "Ventana de escucha finalizada. No se inicia conversación."; return }
                        val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        val match = gate.accept(best, gateEpoch)
                        if (match != null) {
                            val test = testing
                            stop()
                            mutable.value = if (test) "Prueba local: frase detectada. No se ha conectado con OpenAI." else "Frase reconocida · preparando conversación"
                            if (!test) activated(match)
                        } else { retries = 0; rearm(current) }
                    }
                    override fun onError(error: Int) {
                        if (generation != current || !armed) return
                        if (error in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && ++retries <= 8) rearm(current)
                        else {
                            stop()
                            mutable.value = when (error) {
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Modelo local del idioma sin descargar. Instálalo en el reconocedor del sistema."
                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "El motor local no admite este idioma. Usa el control manual."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Android rechazó el permiso de micrófono."
                                else -> "Reconocimiento local detenido (código Android $error). Usa el control manual o vuelve a intentarlo."
                            }
                        }
                    }
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, selected.language)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1).apply {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            var characters = 0
                            val hints = candidates.flatMap { p -> p.activationPhrases.filter { it.enabled }.map { it.rendered(p.name) } }.distinct()
                                .takeWhile { characters += it.length; characters <= 32768 }
                            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(hints))
                        }
                    })
            }
            mutable.value = "Preparando escucha local · micrófono autorizado"
            loop?.cancel(); loop = scope.launch { delay(20000); if (current == generation && armed) { stop(); mutable.value = "El reconocedor no respondió a tiempo. Escucha detenida." } }
        } catch (_: Exception) { stop(); mutable.value = "No se pudo iniciar el motor local. No se ha usado reconocimiento remoto." }
    }
    private fun rearm(current: Int) {
        mutable.value = "Espera local · reiniciando reconocedor (puede haber huecos de escucha)"
        loop?.cancel(); loop = scope.launch { delay(700); if (generation == current && armed) listen() }
    }
    fun stop() {
        generation++; armed = false; loop?.cancel(); loop = null
        gate.stop(); candidates = emptyList()
        testing = false
        val previous = recognizer; recognizer = null
        runCatching { previous?.cancel() }; runCatching { previous?.destroy() }
        mutable.value = "Escucha local desactivada · micrófono liberado"
    }
}
