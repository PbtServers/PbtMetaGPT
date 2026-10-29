package com.pbtservers.pbtmetagpt

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Una ruta/foco por proceso. El servicio la retiene entre activación, turno y TTS. */
class CommunicationAudioSession(private val context: Context) {
    private val app = context.applicationContext as? PbtApplication
    private val manager = context.getSystemService(AudioManager::class.java)
    private val settings = AudioInputSettings(context)
    private val handler = Handler(Looper.getMainLooper())
    private val owners = linkedMapOf<Any, (String) -> Unit>()
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousDevice: AudioDeviceInfo? = null
    private var selected: AudioDeviceInfo? = null
    private var bluetooth = false
    private var confirmed = false
    private var interrupted = false
    private var configured = false
    private var requestedAt = 0L
    private var inputValid = false
    private var reportedDeviceId: Int? = null
    private var expectedAddress = ""
    private val mutableInput = MutableStateFlow(ActiveMicrophone())
    val input = mutableInput.asStateFlow()
    private val verify = Runnable {
        synchronized(this) {
            if (owners.isNotEmpty() && (manager.communicationDevice?.id != selected?.id || input.value.active && !inputValid))
                interrupt("Android no confirmó la ruta solicitada. Comprueba Bluetooth o selecciona el teléfono y reanuda expresamente.")
        }
    }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener({ if (it < 0) interrupt("Audio pausado porque otra aplicación o una llamada necesita el micrófono.") }, handler).build()
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.id == selected?.id }) interrupt("Se ha desconectado la ruta de audio. Selecciona una entrada disponible y reanuda expresamente.")
        }
    }
    private val communication = AudioManager.OnCommunicationDeviceChangedListener { device ->
        synchronized(this) {
            if (owners.isNotEmpty()) {
                if (device?.id == selected?.id) confirmed = true
                else if (confirmed) interrupt("La ruta de comunicación ha cambiado. Escucha pausada para evitar usar otro micrófono.")
            }
        }
    }
    private val recording = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val active = configs.singleOrNull()
            if (active == null) { synchronized(this@CommunicationAudioSession) { inputValid = false; mutableInput.value = ActiveMicrophone() }; return }
            if (active.isClientSilenced) { interrupt("Android ha silenciado el micrófono. Comprueba llamadas y permisos antes de reanudar."); return }
            report(active.audioDevice, active.clientFormat.sampleRate)
        }
    }
    @Synchronized fun acquire(owner: Any, legacyBluetooth: Boolean, speaker: Boolean, lost: (String) -> Unit): String {
        if (owner in owners) return label()
        val useBluetooth = settings.useBluetooth(legacyBluetooth)
        if (owners.isNotEmpty()) {
            check(!interrupted && bluetooth == useBluetooth) { "Detén la escucha antes de cambiar la ruta de audio." }
            owners[owner] = lost
            return label()
        }
        previousMode = manager.mode; previousDevice = manager.communicationDevice
        check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Otra aplicación está usando el audio. Reanuda con la app visible." }
        configured = true
        try {
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            val available = manager.availableCommunicationDevices
            val candidates = if (useBluetooth && settings.choice == MicrophoneChoice.GLASSES && settings.externalName.isNotBlank())
                available.filter { it.productName.toString() == settings.externalName } else available
            if (useBluetooth && candidates.count { AudioInputSettings.bluetooth(it.type) } > 1 && settings.choice == MicrophoneChoice.GLASSES && settings.externalName.isBlank())
                throw DiagnosticFailure("AUDIO_AMBIGUOUS", "Hay varios auriculares. Selecciona en Dispositivos cuál corresponde a tus gafas.")
            val id = AudioRoutePolicy.choose(candidates.map { AudioCandidate(it.id, it.type) }, useBluetooth, speaker)
            val target = candidates.firstOrNull { it.id == id }
                ?: throw DiagnosticFailure("AUDIO_ROUTE_UNAVAILABLE", "No hay ruta de comunicación compatible con la selección. Comprueba las gafas o elige el teléfono.")
            check(manager.setCommunicationDevice(target)) { "Android rechazó la ruta de comunicación." }
            bluetooth = useBluetooth; selected = target; confirmed = manager.communicationDevice?.id == target.id; interrupted = false
            expectedAddress = target.address
            requestedAt = android.os.SystemClock.elapsedRealtime()
            owners[owner] = lost
            manager.registerAudioDeviceCallback(devices, handler)
            manager.addOnCommunicationDeviceChangedListener(context.mainExecutor, communication)
            manager.registerAudioRecordingCallback(recording, handler)
            handler.postDelayed(verify, 3_000)
            return label()
        } catch (problem: Exception) { release(owner); throw problem }
    }
    private fun label() = "${if (bluetooth) "Bluetooth" else "Teléfono"} solicitado: ${selected?.productName ?: "sin ruta"}. Entrada PCM aún por comprobar."
    @Synchronized fun report(device: AudioDeviceInfo?, rate: Int): Boolean {
        if (owners.isEmpty() || interrupted) return false
        if (!confirmed) confirmed = manager.communicationDevice?.id == selected?.id
        val valid = device != null && (if (bluetooth) AudioInputSettings.bluetooth(device.type) && CommunicationSourcePolicy.bluetoothMatches(confirmed, expectedAddress, device.address)
            else device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC)
        inputValid = valid
        if (reportedDeviceId != device?.id || input.value.sampleRate != rate || input.value.active != (device != null)) {
            reportedDeviceId = device?.id
            mutableInput.value = ActiveMicrophone(device != null, AudioInputSettings.describe(device), device?.type ?: 0, rate)
        }
        if (!valid && android.os.SystemClock.elapsedRealtime() - requestedAt > 3_000)
            interrupt("La entrada PCM no coincide con el micrófono seleccionado. Escucha pausada; comprueba la ruta en Dispositivos.")
        return valid
    }
    @Synchronized fun captureConfirmed() = owners.isNotEmpty() && !interrupted && inputValid && input.value.active
    @Synchronized fun hasOwners() = owners.isNotEmpty()
    @Synchronized fun actualRoute() = "Comunicación: ${manager.communicationDevice?.productName ?: "sin confirmar"}. Captura: ${input.value.description}"
    @Synchronized fun release(owner: Any) {
        owners.remove(owner)
        if (owners.isNotEmpty() || !configured) return
        configured = false; selected = null; confirmed = false; interrupted = false; inputValid = false
        reportedDeviceId = null; expectedAddress = ""
        handler.removeCallbacks(verify)
        closePart { manager.unregisterAudioDeviceCallback(devices) }
        closePart { manager.removeOnCommunicationDeviceChangedListener(communication) }
        closePart { manager.unregisterAudioRecordingCallback(recording) }
        closePart { manager.clearCommunicationDevice() }
        closePart { previousDevice?.let { manager.setCommunicationDevice(it) } }
        closePart { manager.mode = previousMode }
        closePart { manager.abandonAudioFocusRequest(focus) }
        mutableInput.value = ActiveMicrophone()
    }
    @Synchronized private fun interrupt(message: String) {
        if (interrupted || owners.isEmpty()) return
        interrupted = true
        val callbacks = owners.values.toList()
        handler.post { callbacks.forEach { it(message) } }
    }
    private fun closePart(action: () -> Unit) {
        try { action() }
        catch (problem: RuntimeException) { app?.journal?.record(Check.ROUTE, Fault.RELEASE, problem) }
    }
}
