package com.pbtservers.pbtmetagpt

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioDeviceCallback
import android.os.Handler
import android.os.Looper

/** Ruta Android por capacidades de comunicación; sin deducir marca o modelo del nombre. */
class AudioRoute(context: Context, private val onInterrupted: (String) -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousDevice: AudioDeviceInfo? = null
    private var started = false
    private var bluetoothDeviceId: Int? = null
    private val devicesCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (started && removed.any { it.id == bluetoothDeviceId }) onInterrupted("Se ha desconectado el dispositivo Bluetooth. La conversación se ha detenido.")
        }
    }
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener {
            if (started && it < 0) onInterrupted("Conversación detenida porque otra aplicación necesita el audio.")
        }.build()

    fun start(preferBluetooth: Boolean, speaker: Boolean = false): String {
        previousMode = manager.mode
        previousDevice = manager.communicationDevice
        require(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Otra aplicación está usando el audio. Vuelve a intentarlo."
        }
        started = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val devices = manager.availableCommunicationDevices
        val targetId = AudioRoutePolicy.choose(devices.map { AudioCandidate(it.id, it.type) }, preferBluetooth, speaker)
        val target = devices.firstOrNull { it.id == targetId }
        if (preferBluetooth && target == null) throw DiagnosticFailure("AUDIO_BLUETOOTH_UNAVAILABLE", "No hay ruta Bluetooth de comunicación compatible. Puedes utilizar el micrófono y altavoz del móvil.")
        if (target == null || !manager.setCommunicationDevice(target)) throw DiagnosticFailure("AUDIO_ROUTE_UNAVAILABLE", "Android no pudo seleccionar la ruta solicitada. Puedes reintentar con el micrófono y altavoz del móvil.")
        bluetoothDeviceId = if (preferBluetooth) target.id else null
        manager.registerAudioDeviceCallback(devicesCallback, Handler(Looper.getMainLooper()))
        return if (preferBluetooth) "Bluetooth solicitado: ${target.productName}. Comprueba entrada y salida."
            else "Audio del teléfono"
    }

    fun actualRoute(): String = manager.communicationDevice?.let { "Ruta Android: ${it.productName}" } ?: "Ruta Android no confirmada"

    fun close() {
        if (!started) return
        started = false
        manager.unregisterAudioDeviceCallback(devicesCallback)
        bluetoothDeviceId = null
        try {
            manager.clearCommunicationDevice()
            previousDevice?.let { manager.setCommunicationDevice(it) }
            manager.mode = previousMode
        } catch (_: SecurityException) {
            // El usuario puede revocar Bluetooth durante la sesión; no reabrir la ruta.
            android.util.Log.w("PbtStart", "AUDIO_PERMISSION_REVOKED_ON_CLOSE")
        } finally { manager.abandonAudioFocusRequest(focus) }
    }
}
