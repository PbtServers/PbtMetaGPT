package com.pbtservers.pbtmetagpt

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioDeviceCallback
import android.os.Handler
import android.os.Looper

/** Ruta de comunicación Android. No afirma que un dispositivo Bluetooth sea una Ray-Ban. */
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

    fun start(preferBluetooth: Boolean): String {
        previousMode = manager.mode
        previousDevice = manager.communicationDevice
        require(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            "Otra aplicación está usando el audio. Vuelve a intentarlo."
        }
        started = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val devices = manager.availableCommunicationDevices
        val bluetooth = if (preferBluetooth) devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        } else null
        val target = bluetooth ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        require(target != null && manager.setCommunicationDevice(target)) { "No se pudo seleccionar una ruta de audio. Comprueba los permisos y el dispositivo." }
        bluetoothDeviceId = bluetooth?.id
        manager.registerAudioDeviceCallback(devicesCallback, Handler(Looper.getMainLooper()))
        return if (bluetooth != null) "Bluetooth solicitado: ${bluetooth.productName}. Comprueba entrada y salida."
            else if (preferBluetooth) "Sin ruta Bluetooth disponible: se utilizará el teléfono."
            else "Audio del teléfono"
    }

    fun actualRoute(): String = manager.communicationDevice?.let { "Ruta Android: ${it.productName}" } ?: "Ruta Android no confirmada"

    fun close() {
        if (!started) return
        started = false
        manager.unregisterAudioDeviceCallback(devicesCallback)
        bluetoothDeviceId = null
        manager.clearCommunicationDevice()
        previousDevice?.let { runCatching { manager.setCommunicationDevice(it) } }
        manager.mode = previousMode
        manager.abandonAudioFocusRequest(focus)
    }
}
