package com.pbtservers.pbtmetagpt

import android.media.AudioDeviceInfo

data class AudioCandidate(val id: Int, val type: Int)

object AudioRoutePolicy {
    fun choose(devices: List<AudioCandidate>, bluetooth: Boolean, speaker: Boolean): Int? {
        if (bluetooth) return devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }?.id
        val preferred = if (speaker) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        return (devices.firstOrNull { it.type == preferred }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER })?.id
    }
}
