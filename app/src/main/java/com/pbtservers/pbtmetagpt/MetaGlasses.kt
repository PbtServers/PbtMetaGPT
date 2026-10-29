package com.pbtservers.pbtmetagpt

import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class GlassesState(
    val initialized: Boolean = false,
    val registered: Boolean = false,
    val status: String = "Concede acceso Bluetooth para buscar tus gafas",
    val devices: List<GlassesDevice> = emptyList(),
    val selectedDevice: DeviceIdentifier? = null,
    val cameraAvailable: Boolean? = null,
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val capturing: Boolean = false,
    val registering: Boolean = false,
    val photo: Bitmap? = null,
)

/** Ciclo DAT 1.0.1. La conexión solo se confirma al recibir STARTED del SDK. */
class MetaGlasses(context: Context, private val scope: CoroutineScope, private val error: (String) -> Unit, private val diagnostics: Diagnostics = Diagnostics(), private val photoMemory: PhotoMemory = PhotoMemory()) {
    private val context = context.applicationContext
    private val recovery = DatConnectionPolicy()
    private var recoveryJob: Job? = null
    private var requestedDevice: DeviceIdentifier? = null
    private val mutable = MutableStateFlow(GlassesState(photo = photoMemory.bitmap))
    val state = mutable.asStateFlow()
    private var session: DeviceSession? = null
    private val sessionJobs = mutableListOf<Job>()
    private var captureJob: Job? = null
    private var connectTimeout: Job? = null
    private var registrationTimeout: Job? = null
    private val metadataJobs = mutableMapOf<DeviceIdentifier, Job>()
    private val deviceDescriptions = mutableMapOf<DeviceIdentifier, GlassesDevice>()

