package com.pbtservers.pbtmetagpt

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class GlassesState(
    val initialized: Boolean = false,
    val registered: Boolean = false,
    val status: String = "Concede acceso Bluetooth para buscar tus gafas",
    val devices: List<String> = emptyList(),
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val capturing: Boolean = false,
    val photo: Bitmap? = null,
)

/** Ciclo DAT 1.0.1. La conexión solo se confirma al recibir STARTED del SDK. */
class MetaGlasses(private val activity: Activity, private val scope: CoroutineScope, private val error: (String) -> Unit, private val diagnostics: Diagnostics = Diagnostics()) {
    private val mutable = MutableStateFlow(GlassesState())
    val state = mutable.asStateFlow()
    private var session: DeviceSession? = null
    private val sessionJobs = mutableListOf<Job>()
    private var captureJob: Job? = null
    private var connectTimeout: Job? = null
    private val metadataJobs = mutableMapOf<DeviceIdentifier, Job>()
    private val deviceDescriptions = mutableMapOf<DeviceIdentifier, String>()

    fun initialize() {
        if (state.value.initialized) return
        try {
            Wearables.initialize(activity)
            diagnostics.record(Check.DAT_INIT, CheckStatus.OK, "DAT ha aceptado la inicialización. Aún no se ha establecido una conexión con las gafas.", "INITIALIZED")
            mutable.update { it.copy(initialized = true, status = "SDK preparado; registra la aplicación en Meta AI") }
            scope.launch {
                Wearables.registrationState.collect { registration ->
                    val registered = registration == RegistrationState.REGISTERED || registration == RegistrationState.UNREGISTERING
                    mutable.update { it.copy(registered = registered) }
                    diagnostics.record(Check.DAT_REGISTRATION, if (registered) CheckStatus.OK else CheckStatus.WARNING, "Estado de registro informado por Meta AI.", registration.name)
                    if (!registered) disconnect()
                }
            }
            scope.launch {
                Wearables.devices.collect { devices ->
                    diagnostics.record(Check.DAT_DEVICES, if (devices.isEmpty()) CheckStatus.WARNING else CheckStatus.OK,
                        "DAT informa de ${devices.size} dispositivos. El descubrimiento no verifica sus capacidades.", "DEVICES_UPDATED")
                    (metadataJobs.keys - devices).forEach { id -> metadataJobs.remove(id)?.cancel(); deviceDescriptions.remove(id) }
                    devices.forEach { id ->
                        deviceDescriptions.putIfAbsent(id, "Gafas detectadas · comprobando compatibilidad")
                        if (id !in metadataJobs) metadataJobs[id] = scope.launch {
                            Wearables.devicesMetadata[id]?.collect { metadata ->
                                val name = metadata.name.ifBlank { "Gafas Meta" }
                                deviceDescriptions[id] = if (metadata.compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED)
                                    "$name · actualización de firmware necesaria" else "$name · detectadas por DAT; prueba física pendiente"
                                mutable.update { it.copy(devices = deviceDescriptions.values.toList()) }
                            }
                        }
                    }
                    mutable.update { it.copy(devices = deviceDescriptions.values.toList()) }
                }
            }
        } catch (problem: Exception) {
            diagnostics.record(Check.DAT_INIT, CheckStatus.ERROR, "No se pudo inicializar DAT. Comprueba Meta AI y los permisos.", Diagnostics.sdkCode(problem))
            error("No se pudo inicializar Meta Wearables. Comprueba Meta AI y los permisos Bluetooth.")
        }
    }

    fun register() = safely { Wearables.startRegistration(activity) }
    fun unregister() { disconnect(); safely { Wearables.startUnregistration(activity) } }

