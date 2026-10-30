package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Solo arranque explícito desde actividad visible; nunca BOOT, alarmas o START_STICKY. */
class ContinuousWakeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: ContinuousWakeEngine
    private lateinit var app: PbtApplication
    private val session = WakeSessionPolicy()
    private var foregroundStarted = false
    private var finishConversationOnDestroy = true
    private var candidates = emptyList<AssistantProfile>()
    private var activation: Job? = null
    private var deadline: Job? = null
    private var rearming: Job? = null
    private var retainedRoute: AudioRoute? = null
    private var audioRelease: Job? = null
    private var cpu: android.os.PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        app = application as PbtApplication
        engine = ContinuousWakeEngine(this, scope)
        instance = this
        alive.value = true
        scope.launch { ListeningMeasurements(this@ContinuousWakeService).observe(
            passive = { session.authorized && session.phase == ListeningPhase.LISTENING }, inputType = { inputTypeValue.value }) }
        scope.launch {
            app.conversationState.collect { voice ->
                val wasAuthorized = session.authorized
                val rearm = session.onVoice(voice.active && voice.sessionConfirmed, voice.connecting, voice.failed, voice.active && voice.paused)
                update(session.phase)
                if (voice.active && !voice.connecting && !voice.failed && !voice.paused) deadline?.cancel()
                if (wasAuthorized && !session.authorized) suspendListening("El proveedor ha pausado o rechazado la conversación. Revisa su estado y pulsa Activar escucha para reintentar.")
                if (rearm) { rearming?.cancel(); rearming = scope.launch { listen() } }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PAUSE) { pause(); return START_NOT_STICKY }
        if (intent?.action != START) { finishConversationOnDestroy = true; session.stop(); stopSelf(); return START_NOT_STICKY }
        if (session.authorized) return START_NOT_STICKY
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Escucha local", NotificationManager.IMPORTANCE_LOW))
            if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) throw SecurityException()
            val deviceType = if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            startForeground(41, notification(ListeningPhase.LOADING), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or deviceType)
            foregroundStarted = true
            candidates = ProfileStore(this@ContinuousWakeService).load().filter { it.language.startsWith("es") && it.activationPhrases.any { p -> p.enabled } }
            check(candidates.isNotEmpty() && WakeModel(this).installed())
            session.authorize()
            detail.value = ""
            app.voiceTransitions.record(session.phase, TransitionReason.START, app.microphone.owner(), inputTypeValue.value)
            getSharedPreferences("escucha", MODE_PRIVATE).edit().putBoolean("proceso-activo", true).apply()
            scope.launch { listen() }
        } catch (problem: SecurityException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); update(ListeningPhase.SUSPENDED); stopSelf() }
        catch (problem: IllegalStateException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); update(ListeningPhase.SUSPENDED); stopSelf() }
        catch (problem: IllegalArgumentException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); update(ListeningPhase.SUSPENDED); stopSelf() }
        catch (problem: org.json.JSONException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); update(ListeningPhase.SUSPENDED); stopSelf() }
        return START_NOT_STICKY
    }
    private suspend fun listen() {
        // «Termina» publica el cierre inmediatamente; el AudioRecord anterior
        // todavía puede estar liberándose. No competir con él al volver a espera.
        try {
            awaitAudioRelease(audioRelease)
            app.hosted.awaitReleased()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { suspendListening("No se ha liberado el micrófono anterior. Pulsa Activar escucha para reintentar."); return }
        val current = session.beginListening() ?: return
        update(ListeningPhase.LOADING)
        armDeadline(20000, "El detector no ha recibido audio a tiempo. Comprueba el micrófono seleccionado y reintenta.")
        try {
            if (cpu == null) {
                // VAD/Vosk necesitan CPU con pantalla bloqueada, solo durante autorización.
                @android.annotation.SuppressLint("WakelockTimeout")
                val wakeLock = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "PbtMetaGPT:sesion-autorizada")
                wakeLock.acquire(); cpu = wakeLock
            }
            if (retainedRoute == null) {
                val route = AudioRoute(this) { pause() }
                route.start(getSharedPreferences("configuracion", MODE_PRIVATE).getBoolean("audio-bluetooth", false), speaker = true, passive = true)
                retainedRoute = route
            }
        } catch (problem: Exception) {
            app.journal.record(Check.ROUTE, Fault.INITIALIZATION, problem)
            suspendListening(); return
        }
        engine.start(candidates, ready = { if (session.ready(current)) { deadline?.cancel(); update(session.phase) } }, detected = { matched, question ->
            if (session.detected(current)) {
                update(ListeningPhase.DETECTED)
                armDeadline(15000, "Se detectó la frase, pero no pudo iniciarse la conversación. Comprueba permisos, proveedor y micrófono y pulsa Activar escucha.")
                activation?.cancel()
                activation = scope.launch {
                if (!session.authorized) return@launch
                WebCommands.parse(question)?.let { app.openWeb(it); return@launch }
                when (VoiceCommands.parse(question, matched.name, ListeningSettings(this@ContinuousWakeService).load())) {
                    LocalVoiceCommand.SYSTEM_ASSISTANT -> { app.openSystemAssistant(); return@launch }
                    LocalVoiceCommand.CHATGPT -> { app.openChatGpt(); return@launch }
                    else -> Unit
                }
                MusicCommands.parse(question)?.let { action ->
                    app.controlSpotify(action)
                    if (session.localCommandFinished()) listen()
                    return@launch
                }
                if (matched.confirmationSound) try {
                    withTimeout(3000) { WakeConfirmation.play(this@ContinuousWakeService, AudioInputSettings(this@ContinuousWakeService).useBluetooth()) }
                } catch (_: TimeoutCancellationException) {
                    suspendListening("El sonido de confirmación no ha terminado. Reintenta la escucha."); return@launch
                } catch (cancel: CancellationException) { throw cancel }
                catch (problem: Exception) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); suspendListening(); return@launch }
                if (!session.authorized) return@launch
                val preferences = getSharedPreferences("configuracion", MODE_PRIVATE)
                // El backend usa token solo en memoria de la sesión actual; no se exporta ni persiste aquí.
                val connection = app.connection
                val direct = connection?.direct ?: preferences.getBoolean("directo", true)
                if (HostedSettings(this@ContinuousWakeService).provider == "groq") {
                    try {
                        if (!HostedSettings(this@ContinuousWakeService).authorized || !KeyVault(this@ContinuousWakeService, provider = "groq").exists()) suspendListening("Configura y autoriza GroqCloud antes de iniciar la conversación.")
                        else {
                            val store = ProfileStore(this@ContinuousWakeService)
                            store.save(store.load(), matched.id)
                            app.hosted.start(matched, dictate = true, initialQuestion = question)
                        }
                    } catch (problem: Exception) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); suspendListening() }
                }
                else if ((direct && !KeyVault(this@ContinuousWakeService).exists()) || (!direct && connection == null)) suspendListening("Falta la clave de OpenAI o una sesión del backend. Configúrala y vuelve a activar la escucha.")
                else {
                    try {
                        val store = ProfileStore(this@ContinuousWakeService)
                        store.save(store.load(), matched.id)
                        app.voice.configure(matched, if (direct) ({ KeyVault(this@ContinuousWakeService).read() }) else null)
                        app.voice.start(connection?.backend.orEmpty(), connection?.token.orEmpty(), preferences.getBoolean("audio-bluetooth", false))
                    } catch (problem: IllegalStateException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); suspendListening() }
                    catch (problem: IllegalArgumentException) { app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); suspendListening() }
                }
                }
            }
        }, failed = { problem -> app.journal.record(Check.WAKE, Fault.INITIALIZATION, problem); suspendListening() }, input = { inputTypeValue.value = it })
    }
    private fun armDeadline(timeout: Long, message: String) {
        deadline?.cancel()
        deadline = scope.launch {
            delay(timeout)
            if (session.authorized) {
                app.voiceTransitions.record(ListeningPhase.SUSPENDED, TransitionReason.TIMEOUT, app.microphone.owner(), inputTypeValue.value)
                suspendListening(message)
                app.hosted.suspendConversation()
                if (app.voice.state.value.connecting) app.voice.stop()
            }
        }
    }
    private fun suspendListening(message: String = "Escucha pausada. Comprueba audio, permisos y proveedor; pulsa Activar escucha para reintentar.") {
        detail.value = message
        deadline?.cancel(); deadline = null
        activation?.cancel(); activation = null
        session.suspendListening()
        update(session.phase)
        releaseListeningAudio()
    }
    private fun releaseListeningAudio() {
        if (audioRelease?.isActive == true) return
        audioRelease = scope.launch { engine.stop(); releaseSessionAudio() }
    }
    private fun releaseSessionAudio() {
        retainedRoute?.close(); retainedRoute = null
        cpu?.let { if (it.isHeld) it.release() }; cpu = null
    }
    private fun pause() {
        suspendListening()
        app.hosted.suspendConversation()
        if (app.voice.state.value.active && !app.voice.state.value.paused) app.voice.pauseOrResume()
        else if (app.voice.state.value.connecting) app.voice.stop()
    }
    private suspend fun prepareConversation() {
        session.preparing()
        withTimeout(8000) { engine.stop() } // No abrir una segunda captura si no termina la primera.
        update(session.phase)
    }
    private fun update(value: ListeningPhase) {
        if (mutable.value != value) app.voiceTransitions.record(value, when(value) {
            ListeningPhase.LISTENING -> TransitionReason.READY
            ListeningPhase.DETECTED -> TransitionReason.DETECTED
            ListeningPhase.PREPARING -> TransitionReason.HANDOFF
            ListeningPhase.RETURNING -> TransitionReason.RETURN
            ListeningPhase.OFF -> TransitionReason.STOP
            ListeningPhase.SUSPENDED -> TransitionReason.PAUSE
            else -> TransitionReason.HANDOFF
        }, app.microphone.owner(), inputTypeValue.value)
        mutable.value = value
        if (foregroundStarted) getSystemService(NotificationManager::class.java).notify(41, notification(value))
    }
    private fun notification(phase: ListeningPhase): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ContinuousWakeService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 2, Intent(this, ContinuousWakeService::class.java).setAction(PAUSE), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_voice).setContentTitle("PbtMetaGPT · escucha local")
            .setContentText(phase.label).setOngoing(true).setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(null, "Pausar", pause).build())
            .addAction(Notification.Action.Builder(null, "Detener escucha y conversación", stop).build()).build()
    }
    override fun onDestroy() {
        deadline?.cancel(); rearming?.cancel()
        activation?.cancel(); activation = null
        session.stop(); instance = null; alive.value = false
        releaseSessionAudio()
        inputTypeValue.value = 0
        getSharedPreferences("escucha", MODE_PRIVATE).edit().putBoolean("proceso-activo", false).apply()
        // El cierre de micrófono se completa antes de cancelar el scope del servicio.
        app.scope.launch { engine.stop(); scope.cancel() }
        if (finishConversationOnDestroy) {
            app.voice.stop(); app.hosted.stop(); app.connection = null
        } else {
            app.voice.suspendForBackground(); app.hosted.suspendConversation()
        }
        if (mutable.value != ListeningPhase.SUSPENDED) mutable.value = ListeningPhase.OFF
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        if (!app.activityVisible) app.glasses.disconnect()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "escucha-local"
        private const val START = "com.pbtservers.pbtmetagpt.ESCUCHAR"
        private const val STOP = "com.pbtservers.pbtmetagpt.DETENER"
        private const val PAUSE = "com.pbtservers.pbtmetagpt.PAUSAR"
        private var instance: ContinuousWakeService? = null
        private val mutable = MutableStateFlow(ListeningPhase.OFF)
        private val inputTypeValue = MutableStateFlow(0)
        private val detail = MutableStateFlow("")
        private val alive = MutableStateFlow(false)
        val isRunning = alive.asStateFlow()
        val error = detail.asStateFlow()
        val inputType = inputTypeValue.asStateFlow()
        val state = mutable.asStateFlow()
        val running get() = instance != null
        fun reportSystemStop() { mutable.value = ListeningPhase.SUSPENDED }
        fun startVisible(context: Context): android.content.ComponentName? {
            check((context.applicationContext as PbtApplication).activityVisible) { "Abre la aplicación para iniciar el micrófono" }
            return context.startForegroundService(Intent(context, ContinuousWakeService::class.java).setAction(START))
        }
        fun stop(context: Context, finishConversation: Boolean = true) {
            instance?.finishConversationOnDestroy = finishConversation
            instance?.session?.stop()
            mutable.value = ListeningPhase.OFF
            context.stopService(Intent(context, ContinuousWakeService::class.java))
        }
        suspend fun beforeRealtime() { instance?.prepareConversation() }
        fun revokeConsent() { instance?.session?.suspendListening(); instance?.activation?.cancel() }
        suspend fun releaseForExternal() {
            instance?.let { service ->
                service.activation?.cancelAndJoin(); service.activation = null
                service.session.suspendListening()
                service.update(ListeningPhase.SUSPENDED)
                service.engine.stop()
                service.releaseSessionAudio()
            }
        }
    }
}
