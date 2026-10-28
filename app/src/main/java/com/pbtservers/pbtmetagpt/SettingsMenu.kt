package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Agrupación de destinos existentes: conserva proveedores, importaciones y diagnóstico. */
@Composable fun SettingsMenu(open: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = listOf(
        Triple("Conexiones", "Cuentas y servicios de conversación", listOf("Servicios de IA", "OpenAI")),
        Triple("Dispositivos", "Gafas, micrófono y Bluetooth", listOf("Dispositivos")),
        Triple("Voz y escucha", "Conversación, frases y voces", listOf("Conversación y escucha", "Activación por voz", "Biblioteca de voces", "Personalidad")),
        Triple("Integraciones", "Aplicaciones oficiales", listOf("ChatGPT oficial")),
        Triple("Privacidad", "Permisos y datos personales", listOf("Privacidad", "Permisos")),
        Triple("Avanzado", "Modelos, rendimiento y diagnóstico", listOf("Biblioteca de voces", "Diagnóstico")))
    groups.forEach { (name, description, destinations) ->
        TextButton(onClick = {
            if (name == "Dispositivos") open("Dispositivos") else expanded = if (expanded == name) null else name
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded == name) destinations.forEach { destination ->
            TextButton(onClick = { open(destination) }, modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) { Text(destination) }
        }
    }
}
