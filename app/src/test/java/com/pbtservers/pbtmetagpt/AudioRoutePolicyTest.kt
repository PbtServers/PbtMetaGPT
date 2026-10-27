package com.pbtservers.pbtmetagpt

import android.media.AudioDeviceInfo
import org.junit.Assert.*
import org.junit.Test

class AudioRoutePolicyTest {
    private val phone = AudioCandidate(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
    @Test fun sinGafasOfreceMovilSoloSiSeSolicita() {
        assertNull(AudioRoutePolicy.choose(listOf(phone), true, true))
        assertEquals(1, AudioRoutePolicy.choose(listOf(phone), false, true))
    }
    @Test fun metaGlassesYRaybanUsanCapacidadScoOBleSinNombre() {
        for (type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)) {
            assertEquals(2, AudioRoutePolicy.choose(listOf(phone, AudioCandidate(2, type)), true, true))
        }
        // A2DP por sí solo no proporciona la ruta de comunicación bidireccional.
        assertNull(AudioRoutePolicy.choose(listOf(AudioCandidate(2, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)), true, true))
    }
    @Test fun desconexionImpideReutilizarUnaRutaAntigua() {
        assertEquals(2, AudioRoutePolicy.choose(listOf(AudioCandidate(2, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)), true, false))
        assertNull(AudioRoutePolicy.choose(emptyList(), true, false))
    }
}
