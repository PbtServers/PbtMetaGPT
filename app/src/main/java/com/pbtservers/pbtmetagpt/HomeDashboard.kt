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
    Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Inicio", style = MaterialTheme.typography.headlineLarge)
        OutlinedCard(onClick = { navigate("Asistentes") }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Tu asistente", style = MaterialTheme.typography.labelLarge)
                    Text(profile?.name ?: "Elige un asistente", style = MaterialTheme.typography.headlineSmall)
                }
                Text("Cambiar", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(when { voice.failed -> "Error"; voice.responding -> "Hablando"; voice.connecting -> "Pensando"; else -> "Listo" }, style = MaterialTheme.typography.titleLarge)
        Button(onClick = { if (voice.active || voice.connecting) navigate("Conversación") else start() }, enabled = configured,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), shape = MaterialTheme.shapes.extraLarge) {
            Text("Hablar", style = MaterialTheme.typography.headlineMedium)
        }
        if (!configured) TextButton(onClick = { navigate(if (profile == null) "Asistentes" else "Servicios de IA") }) { Text("Preparar mi conversación") }
        if (voice.active || voice.connecting) OutlinedButton(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text("Detener") }
        ListeningControls(configured, microphoneReady, requestMic, stop, compact = true, configure = { navigate("Activación por voz") })
        OutlinedCard(onClick = { navigate("Dispositivos") }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gafas y audio", style = MaterialTheme.typography.titleMedium)
                Text(deviceSummary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(if (provider == "groq") "GroqCloud · acceso gratuito con límites" else "OpenAI API · uso facturable elegido", style = MaterialTheme.typography.bodySmall)
    }
}
