package com.pbtservers.pbtmetagpt

import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

data class VoiceState(
    val connecting: Boolean = false,
    val active: Boolean = false,
    val paused: Boolean = false,
    val responding: Boolean = false,
    val sendingImage: Boolean = false,
    val status: String = "Conversación detenida",
    val route: String = "El micrófono está apagado",
    val transcript: String = "",
    val phoneFallbackAvailable: Boolean = false,
    val imageAnalysis: Boolean = false,
    val failed: Boolean = false,
    val sessionConfirmed: Boolean = false,
)

/** Una conexión WebRTC conserva el contexto. No se persisten audio, imágenes ni credenciales. */
class RealtimeClient(context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit, private val diagnostics: Diagnostics = Diagnostics()) {
    private val appContext = context.applicationContext
    private val mutable = MutableStateFlow(VoiceState())
    val state = mutable.asStateFlow()
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private var factory: PeerConnectionFactory? = null
    private var module: JavaAudioDeviceModule? = null
    private var peer: PeerConnection? = null
    private var channel: DataChannel? = null
    private var source: AudioSource? = null
    private var microphone: AudioTrack? = null
    private var microphoneLease: MicrophoneGate.Lease? = null
    private var job: Job? = null
    private var timeout: Job? = null
    private var sessionLimit: Job? = null
    private var generation = 0
    private val callbacks = NativeCallbackQueue(scope) { generation }
    private var diagnosticOnly = false
    private var sampleOnly = false
    private var imageOnly = false
    private var pendingImage: Bitmap? = null
    private var requestedBluetooth = false
    private var profile: AssistantProfile? = null
    private var sessionProfile: AssistantProfile? = null
    private var directKey: (() -> String)? = null
    private var configured = false
    private var generating = false
    private var outputPlaying = false
    private var currentResponse = ""
    private val suppressed = mutableSetOf<String>()
    private val texts = mutableMapOf<String, String>()
    private fun usesAlternative() = sessionProfile?.let(VoiceSynthesisContract::alternative) == true
    private val alternative = AlternativeSpeech(appContext, scope,
        { message -> if (state.value.active) {
            if (message.startsWith("Reproduciendo")) diagnostics.record(Check.TTS_ENGINE, CheckStatus.OK, "El motor entregó PCM y comenzó su reproducción; falta confirmación auditiva y física.", "PCM_READY")
            else diagnostics.record(Check.TTS_ENGINE, CheckStatus.RUNNING, "El motor está preparando la síntesis alternativa.", "TTS_RUNNING")
            mutable.update { it.copy(status = message, responding = true) }
        } },
        { message -> diagnostics.record(Check.TTS_ENGINE, CheckStatus.ERROR, message, "ALTERNATIVE_TTS_ERROR"); error(message) })
    fun configure(profile: AssistantProfile?, directKey: (() -> String)?) {
        check(!state.value.active && !state.value.connecting) { "Finaliza la sesión antes de cambiar su configuración." }
        this.profile = profile
        this.directKey = directKey
    }
    private val route = AudioRoute(appContext) { message -> scope.launch {
        val offerPhone = requestedBluetooth
        stopInternal(false, message, offerPhone)
        error(message)
    } }

