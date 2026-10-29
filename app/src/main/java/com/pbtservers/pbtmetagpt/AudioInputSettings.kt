package com.pbtservers.pbtmetagpt

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class MicrophoneChoice { AUTOMATIC, GLASSES, PHONE }

/** Preferencias sin activación implícita. El nombre externo lo elige el usuario; no identifica hardware DAT. */
class AudioInputSettings(context: Context) {
    private val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AudioManager::class.java)
    val choice get() = runCatching { MicrophoneChoice.valueOf(prefs.getString("microfono", "AUTOMATIC").orEmpty()) }.getOrDefault(MicrophoneChoice.AUTOMATIC)
    val externalName get() = prefs.getString("microfono-externo", "").orEmpty()
    fun save(choice: MicrophoneChoice, name: String = "") {
        require(name.length <= 200)
        check(prefs.edit().putString("microfono", choice.name).putString("microfono-externo", name).commit())
    }
    fun externalDevices(): List<AudioDeviceInfo> = try { manager.availableCommunicationDevices.filter { bluetooth(it.type) } }
        catch (_: SecurityException) { emptyList() }
    fun preferredInput(): AudioDeviceInfo? {
        val external = useBluetooth()
        val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        if (!external) return inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val candidates = inputs.filter { bluetooth(it.type) }
        val target = externalDevices().firstOrNull { choice != MicrophoneChoice.GLASSES || externalName.isBlank() || it.productName.toString() == externalName }
        val matching = candidates.filter { target == null || CommunicationSourcePolicy.bluetoothMatches(true, target.address, it.address) }
        return matching.firstOrNull { target?.address?.isNotBlank() == true && it.address.equals(target.address, ignoreCase = true) }
            ?: matching.firstOrNull { target != null && it.productName.toString() == target.productName.toString() }
            ?: matching.singleOrNull()
    }
    fun prefer(recorder: android.media.AudioRecord) {
        preferredInput()?.let { check(recorder.setPreferredDevice(it)) { "Android rechazó la entrada preferida." } }
    }
    fun useBluetooth(legacy: Boolean = prefs.getBoolean("audio-bluetooth", false)): Boolean = when (choice) {
        MicrophoneChoice.PHONE -> false
        MicrophoneChoice.GLASSES -> true
        MicrophoneChoice.AUTOMATIC -> legacy || externalDevices().isNotEmpty()
    }
    fun accepts(device: AudioDeviceInfo?, expectedBluetooth: Boolean): Boolean {
        if (device == null) return false
        if (!expectedBluetooth) return device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        val communication = manager.communicationDevice ?: return false
        return bluetooth(device.type) && bluetooth(communication.type) &&
            (choice != MicrophoneChoice.GLASSES || externalName.isBlank() || communication.productName.toString() == externalName) &&
            CommunicationSourcePolicy.bluetoothMatches(true, communication.address, device.address)
    }
    companion object {
        fun bluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET
        fun describe(device: AudioDeviceInfo?): String = device?.let {
            "${it.productName} · ${when { bluetooth(it.type) -> "Bluetooth HFP/LE"; it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micrófono del teléfono"; else -> "tipo Android ${it.type}" }} · id ${it.id}"
        } ?: "entrada real sin confirmar"
    }
}

data class ActiveMicrophone(val active: Boolean = false, val description: String = "Micrófono inactivo", val type: Int = 0, val sampleRate: Int = 0)
