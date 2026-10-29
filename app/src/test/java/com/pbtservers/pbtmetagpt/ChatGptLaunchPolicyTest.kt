package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class ChatGptLaunchPolicyTest {
    @Test fun soloAbreDirectamenteConInterfazVisibleYTelefonoDesbloqueado() {
        for (visible in listOf(false, true)) for (locked in listOf(false, true)) {
            assertEquals(if (visible && !locked) ChatGptDestination.APPLICATION else ChatGptDestination.NOTIFICATION,
                ChatGptLaunchPolicy.destination(true, visible, locked, true))
        }
    }
    @Test fun sinInstalacionOSinNotificacionesNoInventaUnDestino() {
        assertEquals(ChatGptDestination.MISSING, ChatGptLaunchPolicy.destination(false, true, false, true))
        assertEquals(ChatGptDestination.NOTIFICATION_SETTINGS, ChatGptLaunchPolicy.destination(true, false, false, false))
        assertEquals(ChatGptDestination.NOTIFICATION_SETTINGS, ChatGptLaunchPolicy.destination(true, true, true, false))
        assertEquals(ChatGptDestination.APPLICATION, ChatGptLaunchPolicy.destination(true, true, false, false))
    }
}
