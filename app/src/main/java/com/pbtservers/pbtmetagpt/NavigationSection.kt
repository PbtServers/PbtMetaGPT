package com.pbtservers.pbtmetagpt

object NavigationSection {
    fun forPage(page: String) = when (page) {
        "Inicio", "Conversación" -> "Inicio"
        "Asistentes", "Personalidad", "Nombre del asistente" -> "Asistentes"
        "Voz", "Biblioteca de voces" -> "Voz"
        else -> "Ajustes"
    }
}
