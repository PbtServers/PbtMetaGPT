package com.pbtservers.pbtmetagpt

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticScreen(
    diagnostics: Diagnostics, voice: RealtimeClient, audio: LocalAudioDiagnostics,
    configured: Boolean, backend: String, token: String, bluetooth: Boolean, microphoneReady: Boolean,
    refresh: () -> Unit, requestMic: () -> Unit, checkCamera: () -> Unit, onBack: () -> Unit,
) {
    val state by diagnostics.state.collectAsStateWithLifecycle()
    val vs by voice.state.collectAsStateWithLifecycle()
    val busy by audio.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var consent by remember { mutableStateOf<String?>(null) }
    val idle = !vs.active && !vs.connecting && !busy
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Diagnóstico · 0.3.0", style = MaterialTheme.typography.headlineMedium)
            Text("Los resultados muestran observaciones reales. Una etapa observada no certifica las gafas ni sustituye una prueba física. Las fechas pertenecen al último intento de cada comprobación.")
            Button(onClick = { audio.stop(); onBack() }) { Text("Volver al asistente") }
            OutlinedButton(onClick = refresh) { Text("Actualizar permisos, Bluetooth y rutas") }
            OutlinedButton(onClick = checkCamera) { Text("Consultar permiso de cámara DAT") }
            if (!configured) Text("Introduce la dirección HTTPS y el token personal en el asistente para probar OpenAI.")
            Button(onClick = { consent = "credencial" }, enabled = idle && configured) { Text("Comprobar credencial temporal") }
            Button(onClick = { consent = "realtime" }, enabled = idle && configured) { Text("Verificar Realtime sin micrófono") }
            Text("Las comprobaciones de OpenAI son reales y pueden consumir cuota. La primera solo valida la emisión de una credencial. La segunda negocia WebRTC y espera session.created, sin capturar voz ni solicitar respuestas.")
            if (!microphoneReady) OutlinedButton(onClick = requestMic) { Text("Permitir micrófono para la prueba local") }
            Button(onClick = { consent = "microfono" }, enabled = idle && microphoneReady) { Text("Probar micrófono durante 3 segundos") }
            Button(onClick = { consent = "altavoz" }, enabled = idle) { Text("Reproducir tono de prueba") }
            Text(if (bluetooth) "Preferencia actual: Bluetooth. Comprueba el tipo de ruta observado y en qué dispositivo oyes el tono." else "Preferencia actual: teléfono. El tono solicita su altavoz.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { audio.confirmSpeaker(true) }, enabled = !busy) { Text("He oído el tono") }
                OutlinedButton(onClick = { audio.confirmSpeaker(false) }, enabled = !busy) { Text("No lo he oído") }
            }
            if (busy || vs.connecting || vs.active) Button(onClick = { audio.stop(); voice.stop() }) { Text("Detener pruebas y conversación") }
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Diagnóstico PbtMetaGPT", diagnostics.report()))
            }) { Text("Copiar informe sin credenciales") }
            OutlinedButton(onClick = diagnostics::reset, enabled = idle) { Text("Borrar resultados de esta pantalla") }
            state.entries.values.forEach { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.check.title, style = MaterialTheme.typography.titleMedium)
                        Text(entry.status.label, color = if (entry.status == CheckStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text(entry.detail)
                        if (entry.code.isNotBlank()) Text("Código: ${entry.code}", style = MaterialTheme.typography.bodySmall)
                        if (entry.time != 0L) Text("Última observación: ${DateFormat.getDateTimeInstance().format(Date(entry.time))}", style = MaterialTheme.typography.bodySmall)
                        if (entry.manual) Text("Confirmación manual del usuario", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    consent?.let { test ->
        val description = when (test) {
            "microfono" -> "Se capturarán unos 3 segundos de audio para medir el nivel y consultar la ruta. Habla ahora al dispositivo que quieras probar. No se guarda ni envía audio."
            "altavoz" -> "Se reproducirá un tono suave durante un segundo por la ruta elegida. Comprueba el volumen antes de comenzar y confirma después dónde lo has oído."
            "realtime" -> "Se emitirá una credencial y se negociará una conexión real con OpenAI. No se activa el micrófono ni se solicita una respuesta. Puede consumir cuota."
            else -> "El backend emitirá una credencial temporal real. No se mostrará ni guardará. No se activan micrófono ni cámara."
        }
        AlertDialog(onDismissRequest = { consent = null }, title = { Text("Autorizar comprobación") }, text = { Text(description) },
            confirmButton = { TextButton(onClick = {
                consent = null
                when (test) {
                    "microfono" -> audio.microphone(bluetooth)
                    "altavoz" -> audio.speaker(bluetooth)
                    "realtime" -> voice.start(backend, token.trim(), bluetooth = false, testOnly = true)
                    else -> voice.probeCredentials(backend, token.trim())
                }
            }) { Text("Comenzar") } }, dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancelar") } })
    }
}
