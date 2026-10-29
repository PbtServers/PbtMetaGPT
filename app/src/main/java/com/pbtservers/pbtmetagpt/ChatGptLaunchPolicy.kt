package com.pbtservers.pbtmetagpt

enum class ChatGptDestination { APPLICATION, NOTIFICATION, MISSING, NOTIFICATION_SETTINGS }

object ChatGptLaunchPolicy {
    fun destination(installed: Boolean, visible: Boolean, locked: Boolean, notifications: Boolean) = when {
        !installed -> ChatGptDestination.MISSING
        visible && !locked -> ChatGptDestination.APPLICATION
        notifications -> ChatGptDestination.NOTIFICATION
        else -> ChatGptDestination.NOTIFICATION_SETTINGS
    }
    const val voiceInstructions = "En ChatGPT abre Ajustes → Voz y activa «Iniciar con Voz», si tu versión lo ofrece. Si aparece la elección de modo, selecciona Live. Al abrir una conversación nueva o vacía, ChatGPT iniciará Voz automáticamente. Si recupera un chat con mensajes, pulsa Nueva conversación o el botón de voz en ChatGPT. PbtMetaGPT no puede activar esa opción ni comprobar que Live haya empezado."
}
