package com.pbtservers.pbtmetagpt

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.io.File

/** Recursos independientes de Compose. Crear el proceso nunca inicia captura. */
class PbtApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var journal: ErrorJournal; private set
    val diagnostics by lazy { Diagnostics(journal = journal) }
    val microphone = MicrophoneGate()
    val voskModels = VoskModels()
    val wakeDownload by lazy { WakeModelDownload(this, scope) }
    val voiceTransitions = VoiceTransitions { android.os.SystemClock.elapsedRealtime() }
    val audioSession by lazy { CommunicationAudioSession(this) }
    val photoMemory = PhotoMemory()
    val glasses by lazy { MetaGlasses(this, scope, { mutableMessage.value = it }, diagnostics, photoMemory) }
    var activityVisible = false
    var stopVisibleWake: (() -> Unit)? = null
    private var handoff: Job? = null
    private var musicCommand: Job? = null
    var connection: WakeConnection? = null
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    val voice by lazy { RealtimeClient(this, scope, { mutableMessage.value = it }, diagnostics) }
    val hosted by lazy { HostedConversation(this, scope) }
    val conversationState by lazy { combine(voice.state, hosted.state) { paid, free ->
        if (HostedSettings(this).provider == "groq") free else paid
    } }
    fun clearMessage() { mutableMessage.value = null }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (getProcessName() == packageName) scope.launch(Dispatchers.IO) { voskModels.close() }
    }
    fun controlSpotify(action: MusicAction) {
        if (!MusicSettings(this).enabled) {
            mutableMessage.value = "Activa Controlar Spotify en Ajustes → Dispositivos → Música."
            return
        }
        handoff?.cancel()
        // Cancelar respuestas/colas antes de ceder foco; conservar el consentimiento del servicio.
        hosted.stop(); voice.stop()
        musicCommand?.cancel()
        musicCommand = scope.launch {
            try { hosted.awaitReleased() }
            catch (_: TimeoutCancellationException) { mutableMessage.value = "La liberación del audio ha agotado su plazo. Detén y reactiva la escucha."; return@launch }
            catch (_: HostedFailure) { mutableMessage.value = "El micrófono anterior sigue ocupado. Detén y reactiva la escucha."; return@launch }
            ContinuousWakeService.releaseFocusForMusic()
            val released = withTimeoutOrNull(5000) {
                while (audioSession.hasFocusOwners()) delay(20)
                true
            } == true
            mutableMessage.value = if (released) SpotifyControls(this@PbtApplication).execute(action)
                else "El asistente aún está liberando el audio. Espera y repite la orden de música."
        }
    }
    fun openChatGpt() = openExternalAssistant { OfficialChatGpt(this).open(activityVisible) }
    fun openSystemAssistant() = openExternalAssistant { SystemAssistantLauncher(this).open(activityVisible) }
    fun openWeb(action: WebAction) = openExternalAssistant { WebLauncher(this).open(action, activityVisible) }
    private fun openExternalAssistant(open: () -> String) {
        if (handoff?.isActive == true) return
        musicCommand?.cancel()
        stopVisibleWake?.invoke()
        ContinuousWakeService.revokeConsent()
        // Invalidar inmediatamente; esperar la liberación antes del Intent, fuera de la UI.
        hosted.stop(); voice.stop()
        handoff = scope.launch {
            try {
                withTimeout(10000) { ContinuousWakeService.releaseForExternal(); hosted.awaitReleased() }
            } catch (_: TimeoutCancellationException) {
                mutableMessage.value = "No se ha liberado el micrófono a tiempo. Detén la escucha y repite la apertura."; return@launch
            } catch (_: HostedFailure) {
                mutableMessage.value = "Micrófono anterior todavía ocupado. Detén la escucha y repite la apertura."; return@launch
            }
            ContinuousWakeService.stop(this@PbtApplication)
            val released = withTimeoutOrNull(5000) {
                while (microphone.owner() != null || audioSession.hasOwners()) delay(20)
                true
            } == true
            if (!released) {
                mutableMessage.value = "Micrófono o foco todavía ocupado. Detén la prueba de audio, espera y solicita abrir el asistente de nuevo."
            } else mutableMessage.value = open()
        }
    }
    fun errorReport(): String = "PbtMetaGPT ${BuildConfig.VERSION_NAME} · Android API ${android.os.Build.VERSION.SDK_INT}\n" +
        "ABI: " + android.os.Build.SUPPORTED_ABIS.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }.joinToString() + "\n" + journal.report() +
        "\nLatencias numéricas (ms; primera trama no acredita sonido físico/Bluetooth):\n" + LatencyStore(this).report() +
        "\nEscucha pasiva: CPU del proceso por ventana y PSS (no batería ni máximo):\n" + ListeningMeasurements(this).report() +
        "\nTransiciones locales sin conversaciones ni audio:\n" + voiceTransitions.report()
    override fun onCreate() {
        super.onCreate()
        if (getProcessName() != packageName) return // UID aislado de TTS.
        journal = ErrorJournal(File(noBackupFilesDir, "diagnostico"))
        val listening = getSharedPreferences("escucha", MODE_PRIVATE)
        if (listening.getBoolean("proceso-activo", false)) {
            journal.record(Check.WAKE, Fault.PROCESS_SYSTEM)
            ContinuousWakeService.reportSystemStop()
            listening.edit().putBoolean("proceso-activo", false).apply()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, problem ->
            try { journal.record(Check.CONTROL, Fault.UNCAUGHT, problem) }
            finally { previous?.uncaughtException(thread, problem) }
        }
        val preferences = getSharedPreferences("diagnostico-sistema", MODE_PRIVATE)
        getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(packageName, 0, 8)
            .filter { it.processName == packageName && it.timestamp > preferences.getLong("ultima-salida", 0) }
            .sortedBy { it.timestamp }.forEach { exit ->
                when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> journal.record(Check.CONTROL, Fault.PROCESS_NATIVE)
                    ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_LOW_MEMORY,
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> journal.record(Check.CONTROL, Fault.PROCESS_SYSTEM)
                }
                preferences.edit().putLong("ultima-salida", exit.timestamp).apply()
            }
    }
}

/** Solo memoria del proceso autorizado; no tiene serialización ni toString con credenciales. */
class WakeConnection(val backend: String, val token: String, val direct: Boolean)
