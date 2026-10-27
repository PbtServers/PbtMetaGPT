package com.pbtservers.pbtmetagpt

import com.meta.wearable.dat.core.types.*
import org.junit.Assert.*
import org.junit.Test

class GlassesDeviceTest {
    @Test fun metaGlasses00r6SeAdmitePorMetadatosSinDeducirElModelo() {
        val id = DeviceIdentifier("identificador-dat")
        val glasses = GlassesDevice(id, "Meta Glasses 00R6", DeviceType.META_GLASSES,
            DeviceCompatibility.COMPATIBLE, LinkState.CONNECTED, false)
        assertTrue(glasses.canConnect)
        assertEquals("Meta Glasses 00R6", glasses.name)
        assertEquals(id, glasses.id)
        assertFalse(glasses.model.contains("00R6"))
        assertFalse(glasses.model.contains("Ray-Ban"))
        assertFalse(glasses.model.contains("Adventurer"))
    }
    @Test fun todosLosTiposDatCompatiblesPuedenConectarse() {
        for (type in DeviceType.entries) {
            assertTrue(GlassesDevice(DeviceIdentifier("id-dat"), "Nombre cualquiera", type, DeviceCompatibility.COMPATIBLE, LinkState.CONNECTED, false).canConnect)
        }
    }
    @Test fun compatibilidadYEnlaceNoSeSustituyenPorLaMarca() {
        val glasses = GlassesDevice(DeviceIdentifier("id-dat"), "Ray-Ban Meta", DeviceType.META_GLASSES,
            DeviceCompatibility.COMPATIBLE, LinkState.CONNECTED, false)
        assertFalse(glasses.copy(compatibility = DeviceCompatibility.DEVICE_UPDATE_REQUIRED).canConnect)
        assertFalse(glasses.copy(linkState = LinkState.DISCONNECTED).canConnect)
        assertFalse(glasses.copy(name = "Meta Glasses 00R6", linkState = LinkState.CONNECTING).canConnect)
    }
}
