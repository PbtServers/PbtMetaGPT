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
fun PhotoAnalysisPanel(bitmap: Bitmap?, configured: Boolean, voice: VoiceState, error: String?,
    onConfirm: (Bitmap) -> Unit, discard: () -> Unit, resume: () -> Unit, cancel: () -> Unit) {
    val status = PhotoAnalysisPolicy.status(bitmap != null && !bitmap.isRecycled, configured, voice, error != null)
    var confirmation by remember(bitmap) { mutableStateOf(false) }
    Text(status.message)
    if (bitmap == null || bitmap.isRecycled) return
    Image(bitmap.asImageBitmap(), contentDescription = "Vista previa local de la foto capturada; su envío requiere confirmación",
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    Button(onClick = { confirmation = true }, enabled = PhotoAnalysisPolicy.canAnalyze(status)) {
        Text(if (voice.sendingImage) "Enviando…" else "Preguntar a ChatGPT sobre esta foto")
    }
    OutlinedButton(onClick = discard, enabled = !voice.connecting && !voice.responding && !voice.sendingImage) { Text("Descartar foto") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (voice.active && voice.paused) OutlinedButton(onClick = resume) { Text("Reanudar sesión") }
    if (voice.connecting || voice.active) OutlinedButton(onClick = cancel) { Text("Cancelar análisis o sesión") }
    if (voice.transcript.isNotBlank()) Text("Respuesta de OpenAI:\n${voice.transcript}")
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false },
        title = { Text("Enviar esta foto a OpenAI") },
        text = { Text("OpenAI recibirá esta imagen para responder a «¿Qué estoy viendo?». Puede consumir API. Si no hay sesión, se abrirá una con el micrófono apagado. Revisa que no contiene información que no quieras compartir.") },
        confirmButton = { TextButton(onClick = { confirmation = false; onConfirm(bitmap) },
            enabled = PhotoAnalysisPolicy.canAnalyze(status)) { Text("Enviar y preguntar") } },
        dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Cancelar") } })
}
