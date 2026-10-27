package com.pbtservers.pbtmetagpt

import com.meta.wearable.dat.core.types.RegistrationState
import org.junit.Assert.*
import org.junit.Test

class DatRegistrationPolicyTest {
    @Test fun soloElEstadoConfirmadoDelSdkConcedeRegistro() {
        RegistrationState.entries.forEach { state ->
            assertEquals(state == RegistrationState.REGISTERED, DatRegistrationPolicy.confirmed(state))
        }
        assertFalse(DatRegistrationPolicy.confirmed(RegistrationState.UNREGISTERING))
    }
    @Test fun elCallbackSoloReconoceElEsquemaPropioYNoOtorgaRegistro() {
        assertTrue(DatRegistrationPolicy.callback("android.intent.action.VIEW", "pbtmetagpt"))
        assertFalse(DatRegistrationPolicy.callback("android.intent.action.VIEW", "https"))
        assertFalse(DatRegistrationPolicy.callback("android.intent.action.MAIN", "pbtmetagpt"))
        assertFalse(DatRegistrationPolicy.callback(null, null))
        assertFalse(DatRegistrationPolicy.confirmed(RegistrationState.REGISTERING))
    }
}
