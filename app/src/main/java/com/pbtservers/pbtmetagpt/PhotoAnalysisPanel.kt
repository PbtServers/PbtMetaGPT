package com.pbtservers.pbtmetagpt

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun PhotoAnalysisPanel(bitmap: Bitmap?, configured: Boolean, voice: VoiceState, error: String?, provider: String = "openai",
    onConfirm: (Bitmap) -> Unit, discard: () -> Unit, resume: () -> Unit, cancel: () -> Unit) {
    val status = PhotoAnalysisPolicy.status(bitmap != null && !bitmap.isRecycled, configured, voice, error != null)
    var confirmation by remember(bitmap) { mutableStateOf(false) }
    Text(status.message)
    if (bitmap == null || bitmap.isRecycled) return
    Image(bitmap.asImageBitmap(), contentDescription = "Vista previa local de la foto capturada; su envío requiere confirmación",
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    Button(onClick = { confirmation = true }, enabled = PhotoAnalysisPolicy.canAnalyze(status)) {
        Text(if (voice.sendingImage) "Enviando…" else "Preguntar sobre esta foto")
    }
    OutlinedButton(onClick = discard, enabled = !voice.connecting && !voice.responding && !voice.sendingImage) { Text("Descartar foto") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (voice.active && voice.paused) OutlinedButton(onClick = resume) { Text("Reanudar sesión") }
    if (voice.connecting || voice.active) OutlinedButton(onClick = cancel) { Text("Cancelar análisis o sesión") }
    if (voice.transcript.isNotBlank()) Text("Respuesta de ${if (provider == "groq") "Groq Vision" else "OpenAI"}:\n${voice.transcript}")
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false },
        title = { Text("Enviar esta foto a ${if (provider == "groq") "Groq Vision" else "OpenAI"}") },
        text = { Text("Se enviará una única fotografía al proveedor seleccionado. Groq puede consumir cuota Free y OpenAI puede generar costes de API. Revisa que no contiene información privada que no quieras compartir.") },
        confirmButton = { TextButton(onClick = { confirmation = false; onConfirm(bitmap) },
            enabled = PhotoAnalysisPolicy.canAnalyze(status)) { Text("Enviar y preguntar") } },
        dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Cancelar") } })
}
