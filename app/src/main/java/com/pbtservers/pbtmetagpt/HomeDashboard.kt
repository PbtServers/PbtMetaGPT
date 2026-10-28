package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun HomeDashboard(profile: AssistantProfile?, configured: Boolean, voice: VoiceState, deviceSummary: String,
    microphoneReady: Boolean, requestMic: () -> Unit, start: () -> Unit, stop: () -> Unit, provider: String = "openai",
    wakeEnabled: Boolean = false, toggleWake: (() -> Unit)? = null, navigate: (String) -> Unit,
) {
    val persistent by ContinuousWakeService.state.collectAsStateWithLifecycle()
    Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Inicio", style = MaterialTheme.typography.headlineLarge)
        OutlinedCard(onClick = { navigate("Asistentes") }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(profile?.let { it.avatar.ifBlank { it.name.take(2).uppercase(java.util.Locale.ROOT) } } ?: "◉", style = MaterialTheme.typography.displaySmall)
                    Text(profile?.name ?: "Elige un asistente", style = MaterialTheme.typography.headlineSmall)
                }
                Text("Cambiar", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(when { voice.failed -> "Error"; voice.status == "Escuchando" -> "Escuchando"; voice.responding -> "Hablando"; voice.connecting -> "Pensando"; else -> "Listo" }, style = MaterialTheme.typography.titleLarge)
        Text(if (microphoneReady) "Micrófono preparado" else "Micrófono pendiente de permiso", style = MaterialTheme.typography.bodySmall)
        if (wakeEnabled) Text("Escuchando la frase de activación", color = MaterialTheme.colorScheme.primary)
        if (persistent != ListeningPhase.OFF) {
            Text(persistent.label)
            TextButton(onClick = stop) { Text("Desactivar escucha") }
        }
        Button(onClick = { if (voice.active || voice.connecting) navigate("Conversación") else start() }, enabled = configured,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), shape = MaterialTheme.shapes.extraLarge) {
            Text("Hablar", style = MaterialTheme.typography.headlineMedium)
        }
        if (!configured) TextButton(onClick = { navigate(if (profile == null) "Asistentes" else "Servicios de IA") }) { Text("Preparar mi conversación") }
        if (voice.active || voice.connecting) OutlinedButton(onClick = stop, modifier = Modifier.fillMaxWidth()) { Text("Detener") }
        if (toggleWake != null) {
            OutlinedButton(onClick = toggleWake, enabled = configured && !voice.active && !voice.connecting, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (wakeEnabled) "Desactivar escucha por frase" else "Activar escucha por frase")
            }
            TextButton(onClick = { navigate("Activación por voz") }) { Text("Cambiar frase de activación") }
        } else ListeningControls(configured, microphoneReady, requestMic, stop, compact = true, configure = { navigate("Activación por voz") })
        OutlinedCard(onClick = { navigate("Dispositivos") }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gafas y audio", style = MaterialTheme.typography.titleMedium)
                Text(deviceSummary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        TextButton(onClick = { navigate("Dispositivos") }) { Text("Cámara de las gafas") }
        if (voice.transcript.isNotBlank()) {
            Text("Última conversación", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(voice.transcript.takeLast(1800), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
