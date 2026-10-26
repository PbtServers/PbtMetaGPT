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
class RealtimeClient(context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit) {
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
    private val route = AudioRoute(appContext) { scope.launch { stop(); error("Conversación detenida porque otra aplicación necesita el audio.") } }

    fun start(baseUrl: String, accessToken: String, bluetooth: Boolean) {
        if (state.value.active || state.value.connecting) return
        stop()
        val current = generation
        mutable.value = VoiceState(connecting = true, status = "Autorizando la conversación…")
        job = scope.launch {
            try {
                withTimeout(35_000) {
                    val endpoint = RealtimeProtocol.endpoint(baseUrl)
                    require(Regex("[A-Za-z0-9_-]{32,256}").matches(accessToken)) {
                        "Introduce el token de acceso personal de tu backend, no una clave API de OpenAI."
                    }
                    require(!accessToken.startsWith("sk-") && !accessToken.startsWith("sk_")) {
                        "No introduzcas claves API de OpenAI en la aplicación."
                    }
                    val tokenRequest = Request.Builder().url(endpoint)
                        .header("Authorization", "Bearer $accessToken")
                        .post(ByteArray(0).toRequestBody()).build()
                    val secret = http.newCall(tokenRequest).await().use { response ->
                        require(response.isSuccessful) { when (response.code) {
                            401, 403 -> "Tu backend ha rechazado el acceso. Comprueba tu token personal."
                            429 -> "Se ha alcanzado el límite de sesiones. Inténtalo más tarde."
                            else -> "El backend no pudo autorizar la sesión (HTTP ${response.code})."
                        } }
                        RealtimeProtocol.secret(response.body?.string().orEmpty(), System.currentTimeMillis() / 1000)
                    }
                    ensureActive()
                    val routeLabel = route.start(bluetooth)
                    mutable.update { it.copy(route = routeLabel, status = "Conectando con OpenAI…") }
                    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
                    val audioModule = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
                    module = audioModule
                    val createdFactory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()
                    factory = createdFactory
                    val connection = createdFactory.createPeerConnection(
                        PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN },
                        observer(current),
                    ) ?: throw IllegalStateException("No se pudo preparar WebRTC.")
                    peer = connection
                    val audioSource = createdFactory.createAudioSource(MediaConstraints())
                    source = audioSource
                    microphone = createdFactory.createAudioTrack("pbt-microphone", audioSource).apply { setEnabled(false) }
                    connection.addTrack(requireNotNull(microphone), listOf("pbt-audio"))
                    channel = connection.createDataChannel("oai-events", DataChannel.Init()).also { dc ->
                        dc.registerObserver(object : DataChannel.Observer {
                            override fun onBufferedAmountChange(previousAmount: Long) = Unit
                            override fun onStateChange() {
                                if (dc.state() == DataChannel.State.CLOSED) dispatch(current) { fail("Se ha cerrado el canal de conversación.") }
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
                    connection.setDescription(offer, local = true)
                    val request = Request.Builder().url("https://api.openai.com/v1/realtime/calls")
                        .header("Authorization", "Bearer $secret")
                        .post(offer.description.toRequestBody("application/sdp".toMediaType())).build()
                    val answer = http.newCall(request).await().use { response ->
                        require(response.isSuccessful) { "OpenAI rechazó la conexión (HTTP ${response.code}). Comprueba acceso al modelo y cuota del backend." }
                        response.body?.string() ?: throw IllegalStateException("OpenAI no devolvió una respuesta de conexión.")
                    }
                    ensureActive()
                    connection.setDescription(SessionDescription(SessionDescription.Type.ANSWER, answer), local = false)
                }
                if (!state.value.active) timeout = scope.launch {
                    delay(15_000)
                    if (generation == current && !state.value.active) fail("OpenAI no confirmó la sesión a tiempo.")
                }
            } catch (_: TimeoutCancellationException) { if (generation == current) fail("Se agotó el tiempo para iniciar la conversación.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (problem: Exception) {
                if (generation == current) fail(if (problem is IllegalArgumentException || problem is IllegalStateException)
                    problem.message ?: "No se pudo iniciar la conversación." else "No se pudo conectar. Comprueba la red y la dirección HTTPS del backend.")
            }
        }
    }

    private fun observer(current: Int) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
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
            (receiver.track() as? AudioTrack)?.setEnabled(true)
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
                    microphone?.setEnabled(true)
                    mutable.update { it.copy(connecting = false, active = true, status = "Escuchando · micrófono encendido", route = it.route + "\n" + route.actualRoute()) }
                }
                "response.created" -> mutable.update { it.copy(responding = true, status = "ChatGPT está respondiendo…") }
                "response.done" -> {
                    mutable.update { it.copy(responding = false, status = if (it.paused) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
                    if (event.optJSONObject("response")?.optString("status") == "failed") error("OpenAI no pudo completar la respuesta. Comprueba cuota y compatibilidad del modelo.")
                }
                "response.output_audio_transcript.delta" -> mutable.update { it.copy(transcript = (it.transcript + event.optString("delta")).takeLast(4000)) }
                "response.output_audio_transcript.done" -> mutable.update { it.copy(transcript = (it.transcript + "\n\n").takeLast(4000)) }
                "error" -> { mutable.update { it.copy(responding = false) }; error("OpenAI rechazó una operación de la sesión. Detén y vuelve a iniciar si el problema persiste.") }
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
        if (pause) interrupt()
        mutable.update { it.copy(paused = pause, status = if (pause) "En pausa · micrófono apagado" else "Escuchando · micrófono encendido") }
    }

    fun interrupt() {
        if (!state.value.active) return
        if (state.value.responding) send(RealtimeProtocol.event("response.cancel"))
        send(RealtimeProtocol.event("output_audio_buffer.clear"))
        send(RealtimeProtocol.event("input_audio_buffer.clear"))
    }

    fun askImage(bitmap: Bitmap, onSent: () -> Unit) {
        if (!state.value.active || state.value.responding || state.value.sendingImage) return
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
            } catch (_: Exception) { error("No se pudo enviar la imagen. Vuelve a intentarlo o descártala.")
            } finally { if (current == generation) mutable.update { it.copy(sendingImage = false) } }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        var edge = 512
        repeat(4) {
            val ratio = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()), maxOf(1, (bitmap.height * ratio).toInt()), true)
            val bytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 65, out); out.toByteArray() }
            if (scaled !== bitmap) scaled.recycle()
            // Dejar margen por debajo de 64 KiB para el mensaje SCTP completo.
            if (bytes.size <= 42_000) return Base64.encodeToString(bytes, Base64.NO_WRAP)
            edge /= 2
        }
        throw IllegalStateException("Imagen demasiado grande")
    }

    private fun fail(message: String) { stop(); error(message) }

    fun stop() {
        generation++
        job?.cancel(); job = null
        timeout?.cancel(); timeout = null
        microphone?.setEnabled(false)
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
