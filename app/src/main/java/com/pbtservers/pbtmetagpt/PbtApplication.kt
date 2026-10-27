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
    var connection: WakeConnection? = null
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    val voice by lazy { RealtimeClient(this, scope, { mutableMessage.value = it }, diagnostics) }
    val hosted by lazy { HostedConversation(this, scope) }
    val conversationState by lazy { combine(voice.state, hosted.state) { paid, free ->
        if (HostedSettings(this).provider == "groq") free else paid
    } }
    fun clearMessage() { mutableMessage.value = null }
    fun errorReport(): String = "PbtMetaGPT ${BuildConfig.VERSION_NAME} · Android API ${android.os.Build.VERSION.SDK_INT}\n" +
        "ABI: " + android.os.Build.SUPPORTED_ABIS.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }.joinToString() + "\n" + journal.report()
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
