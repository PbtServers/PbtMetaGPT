package com.pbtservers.pbtmetagpt

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
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
    private var job: Job? = null
    private var timeout: Job? = null
    private var generation = 0
    private var diagnosticOnly = false
    private val route = AudioRoute(appContext) { message -> scope.launch { stop(); error(message) } }

    fun start(baseUrl: String, accessToken: String, bluetooth: Boolean, testOnly: Boolean = false) {
        if (state.value.active || state.value.connecting) return
        stop()
        val current = generation
        diagnosticOnly = testOnly
        for (check in listOf(Check.SDP, Check.ICE, Check.DATA_CHANNEL, Check.REALTIME)) diagnostics.record(check, CheckStatus.PENDING, "Pendiente en este intento.")
        mutable.value = VoiceState(connecting = true, status = "Autorizando la conversación…")
        job = scope.launch {
            var stage = Check.BACKEND
            try {
                withTimeout(35_000) {
                    val secret = credential(baseUrl, accessToken)
                    ensureActive()
                    stage = Check.ROUTE
                    val routeLabel = if (testOnly) "Prueba sin micrófono" else route.start(bluetooth)
                    if (!testOnly) diagnostics.record(Check.ROUTE, CheckStatus.WARNING, "Ruta de comunicación solicitada a Android. La selección no acredita micrófono o altavoz de las gafas.")
                    mutable.update { it.copy(route = routeLabel, status = "Conectando con OpenAI…") }
                    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
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
                    val createdFactory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()
                    factory = createdFactory
                    val connection = createdFactory.createPeerConnection(
                        PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN },
                        observer(current),
                    ) ?: throw IllegalStateException("No se pudo preparar WebRTC.")
                    peer = connection
                    connection.setAudioRecording(false)
                    if (testOnly) {
                        connection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
                    } else {
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
                    val offer = connection.offer()
                    stage = Check.SDP
                    diagnostics.record(Check.SDP, CheckStatus.RUNNING, "Oferta de audio creada; esperando respuesta de OpenAI.")
                    connection.setDescription(offer, local = true)
                    val request = Request.Builder().url("https://api.openai.com/v1/realtime/calls")
                        .header("Authorization", "Bearer $secret")
                        .post(offer.description.toRequestBody("application/sdp".toMediaType())).build()
                    val answer = http.newCall(request).await().use { response ->
                        if (!response.isSuccessful) throw DiagnosticFailure("SDP_HTTP_${response.code}", "OpenAI rechazó la negociación SDP (HTTP ${response.code}). Revisa cuota, modelo y caducidad de la credencial.")
                        response.body?.string() ?: throw IllegalStateException("OpenAI no devolvió una respuesta de conexión.")
                    }
                    ensureActive()
                    connection.setDescription(SessionDescription(SessionDescription.Type.ANSWER, answer), local = false)
                    diagnostics.record(Check.SDP, CheckStatus.OK, "OpenAI ha aceptado el SDP. Falta confirmar transporte y sesión.", "SDP_ACCEPTED")
                }
                if (!state.value.active) timeout = scope.launch {
                    delay(15_000)
                    if (generation == current && !state.value.active) fail("OpenAI no confirmó la sesión a tiempo.")
                }
            } catch (_: TimeoutCancellationException) { if (generation == current) fail("Se agotó el tiempo para iniciar la conversación.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (problem: Exception) {
                if (generation == current) recordFailure(problem, stage)
                if (generation == current) fail(if (problem is IllegalArgumentException || problem is IllegalStateException)
                    problem.message ?: "No se pudo iniciar la conversación." else "No se pudo conectar. Comprueba la red y la dirección HTTPS del backend.")
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
            dispatch(current) { (receiver.track() as? AudioTrack)?.setEnabled(true) }
        }
    }

    private fun dispatch(current: Int, action: () -> Unit) {
        scope.launch { if (current == generation) action() }
    }

    private fun receive(text: String) {
        try {
            val event = JSONObject(text)
            when (event.optString("type")) {
                "session.created" -> {
                    timeout?.cancel()
                    diagnostics.record(Check.REALTIME, CheckStatus.OK, "OpenAI ha enviado session.created por el canal real. Esto no acredita audio ni gafas.", "SESSION_CREATED")
                    if (diagnosticOnly) { stop(); return }
                    peer?.setAudioRecording(true)
                    microphone?.setEnabled(true)
                    mutable.update { it.copy(connecting = false, active = true, status = "Escuchando · micrófono encendido", route = it.route + "\n" + route.actualRoute()) }
                }
                "response.created" -> {
                    mutable.update { it.copy(responding = true, status = if (it.paused) "En pausa · micrófono apagado" else "ChatGPT está respondiendo…") }
                    if (state.value.paused) interrupt() else peer?.setAudioPlayout(true)
                }
                "response.done" -> {
                    mutable.update { it.copy(responding = false, status = if (it.paused) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
                    if (event.optJSONObject("response")?.optString("status") == "failed") {
                        val code = event.optJSONObject("response")?.optJSONObject("status_details")?.optJSONObject("error")?.optString("code").orEmpty()
                        diagnostics.record(Check.PROVIDER, CheckStatus.ERROR, "OpenAI ha comunicado que la respuesta falló. No se guarda el cuerpo del error.", Diagnostics.safeCode(code))
                        error("OpenAI no pudo completar la respuesta. Comprueba cuota y compatibilidad del modelo.")
                    }
                }
                "response.output_audio_transcript.delta" -> mutable.update { it.copy(transcript = (it.transcript + event.optString("delta")).takeLast(4000)) }
                "response.output_audio_transcript.done" -> mutable.update { it.copy(transcript = (it.transcript + "\n\n").takeLast(4000)) }
                "error" -> {
                    diagnostics.record(Check.PROVIDER, CheckStatus.ERROR, "OpenAI rechazó una operación. El cuerpo original no se guarda.", Diagnostics.safeCode(event.optJSONObject("error")?.optString("code").orEmpty()))
                    mutable.update { it.copy(responding = false) }; error("OpenAI rechazó una operación de la sesión. Consulta Diagnóstico.")
                }
            }
        } catch (_: Exception) { error("Se recibió un evento de conversación no válido.") }
    }

    private fun send(json: String): Boolean = channel?.let {
        it.state() == DataChannel.State.OPEN && it.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false))
    } ?: false

    fun pauseOrResume() {
        if (!state.value.active) return
        val pause = !state.value.paused
        microphone?.setEnabled(!pause)
        peer?.setAudioRecording(!pause)
        if (pause) interrupt()
        mutable.update { it.copy(paused = pause, status = if (pause) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
    }

    fun interrupt() {
        if (!state.value.active) return
        peer?.setAudioPlayout(false)
        if (state.value.responding) send(RealtimeProtocol.event("response.cancel"))
        send(RealtimeProtocol.event("output_audio_buffer.clear"))
        send(RealtimeProtocol.event("input_audio_buffer.clear"))
    }

    fun askImage(bitmap: Bitmap, onSent: () -> Unit) {
        if (!state.value.active || state.value.paused || state.value.responding || state.value.sendingImage) return
        val current = generation
        mutable.update { it.copy(sendingImage = true) }
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { encodeImage(bitmap) }
                if (current != generation || !state.value.active) return@launch
                require(send(RealtimeProtocol.visualQuestion(jpeg))) { "No se pudo enviar la imagen; comprueba la conexión." }
                require(send(RealtimeProtocol.event("response.create"))) { "No se pudo solicitar la respuesta hablada." }
                onSent()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { if (current == generation) error("No se pudo enviar la imagen. Vuelve a intentarlo o descártala.")
            } finally { if (current == generation) mutable.update { it.copy(sendingImage = false) } }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        var edge = 512
        repeat(4) {
            val ratio = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()), maxOf(1, (bitmap.height * ratio).toInt()), true)
            val bytes = ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 65, out))
                out.toByteArray()
            }
            if (scaled !== bitmap) scaled.recycle()
            // Dejar margen por debajo de 64 KiB para el mensaje SCTP completo.
            if (bytes.size <= 42_000) return Base64.encodeToString(bytes, Base64.NO_WRAP)
            edge /= 2
        }
        throw IllegalStateException("Imagen demasiado grande")
    }

    private suspend fun credential(baseUrl: String, accessToken: String): String {
        val endpoint = RealtimeProtocol.endpoint(baseUrl)
        require(Regex("[A-Za-z0-9_-]{32,256}").matches(accessToken) && !Regex("^sk[-_].*").matches(accessToken)) { "Introduce el token personal del backend, nunca una clave de OpenAI." }
        diagnostics.record(Check.BACKEND, CheckStatus.RUNNING, "Conectando por HTTPS sin redirecciones.")
        diagnostics.record(Check.CREDENTIAL, CheckStatus.RUNNING, "Solicitando una credencial temporal al backend.")
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $accessToken").post(ByteArray(0).toRequestBody()).build()
        return http.newCall(request).await().use { response ->
            diagnostics.record(Check.BACKEND, CheckStatus.OK, "Se recibió respuesta HTTPS del backend.", "HTTP_${response.code}")
            val body = response.body?.string().orEmpty()
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
        mutable.value = VoiceState(connecting = true, status = "Comprobando backend sin activar audio…")
        job = scope.launch {
            try { credential(baseUrl, accessToken) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (problem: Exception) { if (current == generation) { recordFailure(problem); error("Falló la comprobación. Consulta el diagnóstico del backend.") } }
            finally { if (current == generation) stop() }
        }
    }
    private fun recordFailure(problem: Exception, fallback: Check = Check.BACKEND) {
        val code = when (problem) {
            is DiagnosticFailure -> problem.code
            is javax.net.ssl.SSLException -> "TLS_ERROR"
            is java.net.UnknownHostException -> "DNS_ERROR"
            is java.net.SocketTimeoutException -> "NETWORK_TIMEOUT"
            else -> "CONNECTION_OR_CONFIGURATION"
        }
        val target = if (code.startsWith("SDP")) Check.SDP else if (code.startsWith("OPENAI") || code.startsWith("BACKEND_")) Check.CREDENTIAL else fallback
        diagnostics.record(target, CheckStatus.ERROR, if (problem is DiagnosticFailure) problem.message.orEmpty() else "La etapa no pudo completarse. Revisa conexión, certificado y configuración.", code)
    }
    private fun fail(message: String) {
        diagnostics.record(Check.CONTROL, CheckStatus.ERROR, message)
        stopInternal(false); error(message)
    }

    fun stop() = stopInternal(true)
    private fun stopInternal(recordControl: Boolean) {
        if (recordControl && (state.value.active || state.value.connecting)) diagnostics.record(Check.CONTROL, CheckStatus.WARNING, "Cierre de sesión y liberación de audio solicitados; no se reanuda automáticamente.", "STOP_REQUESTED")
        generation++
        job?.cancel(); job = null
        timeout?.cancel(); timeout = null
        microphone?.setEnabled(false)
        peer?.setAudioRecording(false)
        channel?.unregisterObserver(); channel?.close(); channel?.dispose(); channel = null
        peer?.close(); peer?.dispose(); peer = null
        microphone?.dispose(); microphone = null
        source?.dispose(); source = null
        factory?.dispose(); factory = null
        module?.release(); module = null
        route.close()
        mutable.value = VoiceState()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, resource, _ -> resource.close() }
        }
    })
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
