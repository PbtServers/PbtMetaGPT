package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class CommunicationSourcePolicyTest {
    @Test fun exigeRutaConfirmadaYRechazaOtroAuricularConDireccionDiferente() {
        assertFalse(CommunicationSourcePolicy.bluetoothMatches(false, "entrada-a", "entrada-a"))
        assertFalse(CommunicationSourcePolicy.bluetoothMatches(true, "entrada-a", "entrada-b"))
        assertTrue(CommunicationSourcePolicy.bluetoothMatches(true, "entrada-a", "ENTRADA-A"))
    }
    @Test fun siAndroidOcultaDireccionesSeUsaElParSeleccionadoPorSuApiPublica() {
        assertTrue(CommunicationSourcePolicy.bluetoothMatches(true, "", ""))
        assertTrue(CommunicationSourcePolicy.bluetoothMatches(true, "entrada-a", ""))
        assertFalse(CommunicationSourcePolicy.bluetoothMatches(false, "", ""))
    }
}