    fun connect() {
        if (!state.value.registered || state.value.connecting || session != null) return
        mutable.update { it.copy(connecting = true, status = "Conectando con las gafas…") }
        Wearables.createSession(AutoDeviceSelector()).onSuccess { created ->
            session = created
            sessionJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
                created.state.collect { value ->
                    diagnostics.record(Check.DAT_SESSION, when (value) {
                        DeviceSessionState.STARTED -> CheckStatus.OK
                        DeviceSessionState.STARTING, DeviceSessionState.IDLE -> CheckStatus.RUNNING
                        else -> CheckStatus.WARNING
                    }, "Estado real de sesión comunicado por DAT.", value.name)
                    mutable.update { it.copy(
                        connected = value == DeviceSessionState.STARTED,
                        connecting = value == DeviceSessionState.STARTING || value == DeviceSessionState.IDLE,
                        status = when (value) {
                            DeviceSessionState.STARTED -> "Gafas conectadas"
                            DeviceSessionState.PAUSED -> "Conexión en pausa; comprueba las gafas"
                            DeviceSessionState.STOPPING -> "Desconectando…"
                            DeviceSessionState.STOPPED -> "Gafas desconectadas"
                            else -> "Conectando con las gafas…"
                        }
                    ) }
                    if (value == DeviceSessionState.STARTED) connectTimeout?.cancel()
                    if (value != DeviceSessionState.STARTED) captureJob?.cancel()
                    if (value == DeviceSessionState.STOPPED) clearSession()
                }
            }
            sessionJobs += scope.launch { created.errors.collect { cause ->
                diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT ha comunicado un error de sesión. Comprueba registro, firmware y disponibilidad.", Diagnostics.sdkCode(cause))
                error("Error de las gafas. Comprueba conexión, firmware y permisos en Meta AI.")
                disconnect()
            } }
            created.start()
            connectTimeout = scope.launch {
                delay(25_000)
                if (!state.value.connected) {
                    diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT no confirmó la conexión antes del límite.", "DAT_TIMEOUT")
                    error("No se han podido conectar las gafas a tiempo."); disconnect()
                }
            }
        }.onFailure { cause, _ ->
            diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT no pudo crear una sesión.", Diagnostics.sdkCode(cause))
            mutable.update { it.copy(connecting = false, status = "No se pudo crear la conexión") }
            error("Meta no pudo crear una sesión. Revisa el registro y la disponibilidad de las gafas.")
        }
    }

    private fun clearSession() {
        session = null
        connectTimeout?.cancel()
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
    }

    fun disconnect() {
        captureJob?.cancel()
        session?.stop()
        clearSession()
        mutable.update { it.copy(connected = false, connecting = false, capturing = false, photo = null, status = "Sin conexión con las gafas") }
    }

    /** Solo tras consentimiento explícito y permiso CAMERA de Meta. */
    fun capture() {
        val current = session ?: return
        if (!state.value.connected || state.value.capturing) return
        mutable.update { it.copy(capturing = true, photo = null) }
        diagnostics.record(Check.CAMERA_CAPTURE, CheckStatus.RUNNING, "Captura puntual autorizada. No se envía la imagen.")
        captureJob = scope.launch {
            try {
                withTimeout(20_000) {
                    current.addCamera(StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24))
                        .onSuccess { camera ->
                            camera.use {
                                camera.stream.start().onFailure { cause, _ -> throw DiagnosticFailure(Diagnostics.sdkCode(cause), "DAT no pudo iniciar la cámara.") }
                                camera.stream.state.first {
                                    diagnostics.record(Check.CAMERA_STREAM, if (it == StreamState.STREAMING) CheckStatus.OK else CheckStatus.RUNNING, "Estado observado del flujo local de cámara.", it.name)
                                    it == StreamState.STREAMING
                                }
                                camera.stream.capturePhoto().onSuccess { data ->
                                    val bitmap = withContext(Dispatchers.Default) {
                                        when (data) {
                                            is PhotoData.Bitmap -> data.bitmap
                                            is PhotoData.HEIC -> ImageDecoder.decodeBitmap(
                                                ImageDecoder.createSource(data.data.duplicate().apply { rewind() })
                                            ) { decoder, info, _ ->
                                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                                val ratio = minOf(1f, 1280f / maxOf(info.size.width, info.size.height))
                                                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                                            }
                                        }
                                    }
                                    mutable.update { it.copy(photo = bitmap) }
                                    diagnostics.record(Check.CAMERA_CAPTURE, CheckStatus.OK, "DAT ha entregado una imagen para vista previa local. No se ha enviado a OpenAI.", "PHOTO_RECEIVED")
                                }.onFailure { cause, _ -> throw DiagnosticFailure(Diagnostics.sdkCode(cause), "DAT no pudo capturar una foto.") }
                            }
                        }.onFailure { cause, _ -> throw DiagnosticFailure(Diagnostics.sdkCode(cause), "DAT no pudo adjuntar la cámara.") }
                }
            } catch (_: TimeoutCancellationException) {
                diagnostics.record(Check.CAMERA_CAPTURE, CheckStatus.ERROR, "La cámara no respondió antes del límite.", "CAMERA_TIMEOUT")
                error("La cámara no respondió a tiempo. Comprueba que llevas puestas las gafas.")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (problem: Exception) {
                diagnostics.record(Check.CAMERA_CAPTURE, CheckStatus.ERROR, "No se completó la captura. Revisa permisos, disponibilidad y firmware.", if (problem is DiagnosticFailure) problem.code else Diagnostics.sdkCode(problem))
                error("No se pudo capturar la foto. La cámara puede no estar disponible en estas gafas o en su firmware.")
            } finally {
                diagnostics.record(Check.CAMERA_STREAM, CheckStatus.WARNING, "El flujo de captura se ha cerrado o cancelado.", "CAPTURE_CLOSED")
                mutable.update { it.copy(capturing = false) }
            }
        }
    }

    fun discardPhoto() { mutable.update { it.copy(photo = null) } }
    fun stopCapture() { captureJob?.cancel(); discardPhoto() }
    private fun safely(block: () -> Unit) { try { block() } catch (_: Exception) { error("No se pudo abrir Meta AI. Comprueba que está instalada y actualizada.") } }
}