    fun start(baseUrl: String, accessToken: String, bluetooth: Boolean, testOnly: Boolean = false, sample: Boolean = false, image: Bitmap? = null) {
        android.util.Log.i("PbtStart", "REALTIME_START_REQUEST")
        if (state.value.active || state.value.connecting) return
        if (!testOnly && profile == null) { error("Crea y selecciona un asistente antes de conversar."); return }
        if (sample && profile?.let(VoiceSynthesisContract::alternative) == true) { previewAlternative(requireNotNull(profile), bluetooth); return }
        if (!testOnly && !sample && image == null && appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            error("Falta permiso de micrófono. No se ha iniciado ninguna conversación."); return
        }
        stop()
        val current = generation
        diagnosticOnly = testOnly
        sampleOnly = sample
        imageOnly = image != null
        pendingImage = image
        requestedBluetooth = bluetooth
        configured = false
        sessionProfile = profile
        for (check in listOf(Check.SDP, Check.ICE, Check.DATA_CHANNEL, Check.REALTIME)) diagnostics.record(check, CheckStatus.PENDING, "Pendiente en este intento.")
        mutable.value = VoiceState(connecting = true, status = if (imageOnly) "Preparando el análisis de imagen · micrófono apagado" else "Autorizando la conversación…", imageAnalysis = imageOnly)
        job = scope.launch {
            var stage = Check.BACKEND
            try {
                withTimeout(60_000) {
                    ContinuousWakeService.beforeRealtime()
                    if (!testOnly) sessionProfile = sessionProfile?.let { AndroidVoiceEngine(appContext).resolveRapid(it) }
                    if (!testOnly && !sample && !imageOnly) {
                        val gate = (appContext as? PbtApplication)?.microphone
                        if (gate != null) {
                            microphoneLease = gate.acquire(MicrophoneOwner.REALTIME)
                            if (microphoneLease == null) throw DiagnosticFailure("MICROPHONE_BUSY", "El micrófono todavía está en uso. Detén la escucha o la prueba local y reintenta.")
                        }
                    }
                    if (usesAlternative()) {
                        stage = Check.TTS_ENGINE
                        alternative.prepare(requireNotNull(sessionProfile))
                        diagnostics.record(Check.TTS_ENGINE, CheckStatus.WARNING, "Configuración del sintetizador validada antes de solicitar credenciales. La inferencia y escucha siguen pendientes.", "TTS_CONFIGURED")
                    }
                    stage = Check.BACKEND
                    android.util.Log.i("PbtStart", "CREDENTIAL_REQUEST")
                    (appContext as? PbtApplication)?.journal?.record(Check.CREDENTIAL, Fault.CREDENTIAL_REQUEST)
                    val secret = credential(baseUrl, accessToken)
                    ensureActive()
                    stage = Check.ROUTE
                    android.util.Log.i("PbtStart", "AUDIO_ROUTE_REQUEST")
                    val routeLabel = if (testOnly) "Prueba sin micrófono" else route.start(bluetooth, speaker = !bluetooth)
                    if (!testOnly) diagnostics.record(Check.ROUTE, CheckStatus.WARNING, "Ruta de comunicación solicitada a Android. La selección no acredita micrófono o altavoz de las gafas.")
                    mutable.update { it.copy(route = routeLabel, status = "Conectando con OpenAI…") }
                    android.util.Log.i("PbtStart", "WEBRTC_INITIALIZE")
                    (appContext as? PbtApplication)?.journal?.record(Check.REALTIME, Fault.WEBRTC_INITIALIZE)
                    WebRtcBootstrap.ensure(appContext)
                    android.util.Log.i("PbtStart", "AUDIO_MODULE_CREATE")
                    (appContext as? PbtApplication)?.journal?.record(Check.MICROPHONE, Fault.AUDIO_MODULE_CREATE)
                    val audioModule = JavaAudioDeviceModule.builder(appContext)
                        .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                            private fun failed() = dispatch(current) { diagnostics.record(Check.MICROPHONE, CheckStatus.ERROR, "WebRTC notificó un fallo real de captura.", "NATIVE_RECORD_ERROR"); fail("No se pudo utilizar el micrófono. Comprueba permisos, ruta de audio y otras aplicaciones.") }
                            override fun onWebRtcAudioRecordInitError(message: String) = failed()
                            override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode, message: String) = failed()
                            override fun onWebRtcAudioRecordError(message: String) = failed()
                        })
                        .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                            private fun failed() = dispatch(current) { diagnostics.record(Check.PLAYBACK, CheckStatus.ERROR, "WebRTC notificó un fallo real de reproducción.", "NATIVE_PLAYBACK_ERROR"); fail("No se pudo reproducir el audio. Comprueba el dispositivo de salida.") }
                            override fun onWebRtcAudioTrackInitError(message: String) = failed()
                            override fun onWebRtcAudioTrackStartError(code: JavaAudioDeviceModule.AudioTrackStartErrorCode, message: String) = failed()
                            override fun onWebRtcAudioTrackError(message: String) = failed()
                        })
                        .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                            override fun onWebRtcAudioRecordStart() = dispatch(current) { diagnostics.record(Check.MICROPHONE, CheckStatus.OK, "El capturador nativo ha iniciado. Comprueba que la voz procede del dispositivo esperado.", "NATIVE_RECORD_STARTED") }
                            override fun onWebRtcAudioRecordStop() = dispatch(current) { diagnostics.record(Check.MICROPHONE, CheckStatus.WARNING, "El capturador nativo se ha detenido.", "NATIVE_RECORD_STOPPED") }
                        })
                        .setAudioTrackStateCallback(object : JavaAudioDeviceModule.AudioTrackStateCallback {
                            override fun onWebRtcAudioTrackStart() = dispatch(current) { diagnostics.record(Check.PLAYBACK, CheckStatus.WARNING, "El reproductor nativo ha iniciado; falta confirmar que se oye el audio.", "NATIVE_PLAYBACK_STARTED") }
                            override fun onWebRtcAudioTrackStop() = dispatch(current) { diagnostics.record(Check.PLAYBACK, CheckStatus.WARNING, "El reproductor nativo se ha detenido.", "NATIVE_PLAYBACK_STOPPED") }
                        }).createAudioDeviceModule()
                    module = audioModule
                    android.util.Log.i("PbtStart", "PEER_FACTORY_CREATE")
                    (appContext as? PbtApplication)?.journal?.record(Check.REALTIME, Fault.PEER_FACTORY_CREATE)
                    val createdFactory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()
                    factory = createdFactory
                    android.util.Log.i("PbtStart", "PEER_CONNECTION_CREATE")
                    val connection = createdFactory.createPeerConnection(
                        PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN },
                        observer(current),
                    ) ?: throw IllegalStateException("No se pudo preparar WebRTC.")
                    peer = connection
                    android.util.Log.i("PbtStart", "PEER_CONNECTION_READY")
                    connection.setAudioRecording(false)
                    if (usesAlternative()) connection.setAudioPlayout(false)
                    if (testOnly || sample || imageOnly) {
                        connection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
                    } else {
                        android.util.Log.i("PbtStart", "MICROPHONE_TRACK_CREATE")
                        val audioSource = createdFactory.createAudioSource(MediaConstraints())
                        source = audioSource
                        microphone = createdFactory.createAudioTrack("pbt-microphone", audioSource).apply { setEnabled(false) }
                        connection.addTrack(requireNotNull(microphone), listOf("pbt-audio"))
                    }
                    channel = connection.createDataChannel("oai-events", DataChannel.Init()).also { dc ->
                        dc.registerObserver(object : DataChannel.Observer {
                            override fun onBufferedAmountChange(previousAmount: Long) = Unit
                            override fun onStateChange() {
                                dispatch(current) {
                                    val value = dc.state()
                                    diagnostics.record(Check.DATA_CHANNEL, if (value == DataChannel.State.OPEN) CheckStatus.OK else CheckStatus.WARNING, "Estado observado del canal de eventos.", value.name)
                                    if (value == DataChannel.State.CLOSED) fail("Se ha cerrado el canal de conversación.")
                                }
                            }
                            override fun onMessage(buffer: DataChannel.Buffer) {
                                if (buffer.binary) return
                                val bytes = ByteArray(buffer.data.remaining())
                                buffer.data.get(bytes)
                                val text = bytes.toString(Charsets.UTF_8)
                                dispatch(current) { receive(text) }
                            }
                        })
                    }
                    android.util.Log.i("PbtStart", "SDP_OFFER_CREATE")
                    val offer = connection.offer()
                    stage = Check.SDP
                    diagnostics.record(Check.SDP, CheckStatus.RUNNING, "Oferta de audio creada; esperando respuesta de OpenAI.")
                    connection.setDescription(offer, local = true)
                    android.util.Log.i("PbtStart", "SDP_LOCAL_SET")
                    val request = Request.Builder().url("https://api.openai.com/v1/realtime/calls")
                        .header("Authorization", "Bearer $secret")
                        .post(offer.description.toRequestBody("application/sdp".toMediaType())).build()
                    val answer = http.newCall(request).awaitReply().let { response ->
                        if (!response.isSuccessful) throw DiagnosticFailure("SDP_HTTP_${response.code}", "OpenAI rechazó la negociación SDP (HTTP ${response.code}). Revisa cuota, modelo y caducidad de la credencial.")
                        response.body.takeIf { it.isNotBlank() } ?: throw IllegalStateException("OpenAI no devolvió una respuesta de conexión.")
                    }
                    ensureActive()
                    connection.setDescription(SessionDescription(SessionDescription.Type.ANSWER, answer), local = false)
                    android.util.Log.i("PbtStart", "SDP_REMOTE_SET")
                    diagnostics.record(Check.SDP, CheckStatus.OK, "OpenAI ha aceptado el SDP. Falta confirmar transporte y sesión.", "SDP_ACCEPTED")
                }
                if (!state.value.active) timeout = scope.launch {
                    delay(15_000)
                    if (generation == current && !state.value.active) fail("OpenAI no confirmó la sesión a tiempo.")
                }
            } catch (_: TimeoutCancellationException) { if (generation == current) fail("Se agotó el tiempo para iniciar la conversación.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (problem: LinkageError) {
                (appContext as? PbtApplication)?.journal?.record(Check.REALTIME, Fault.INITIALIZATION, problem)
                diagnostics.record(Check.REALTIME, CheckStatus.ERROR, "No se pudo cargar una API o biblioteca nativa de WebRTC.", Diagnostics.sdkCode(problem))
                android.util.Log.e("PbtStart", "WEBRTC_LINKAGE_ERROR")
                if (generation == current) fail("La biblioteca de audio no es compatible con este dispositivo o APK. Consulta Diagnóstico y prueba una compilación compatible.")
            } catch (problem: Exception) {
                if (generation == current) recordFailure(problem, stage)
                if (generation == current) {
                    fail(if (stage == Check.TTS_ENGINE) "El sintetizador elegido no está preparado. Revisa la voz instalada, el paquete local o la autorización del proveedor. No se ha iniciado la captura."
                        else if (problem is IllegalArgumentException || problem is IllegalStateException)
                        problem.message ?: "No se pudo iniciar la conversación." else "No se pudo conectar. Comprueba la red y la dirección HTTPS del backend.")
                    if (stage == Check.ROUTE && bluetooth) mutable.update { it.copy(phoneFallbackAvailable = true) }
                }
            }
        }
    }

    private fun observer(current: Int) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            dispatch(current) { diagnostics.record(Check.ICE, when (state) {
                PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> CheckStatus.OK
                PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.DISCONNECTED -> CheckStatus.ERROR
                else -> CheckStatus.RUNNING
            }, "Estado de transporte informado por WebRTC.", state.name) }
            if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.DISCONNECTED)
                dispatch(current) { fail("Se perdió la conexión de audio. Inicia una sesión nueva cuando vuelva la red.") }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dc: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            dispatch(current) { (receiver.track() as? AudioTrack)?.setEnabled(!usesAlternative()) }
        }
    }

    private fun dispatch(current: Int, action: () -> Unit) {
        callbacks.dispatch(current) {
            try { action() }
            catch (problem: IllegalStateException) { recordFailure(problem, Check.REALTIME); fail("El audio dejó de estar disponible. Puedes reintentar.") }
            catch (problem: SecurityException) { recordFailure(problem, Check.MICROPHONE); fail("Android ha retirado un permiso de audio.") }
            catch (problem: LinkageError) {
                (appContext as? PbtApplication)?.journal?.record(Check.REALTIME, Fault.INITIALIZATION, problem)
                fail("La biblioteca nativa no pudo completar la operación. Consulta Registros de errores.")
            }
        }
    }

    private fun receive(text: String) {
        try {
            val event = JSONObject(text)
            when (event.optString("type")) {
                "session.created" -> {
                    diagnostics.record(Check.REALTIME, CheckStatus.OK, "OpenAI ha enviado session.created por el canal real. Esto no acredita audio ni gafas.", "SESSION_CREATED")
                    val selected = sessionProfile
                    if (selected != null) {
                        val settings = RealtimeSettings.session(selected).apply { remove("model") }
                        if (!send(JSONObject().put("type", "session.update").put("session", settings).toString())) fail("No se pudo aplicar el perfil. No se ha activado el micrófono.")
                    } else ready()
                }
                "session.updated" -> {
                    val selected = sessionProfile
                    if (selected != null && !RealtimeSettings.confirms(event.optJSONObject("session"), selected))
                        fail("OpenAI no confirmó el modelo, la voz y las instrucciones solicitadas. El micrófono permanece apagado.")
                    else ready()
                }
                "response.created" -> {
                    currentResponse = event.optJSONObject("response")?.optString("id").orEmpty()
                    generating = true
                    mutable.update { it.copy(responding = true, status = if (it.paused) "En pausa · micrófono apagado" else "ChatGPT está respondiendo…") }
                    if (state.value.paused && !sampleOnly) interrupt() else if (!usesAlternative()) peer?.setAudioPlayout(true)
                }
                "response.done" -> {
                    generating = false
                    val response = event.optJSONObject("response")
                    val id = response?.optString("id").orEmpty()
                    val cancelled = suppressed.remove(id)
                    mutable.update { it.copy(responding = outputPlaying, status = if (it.paused && !sampleOnly) "En pausa · micrófono apagado" else if (outputPlaying) "Reproduciendo respuesta…" else if (imageOnly) "Análisis recibido · micrófono apagado" else if (sampleOnly) "Terminando muestra de voz…" else "Escuchando · micrófono encendido") }
                    if (event.optJSONObject("response")?.optString("status") == "failed") {
                        diagnostics.record(Check.PROVIDER, CheckStatus.ERROR, "OpenAI no pudo completar la respuesta.", "RESPONSE_FAILED")
                        error("OpenAI no pudo completar la respuesta. Comprueba cuota y compatibilidad del modelo.")
                    }
                    if (usesAlternative() && response?.optString("status") == "completed" && !cancelled && !state.value.paused) {
                        val text = responseText(response).ifBlank { texts[id].orEmpty() }
                        if (text.isNotBlank()) speakAlternative(text)
                        else error("OpenAI no devolvió texto para el sintetizador alternativo.")
                    }
                    texts.remove(id)
                }
                "output_audio_buffer.started" -> {
                    if (usesAlternative()) { peer?.setAudioPlayout(false); return }
                    outputPlaying = true
                    mutable.update { it.copy(responding = true, status = "Reproduciendo respuesta…") }
                }
                "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> {
                    if (usesAlternative()) return
                    outputPlaying = false
                    mutable.update { it.copy(responding = generating, status = if (imageOnly) "Análisis recibido · micrófono apagado" else if (sampleOnly) "Muestra terminada" else if (it.paused) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
                    if (sampleOnly) { timeout?.cancel(); timeout = scope.launch { delay(500); stop() } }
                }
                "input_audio_buffer.speech_started" -> if (!state.value.paused) mutable.update { it.copy(status = "Escuchando tu petición…") }
                "input_audio_buffer.speech_stopped" -> if (!state.value.paused) mutable.update { it.copy(status = "Procesando tu petición…") }
                "response.output_audio_transcript.delta" -> mutable.update { it.copy(transcript = (it.transcript + event.optString("delta")).takeLast(4000)) }
                "response.output_audio_transcript.done" -> mutable.update { it.copy(transcript = (it.transcript + "\n\n").takeLast(4000)) }
                "response.output_text.delta" -> mutable.update { it.copy(transcript = (it.transcript + event.optString("delta")).takeLast(4000)) }
                "response.output_text.done" -> {
                    texts[event.optString("response_id")] = event.optString("text").take(16000)
                    mutable.update { it.copy(transcript = (it.transcript + "\n\n").takeLast(4000)) }
                }
                "error" -> {
                    diagnostics.record(Check.PROVIDER, CheckStatus.ERROR, "OpenAI rechazó una operación. El cuerpo original no se guarda.", Diagnostics.safeCode(event.optJSONObject("error")?.optString("code").orEmpty()))
                    if (!configured) fail("OpenAI rechazó la configuración. El micrófono sigue apagado.")
                    else { mutable.update { it.copy(responding = false) }; error("OpenAI rechazó una operación de la sesión. Consulta Diagnóstico.") }
                }
            }
        } catch (problem: org.json.JSONException) { recordFailure(problem, Check.REALTIME); fail("Se recibió un evento de conversación no válido.") }
        catch (problem: IllegalStateException) { recordFailure(problem, Check.REALTIME); fail("La sesión de audio ya no está disponible. Vuelve a intentarlo.") }
        catch (problem: SecurityException) { recordFailure(problem, Check.MICROPHONE); fail("Android ha retirado el permiso de audio. Revisa los permisos antes de reintentar.") }
    }

    private fun responseText(response: JSONObject): String = buildString {
        val output = response.optJSONArray("output") ?: return@buildString
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) { val part = content.optJSONObject(j) ?: continue; if (part.optString("type") == "output_text") append(part.optString("text")) }
        }
    }

    private fun speakAlternative(text: String) {
        val selected = sessionProfile ?: return
        val current = generation
        peer?.setAudioRecording(false); microphone?.setEnabled(false); peer?.setAudioPlayout(false)
        mutable.update { it.copy(responding = true, status = "Generando voz alternativa · micrófono apagado") }
        alternative.speak(selected, text) {
            if (current != generation) return@speak
            if (sampleOnly) stop()
            else if (state.value.active) {
                if (!state.value.paused && !imageOnly) { peer?.setAudioRecording(true); microphone?.setEnabled(true) }
                mutable.update { it.copy(responding = false, status = if (imageOnly) "Análisis recibido · micrófono apagado" else if (it.paused) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
            }
        }
    }

    fun previewAlternative(selected: AssistantProfile, bluetooth: Boolean) {
        if (state.value.active || state.value.connecting) return
        stop(); sessionProfile = selected.validated(); sampleOnly = true
        try {
            val label = route.start(bluetooth, speaker = !bluetooth)
            mutable.value = VoiceState(active = true, paused = true, status = "Muestra alternativa · micrófono apagado", route = label)
            val sample = if (selected.ttsEngine == "sherpa_vits") VoicePackageStore(java.io.File(appContext.filesDir, "voces")).describe(selected.ttsVoice).sampleText
                else if (selected.ttsEngine == "piper_es") "Hola. En Zaragoza hay cinco plazas. ¿Podéis acercaros vosotros?"
                else "Hola. Esta es una muestra de mi voz, lista para ayudarte."
            speakAlternative(sample)
        } catch (_: Exception) { fail("No se pudo preparar la muestra. Revisa la voz, los requisitos del modelo y la ruta de audio.") }
    }

    private fun ready() {
        if (configured) return
        configured = true
        mutable.update { it.copy(sessionConfirmed = true) }
        timeout?.cancel()
        diagnostics.record(Check.REALTIME, CheckStatus.OK, "Sesión y configuración confirmadas por OpenAI; audio pendiente de validación humana.", "SESSION_CONFIGURED")
        if (diagnosticOnly) { stop(); return }
        if (imageOnly) {
            mutable.update { it.copy(connecting = false, active = true, status = "Analizando imagen · micrófono apagado") }
            pendingImage?.let { bitmap -> pendingImage = null; askImage(bitmap) {} }
            sessionLimit = scope.launch { delay(120_000); stop(); error("Sesión de análisis finalizada por tiempo límite. Puedes volver a analizar la foto.") }
        } else if (sampleOnly) {
            mutable.update { it.copy(connecting = false, active = true, paused = true, status = "Muestra de voz · micrófono apagado") }
            send(JSONObject().put("type", "response.create").put("response", JSONObject()
                .put("instructions", "Di únicamente: Hola. Esta es una muestra de mi voz, lista para ayudarte.")).toString())
            timeout = scope.launch { delay(30000); stop() }
        } else {
                    android.util.Log.i("PbtStart", "MICROPHONE_CAPTURE_START")
                    peer?.setAudioRecording(true)
                    microphone?.setEnabled(true)
                    mutable.update { it.copy(connecting = false, active = true, status = "Escuchando · micrófono encendido", route = it.route + "\n" + route.actualRoute()) }
            sessionProfile?.let { selected ->
                sessionLimit = scope.launch { delay(selected.sessionMinutes * 60_000L); stop(); error("La sesión ha terminado por su límite de duración configurado.") }
                if (selected.welcomeMessage.isNotBlank()) send(JSONObject().put("type", "response.create").put("response", JSONObject()
                    .put("instructions", "Saluda al usuario con este mensaje: ${selected.welcomeMessage}")).toString())
            }
        }
    }

    private fun send(json: String): Boolean = channel?.let {
        it.state() == DataChannel.State.OPEN && it.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false))
    } ?: false

    fun pauseOrResume() {
        if (!state.value.active || sampleOnly || imageOnly) return
        val pause = !state.value.paused
        mutable.update { it.copy(paused = pause) }
        microphone?.setEnabled(!pause)
        peer?.setAudioRecording(!pause)
        if (pause) interrupt()
        mutable.update { it.copy(paused = pause, status = if (pause) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
    }

    fun interrupt() {
        if (!state.value.active) return
        peer?.setAudioPlayout(false)
        alternative.stop()
        if (currentResponse.isNotBlank()) suppressed.add(currentResponse)
        if (generating) send(RealtimeProtocol.event("response.cancel"))
        if (!usesAlternative()) send(RealtimeProtocol.event("output_audio_buffer.clear"))
        send(RealtimeProtocol.event("input_audio_buffer.clear"))
        if (usesAlternative()) {
            if (!state.value.paused && !imageOnly) { peer?.setAudioRecording(true); microphone?.setEnabled(true) }
            mutable.update { it.copy(responding = false, status = if (imageOnly) "Análisis interrumpido · micrófono apagado" else if (it.paused) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
        }
    }

    fun askImage(bitmap: Bitmap, onSent: () -> Unit) {
        if (!state.value.active || state.value.paused || state.value.responding || state.value.sendingImage) {
            error("No se puede enviar ahora. Espera la conexión o la respuesta, o reanuda la sesión. La foto se conserva.")
            return
        }
        val current = generation
        mutable.update { it.copy(sendingImage = true) }
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { ImageEncoding.jpeg(bitmap) }
                if (current != generation || !state.value.active) return@launch
                require(send(RealtimeProtocol.visualQuestion(jpeg))) { "No se pudo enviar la imagen; comprueba la conexión." }
                require(send(RealtimeProtocol.event("response.create"))) { "No se pudo solicitar la respuesta hablada." }
                mutable.update { it.copy(responding = true, status = "Imagen enviada; esperando respuesta de OpenAI…") }
                onSent()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { if (current == generation) error("No se pudo enviar la imagen. Vuelve a intentarlo o descártala.")
            } finally { if (current == generation) mutable.update { it.copy(sendingImage = false) } }
        }
    }

    private suspend fun credential(baseUrl: String, accessToken: String): String {
        directKey?.let { readKey ->
            val selected = sessionProfile ?: profile
            diagnostics.record(Check.BACKEND, CheckStatus.PENDING, "Modo directo: no se utiliza un backend propio.", "DIRECT_MODE")
            diagnostics.record(Check.CREDENTIAL, CheckStatus.RUNNING, "Solicitando una credencial temporal directamente a OpenAI, sin backend propio.")
            val request = DirectCredentials.request(readKey(), selected)
            val response = http.newCall(request).awaitReply()
            return DirectCredentials.decode(response, System.currentTimeMillis() / 1000).also {
                diagnostics.record(Check.CREDENTIAL, CheckStatus.OK, "OpenAI emitió una credencial temporal. Esto no verifica voz ni hardware.", "DIRECT_EPHEMERAL_ISSUED")
            }
        }
        val endpoint = RealtimeProtocol.endpoint(baseUrl)
        require(Regex("[A-Za-z0-9_-]{32,256}").matches(accessToken) && !Regex("^sk[-_].*").matches(accessToken)) { "Introduce el token personal del backend, nunca una clave de OpenAI." }
        diagnostics.record(Check.BACKEND, CheckStatus.RUNNING, "Conectando por HTTPS sin redirecciones.")
        diagnostics.record(Check.CREDENTIAL, CheckStatus.RUNNING, "Solicitando una credencial temporal al backend.")
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $accessToken").post(ByteArray(0).toRequestBody()).build()
        return http.newCall(request).awaitReply().let { response ->
            diagnostics.record(Check.BACKEND, CheckStatus.OK, "Se recibió respuesta HTTPS del backend.", "HTTP_${response.code}")
            val body = response.body
            if (!response.isSuccessful) throw RealtimeProtocol.backendFailure(body, response.code)
            RealtimeProtocol.secret(body, System.currentTimeMillis() / 1000).also {
                diagnostics.record(Check.CREDENTIAL, CheckStatus.OK, "El backend emitió una credencial efímera vigente. Aún no se ha confirmado Realtime.", "EPHEMERAL_ISSUED")
            }
        }
    }
    fun probeCredentials(baseUrl: String, accessToken: String) {
        if (state.value.active || state.value.connecting) return
        stop()
        val current = generation
        mutable.value = VoiceState(connecting = true, status = "Comprobando credenciales sin activar audio…")
        job = scope.launch {
            try { credential(baseUrl, accessToken); if (current == generation) error("Comprobación correcta: credencial temporal recibida. Todavía falta comprobar WebRTC y el audio.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (problem: Exception) { if (current == generation) { recordFailure(problem); error(if (problem is DiagnosticFailure) problem.message.orEmpty() else "Falló la comprobación. Consulta Diagnóstico; no se muestran cuerpos de error ni credenciales.") } }
            finally { if (current == generation) stop() }
        }
    }
    private fun recordFailure(problem: Exception, fallback: Check = Check.BACKEND) {
        val code = when (problem) {
            is DiagnosticFailure -> problem.code
            is javax.net.ssl.SSLException -> "TLS_ERROR"
            is java.net.UnknownHostException -> "DNS_ERROR"
            is java.net.SocketTimeoutException -> "NETWORK_TIMEOUT"
            is java.security.GeneralSecurityException -> "KEYSTORE_ERROR"
            else -> "CONNECTION_OR_CONFIGURATION"
        }
        val target = if (code.startsWith("SDP")) Check.SDP else if (code.startsWith("OPENAI") || code.startsWith("BACKEND_")) Check.CREDENTIAL else fallback
        (appContext as? PbtApplication)?.journal?.record(target, FaultClassifier.connection(code), problem)
        diagnostics.record(target, CheckStatus.ERROR, if (problem is DiagnosticFailure) problem.message.orEmpty() else "La etapa no pudo completarse. Revisa conexión, certificado y configuración.", code)
    }
    private fun fail(message: String) {
        diagnostics.record(Check.CONTROL, CheckStatus.ERROR, message)
        stopInternal(false, message)
        error(message)
    }

    fun stop() = stopInternal(true)
    private fun stopInternal(recordControl: Boolean, failure: String? = null, offerPhone: Boolean = false) {
        if (recordControl && (state.value.active || state.value.connecting)) diagnostics.record(Check.CONTROL, CheckStatus.WARNING, "Cierre de sesión y liberación de audio solicitados; no se reanuda automáticamente.", "STOP_REQUESTED")
        generation++
        pendingImage = null
        sessionProfile = null
        imageOnly = false; sampleOnly = false; diagnosticOnly = false
        generating = false; outputPlaying = false
        currentResponse = ""; suppressed.clear(); texts.clear(); releasePart { alternative.stop() }
        job?.cancel(); job = null
        timeout?.cancel(); timeout = null
        sessionLimit?.cancel(); sessionLimit = null
        // Desvincular primero: una segunda parada no vuelve a liberar los mismos objetos nativos.
        val oldMic = microphone; microphone = null
        val oldPeer = peer; peer = null
        val oldChannel = channel; channel = null
        val oldSource = source; source = null
        val oldFactory = factory; factory = null
        val oldModule = module; module = null
        releasePart { oldMic?.setEnabled(false) }
        releasePart { oldPeer?.setAudioRecording(false) }
        releasePart { oldChannel?.unregisterObserver() }
        releasePart { oldChannel?.close() }
        releasePart { oldChannel?.dispose() }
        releasePart { oldPeer?.close() }
        releasePart { oldPeer?.dispose() }
        releasePart { oldMic?.dispose() }
        releasePart { oldSource?.dispose() }
        releasePart { oldFactory?.dispose() }
        releasePart { oldModule?.release() }
        releasePart { route.close() }
        (appContext as? PbtApplication)?.microphone?.release(microphoneLease); microphoneLease = null
        // Publicar cierre y error juntos: nunca un estado intermedio que parezca fin normal y rearme el servicio.
        mutable.value = if (failure == null) VoiceState() else VoiceState(failed = true,
            status = "Error recuperable · $failure", phoneFallbackAvailable = offerPhone)
    }
    private fun releasePart(action: () -> Unit) {
        try { action() }
        catch (problem: IllegalStateException) { releaseFailure(problem) }
        catch (problem: SecurityException) { releaseFailure(problem) }
        catch (problem: LinkageError) { releaseFailure(problem) }
    }
    private fun releaseFailure(problem: Throwable) {
        (appContext as? PbtApplication)?.journal?.record(Check.CONTROL, Fault.RELEASE, problem)
        diagnostics.record(Check.CONTROL, CheckStatus.ERROR, "Una operación de cierre de audio ha fallado; se continúa liberando el resto. Consulta Registros de errores.", "RELEASE_FAILED")
    }
}

private open class SdpCallback : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(message: String) = Unit
    override fun onSetFailure(message: String) = Unit
}
private suspend fun PeerConnection.offer(): SessionDescription = suspendCancellableCoroutine { continuation ->
    createOffer(object : SdpCallback() {
        override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
        override fun onCreateFailure(message: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo crear la oferta de audio.")) }
    }, MediaConstraints())
}
private suspend fun PeerConnection.setDescription(sdp: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { continuation ->
    val callback = object : SdpCallback() {
        override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
        override fun onSetFailure(message: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo negociar el audio.")) }
    }
    if (local) setLocalDescription(callback, sdp) else setRemoteDescription(callback, sdp)
}
