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
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as PbtApplication
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
        Text(when { voice.failed -> "Error"; voice.paused -> "Pausado"; voice.status in listOf("Escuchando", "Te escucho") -> "Te escucho"; voice.responding -> "Hablando"; voice.connecting -> "Pensando"; voice.active -> "Conversación activa"; else -> "Listo" }, style = MaterialTheme.typography.titleLarge)
        Text(if (microphoneReady) "Micrófono preparado" else "Micrófono pendiente de permiso", style = MaterialTheme.typography.bodySmall)
        if (wakeEnabled) Text("Escuchando la frase de activación", color = MaterialTheme.colorScheme.primary)
        if (persistent != ListeningPhase.OFF) {
            Text(persistent.label)
            if (ContinuousWakeService.running && ListeningSettings(context).load().background) Text("Escucha en segundo plano autorizada", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = stop) { Text("Desactivar escucha") }
        }
        Button(onClick = { if (voice.active || voice.connecting) navigate("Conversación") else start() }, enabled = configured,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), shape = MaterialTheme.shapes.extraLarge) {
            Text("Hablar", style = MaterialTheme.typography.headlineMedium)
        }
        if (!configured) TextButton(onClick = { navigate(if (profile == null) "Asistentes" else "Servicios de IA") }) { Text("Preparar mi conversación") }
        if (voice.responding || voice.connecting) Button(onClick = { if (provider == "groq") app.hosted.interrupt() else app.voice.interrupt() }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Parar", style = MaterialTheme.typography.headlineSmall) }
        if (voice.active || voice.connecting) OutlinedButton(onClick = { if (provider == "groq") app.hosted.stop() else app.voice.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Terminar conversación") }
        TextButton(onClick = app::openChatGpt) { Text("Abrir ChatGPT") }
        ListeningControls(configured, microphoneReady, requestMic, stop, compact = true,
            configure = { navigate("Activación por voz") })
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