    fun initialize() {
        if (state.value.initialized) return
        try {
            var ready = false
            Wearables.initialize(context).onSuccess { ready = true }.onFailure { cause, _ ->
                ready = cause == com.meta.wearable.dat.core.types.WearablesError.ALREADY_INITIALIZED
                if (!ready) {
                    diagnostics.record(Check.DAT_INIT, CheckStatus.ERROR, "DAT rechazó la inicialización.", Diagnostics.sdkCode(cause))
                    error("No se pudo preparar Meta DAT. Comprueba Bluetooth y Meta AI antes de reintentar.")
                }
            }
            if (!ready) return
            diagnostics.record(Check.DAT_INIT, CheckStatus.OK, "DAT ha aceptado la inicialización. Aún no se ha establecido una conexión con las gafas.", "INITIALIZED")
            mutable.update { it.copy(initialized = true, status = "SDK preparado; registra la aplicación en Meta AI") }
            scope.launch {
                Wearables.registrationErrorStream.collect { cause ->
                    registrationTimeout?.cancel()
                    registrationFailure(Diagnostics.sdkCode(cause), "Meta AI no pudo completar el registro. Comprueba que el modo de desarrollador esté activo, DAT instalado en las gafas y la aplicación actualizada. Puedes reintentarlo sin perder el Setup.")
                }
            }
            scope.launch {
                Wearables.registrationState.collect { registration ->
                    val registered = DatRegistrationPolicy.confirmed(registration)
                    val pending = registration == RegistrationState.REGISTERING
                    mutable.update { it.copy(registered = registered, registering = pending, status = when {
                        registered -> "Registro confirmado por Meta AI; puedes conectar las gafas"
                        pending -> "Esperando autorización en Meta AI…"
                        else -> "Sin registro confirmado; puedes reintentar desde aquí"
                    }) }
                    if (!pending) registrationTimeout?.cancel()
                    diagnostics.record(Check.DAT_REGISTRATION, if (registered) CheckStatus.OK else CheckStatus.WARNING, "Estado de registro informado por Meta AI.", registration.name)
                    if (!registered && session != null) disconnect()
                }
            }
            scope.launch {
                Wearables.devices.collect { devices ->
                    diagnostics.record(Check.DAT_DEVICES, if (devices.isEmpty()) CheckStatus.WARNING else CheckStatus.OK,
                        "DAT informa de ${devices.size} dispositivos. El descubrimiento no verifica sus capacidades.", "DEVICES_UPDATED")
                    (metadataJobs.keys - devices).forEach { id -> metadataJobs.remove(id)?.cancel(); deviceDescriptions.remove(id) }
                    devices.forEach { id ->
                        if (id !in metadataJobs) metadataJobs[id] = scope.launch {
                            Wearables.devicesMetadata[id]?.collect { metadata ->
                                val name = metadata.name.ifBlank { "Gafas Meta" }
                                deviceDescriptions[id] = GlassesDevice(id, name, metadata.deviceType,
                                    metadata.compatibility, metadata.linkState, metadata.isDisplayCapable())
                                mutable.update { it.copy(devices = deviceDescriptions.values.toList()) }
                                if (session == null) scheduleRecovery()
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

    fun register(activity: Activity) {
        if (!state.value.initialized) { error("Concede Bluetooth para preparar Meta DAT antes de registrar."); return }
        try {
            val info = activity.packageManager.getPackageInfo("com.facebook.stella", 0)
            diagnostics.record(Check.DAT_REGISTRATION, CheckStatus.RUNNING,
                "Meta AI instalada. Versión: ${info.longVersionCode}. El registro solo se confirma mediante DAT.", "REGISTRATION_REQUESTED")
            Wearables.startRegistration(activity)
            mutable.update { it.copy(registering = true, status = "Esperando autorización en Meta AI…") }
            registrationTimeout?.cancel()
            registrationTimeout = scope.launch {
                delay(35_000)
                if (!state.value.registered) {
                    mutable.update { it.copy(registering = false, status = "Registro no confirmado. Puedes reintentar sin perder la configuración.") }
                    diagnostics.record(Check.DAT_REGISTRATION, CheckStatus.WARNING,
                        "Meta AI no confirmó el registro. Revisa modo de desarrollador, DAT instalado en las gafas y configuración pública.", "REGISTRATION_NOT_CONFIRMED")
                    error("Meta AI no confirmó el registro o se canceló. Si muestra «Error al abrir el enlace», con las gafas conectadas desactiva y activa el modo de desarrollador, confirma que DAT esté instalado en las gafas y reinicia Meta AI. Después vuelve a intentarlo desde Dispositivos. Tu configuración se conserva.")
                }
            }
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            registrationFailure("META_AI_MISSING", "Instala o habilita Meta AI y empareja las gafas antes de registrar. Puedes omitir este paso.")
        } catch (_: android.content.ActivityNotFoundException) {
            registrationFailure("META_AI_LINK_UNAVAILABLE", "Meta AI no puede abrir el registro. Actualiza la aplicación y comprueba el modo de desarrollador con las gafas conectadas.")
        } catch (_: SecurityException) {
            registrationFailure("META_AI_PERMISSION", "Android ha rechazado abrir Meta AI. Revisa Bluetooth y que Meta AI esté habilitada.")
        } catch (_: IllegalStateException) {
            registrationFailure("DAT_NOT_READY", "Meta DAT no está preparado para registrar. Vuelve a Dispositivos y reinténtalo.")
        }
    }
    private fun registrationFailure(code: String, message: String) {
        mutable.update { it.copy(registering = false, status = message) }
        diagnostics.record(Check.DAT_REGISTRATION, CheckStatus.ERROR, message, code)
        error(message)
    }
    fun registrationReturn() {
        if (!state.value.initialized) return
        diagnostics.record(Check.DAT_REGISTRATION, if (state.value.registered) CheckStatus.OK else CheckStatus.WARNING,
            "Retorno a la aplicación. El enlace por sí solo no concede registro ni inicia sensores.", "RETURN_TO_APP")
    }
    fun unregister(activity: Activity) { disconnect(); safely { Wearables.startUnregistration(activity) } }

    fun connect(deviceId: DeviceIdentifier? = null) {
        recoveryJob?.cancel(); recoveryJob = null
        recovery.authorize(); requestedDevice = deviceId
        connectAuthorized(deviceId)
    }

    private fun connectAuthorized(deviceId: DeviceIdentifier?) {
        if (!state.value.registered || state.value.connecting || session != null) return
        if (deviceId != null && state.value.devices.none { it.id == deviceId && it.canConnect }) {
            error("DAT no confirma que estas gafas estén disponibles y sean compatibles. Revisa el estado indicado y vuelve a intentarlo.")
            return
        }
        mutable.update { it.copy(connecting = true, selectedDevice = deviceId, cameraAvailable = null, status = "Conectando con las gafas…") }
        val selector = if (deviceId == null) AutoDeviceSelector() else SpecificDeviceSelector(deviceId)
        Wearables.createSession(selector).onSuccess { created ->
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
                        selectedDevice = selector.activeDevice(),
                        connecting = value == DeviceSessionState.STARTING || value == DeviceSessionState.IDLE,
                        status = when (value) {
                            DeviceSessionState.STARTED -> "Sesión DAT conectada · ${deviceDescriptions[selector.activeDevice()]?.name ?: "gafas Meta"}"
                            DeviceSessionState.PAUSED -> "Conexión en pausa; comprueba las gafas"
                            DeviceSessionState.STOPPING -> "Desconectando…"
                            DeviceSessionState.STOPPED -> "Gafas desconectadas"
                            else -> "Conectando con las gafas…"
                        }
                    ) }
                    if (value == DeviceSessionState.STARTED) connectTimeout?.cancel()
                    if (value != DeviceSessionState.STARTED) captureJob?.cancel()
                    if (value == DeviceSessionState.STOPPED) {
                        clearSession()
                        mutable.update { it.copy(connected = false, connecting = false, capturing = false, cameraAvailable = null) }
                        scheduleRecovery()
                    }
                }
            }
            sessionJobs += scope.launch { created.errors.collect { cause ->
                diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT ha comunicado un error de sesión. Comprueba registro, firmware y disponibilidad.", Diagnostics.sdkCode(cause))
                error("Error de las gafas. Comprueba conexión, firmware y permisos en Meta AI.")
                session?.stop(); clearSession()
                mutable.update { it.copy(connected = false, connecting = false, capturing = false, cameraAvailable = null) }
                scheduleRecovery()
            } }
            created.start()
            connectTimeout = scope.launch {
                delay(25_000)
                if (!state.value.connected) {
                    diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT no confirmó la conexión antes del límite.", "DAT_TIMEOUT")
                    error("No se han podido conectar las gafas a tiempo.")
                    session?.stop(); clearSession()
                    mutable.update { it.copy(connected = false, connecting = false, capturing = false, cameraAvailable = null) }
                    scheduleRecovery()
                }
            }
        }.onFailure { cause, _ ->
            diagnostics.record(Check.DAT_SESSION, CheckStatus.ERROR, "DAT no pudo crear una sesión.", Diagnostics.sdkCode(cause))
            mutable.update { it.copy(connecting = false, status = "No se pudo crear la conexión") }
            error("Meta no pudo crear una sesión. Revisa el registro y la disponibilidad de las gafas.")
            scheduleRecovery()
        }
    }

    private fun clearSession() {
        captureJob?.cancel()
        session = null
        connectTimeout?.cancel()
        sessionJobs.forEach { it.cancel() }
        sessionJobs.clear()
    }

    fun disconnect() {
        recovery.revoke(); recoveryJob?.cancel(); recoveryJob = null
        captureJob?.cancel()
        session?.stop()
        clearSession()
        mutable.update { it.copy(connected = false, connecting = false, capturing = false, cameraAvailable = null, selectedDevice = null, status = "Sin conexión con las gafas") }
    }

    private fun scheduleRecovery() {
        if (recoveryJob?.isActive == true || session != null || !state.value.registered) return
        val owner = context as? PbtApplication ?: return
        val audio = context.getSystemService(AudioManager::class.java)
        val hfp = audio.mode == AudioManager.MODE_IN_COMMUNICATION && audio.communicationDevice?.type in
            listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
        val available = state.value.devices.any { it.canConnect && (requestedDevice == null || it.id == requestedDevice) }
        val wait = recovery.nextDelay(available, owner.activityVisible || ContinuousWakeService.running, hfp)
        if (wait == null) {
            if (recovery.authorized) mutable.update { it.copy(status = if (hfp)
                "Sesión DAT detenida mientras HFP sigue activo. Posible límite de firmware; el audio Bluetooth se comprueba aparte. Reconecta DAT expresamente sin audio."
                else "Sesión DAT detenida. Sin reintentos disponibles; comprueba las gafas y pulsa Conectar.") }
            return
        }
        mutable.update { it.copy(status = "Sesión DAT perdida · recuperación ${recovery.attempts}/3 en ${wait / 1000} s. No se reanuda la cámara.") }
        recoveryJob = scope.launch {
            delay(wait)
            recoveryJob = null
            if (recovery.authorized && (owner.activityVisible || ContinuousWakeService.running) && session == null && state.value.registered)
                connectAuthorized(requestedDevice)
        }
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
                            mutable.update { it.copy(cameraAvailable = true) }
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
                                    photoMemory.bitmap = bitmap
                                    photoMemory.error = null
                                    mutable.update { it.copy(photo = bitmap) }
                                    diagnostics.record(Check.CAMERA_CAPTURE, CheckStatus.OK, "DAT ha entregado una imagen para vista previa local. No se ha enviado a OpenAI.", "PHOTO_RECEIVED")
                                }.onFailure { cause, _ -> throw DiagnosticFailure(Diagnostics.sdkCode(cause), "DAT no pudo capturar una foto.") }
                            }
                        }.onFailure { cause, _ ->
                            mutable.update { it.copy(cameraAvailable = false) }
                            throw DiagnosticFailure(Diagnostics.sdkCode(cause), "DAT no pudo adjuntar la cámara.")
                        }
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

    fun discardPhoto() { photoMemory.bitmap = null; photoMemory.error = null; mutable.update { it.copy(photo = null) } }
    fun stopCapture() { captureJob?.cancel() }
    private fun safely(block: () -> Unit) { try { block() } catch (_: Exception) { error("No se pudo abrir Meta AI. Comprueba que está instalada y actualizada.") } }
}
