package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun HomeDashboard(profile: AssistantProfile?, configured: Boolean, voice: VoiceState, deviceSummary: String,
    microphoneReady: Boolean, requestMic: () -> Unit, start: () -> Unit, stop: () -> Unit, provider: String = "openai", navigate: (String) -> Unit,
) {
    Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("PbtMetaGPT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Tu asistente, a tu manera", style = MaterialTheme.typography.headlineLarge)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(profile?.let { "${it.avatar.ifBlank { "◉" }}  ${it.name}" } ?: "Crea tu primer asistente", style = MaterialTheme.typography.headlineSmall)
                Text(profile?.personality?.take(200)?.ifBlank { "Listo para escucharte cuando tú decidas." } ?: "Elige su nombre, personalidad y voz.")
                val service = if (provider == "groq") "GroqCloud · Free" else "OpenAI API · facturable"
                Text(if (voice.sessionConfirmed) "$service · sesión iniciada" else if (configured) "$service configurado · sin sesión activa" else "$service pendiente de configuración", style = MaterialTheme.typography.labelLarge)
                Text(deviceSummary, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = start, enabled = configured && !voice.active && !voice.connecting, modifier = Modifier.fillMaxWidth()) { Text("Iniciar conversación") }
                if (voice.active || voice.connecting) {
                    Text(voice.status)
                    OutlinedButton(onClick = { navigate("Conversación") }) { Text("Abrir conversación actual") }
                }
            }
        }
        ListeningControls(configured, microphoneReady, requestMic, stop)
        Text("Tu espacio", style = MaterialTheme.typography.titleLarge)
        listOf("Asistentes" to "Personalidad y perfiles", "Voz" to "Explora tu biblioteca de audio",
            "Dispositivos" to "Gafas Meta, cámara y análisis de fotos", "Activación por voz" to "Frases y coincidencia local",
            "Servicios de IA" to "Elige y comprueba un proveedor gratuito", "Ajustes" to "Conexión, permisos, privacidad y diagnóstico").forEach { (destination, description) ->
            OutlinedCard(onClick = { navigate(destination) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(destination, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium) }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text("Versión experimental. Micrófono, altavoces y escucha con pantalla bloqueada requieren pruebas en tu teléfono y gafas.", style = MaterialTheme.typography.bodySmall)
    }
}
