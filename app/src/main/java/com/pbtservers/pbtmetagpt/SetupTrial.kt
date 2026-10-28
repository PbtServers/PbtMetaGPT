package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun SetupTrial(context: Context, profile: AssistantProfile, provider: String, configure: () -> Unit) {
    val voice = (context.applicationContext as PbtApplication).hosted
    val state by voice.state.collectAsStateWithLifecycle()
    val busy by voice.busy.collectAsStateWithLifecycle()
    val audioError by voice.audioError.collectAsStateWithLifecycle()
    Text("Todo preparado a tu ritmo", style = MaterialTheme.typography.titleLarge)
    Text("Puedes probar texto y voz ahora o finalizar y configurarlo después. Las gafas son opcionales.")
    if (provider == "groq") {
        Button(onClick = { voice.start(profile); voice.send("Saluda brevemente en español para probar texto y voz.") }, enabled = !busy) { Text("Probar conversación gratuita") }
        Text("La prueba envía una petición de ejemplo a Groq. Kokoro y Android generan su voz en el teléfono; Qwen remoto solo recibe texto con su consentimiento independiente. Consume cuota del proveedor.", style = MaterialTheme.typography.bodySmall)
        if (state.transcript.isNotBlank()) Text(state.transcript)
        Text(state.status)
        audioError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) OutlinedButton(onClick = voice::stop) { Text("Detener prueba") }
    } else Text("Has elegido OpenAI opcional, con facturación independiente. Podrás comprobarlo desde Ajustes tras finalizar; no se inicia ninguna llamada automáticamente.")
    TextButton(onClick = configure) { Text("Revisar servicio y clave") }
}
