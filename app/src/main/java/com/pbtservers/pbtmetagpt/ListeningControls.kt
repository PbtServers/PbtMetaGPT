package com.pbtservers.pbtmetagpt

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import java.io.IOException

@Composable fun ListeningControls(configured: Boolean, microphoneReady: Boolean, requestMic: () -> Unit, stopVisible: () -> Unit,
    compact: Boolean = false, configure: () -> Unit = {}) {
    val context = LocalContext.current
    val phase by ContinuousWakeService.state.collectAsStateWithLifecycle()
    val running by ContinuousWakeService.isRunning.collectAsStateWithLifecycle()
    val actualInput by ContinuousWakeService.inputType.collectAsStateWithLifecycle()
    val app = context.applicationContext as PbtApplication
    val download by app.wakeDownload.state.collectAsStateWithLifecycle()
    val installed = download.installed
    val downloading = download.downloading
    val problem by ContinuousWakeService.error.collectAsStateWithLifecycle()
    val recognition by app.hosted.recognition.collectAsStateWithLifecycle()
    var status by remember { mutableStateOf("") }
    var downloadConsent by remember { mutableStateOf(false) }
    val settings = remember { HostedSettings(context) }
    var mode by remember { mutableStateOf(settings.recognitionMode) }
    var source by remember { mutableStateOf(settings.dictationSource) }
    LaunchedEffect(Unit) { app.wakeDownload.refresh() }
    var consent by remember { mutableStateOf(false) }
    var backgroundConsent by remember { mutableStateOf(ListeningSettings(context).load().background) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Notificaciones autorizadas. Pulsa Activar escucha para continuar." else "Activa las notificaciones para disponer del control de parada fuera de la aplicación."
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Escucha permanente", style = MaterialTheme.typography.titleLarge)
            Text(phase.label, color = MaterialTheme.colorScheme.primary)
            if (phase == ListeningPhase.LISTENING) Text(when (actualInput) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Entrada comprobada por Android: micrófono del teléfono"
                0 -> "Entrada real aún sin confirmar"
                else -> "Entrada comprobada por Android: tipo $actualInput · no acredita gafas"
            }, style = MaterialTheme.typography.bodySmall)
            Text(when { downloading -> download.status; installed -> "Modelo español instalado"; download.invalid -> "Modelo no válido: requiere reparación"; else -> "Modelo español no instalado" }, style = MaterialTheme.typography.bodySmall)
            if (problem.isNotBlank() && phase == ListeningPhase.SUSPENDED) Text(problem, color = MaterialTheme.colorScheme.error)
            if (phase == ListeningPhase.CONVERSATION) Text(recognition, style = MaterialTheme.typography.bodySmall)
            if (phase == ListeningPhase.LISTENING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (compact) "Activación local · sin enviar audio de fondo." else "Escucha continua local en español, con notificación y parada explícita. Puede aumentar el consumo de batería; duración y acústica pendientes de validar.")
            if (!installed || downloading) {
                if (downloading) {
                    if (download.total > 0) LinearProgressIndicator(progress = { (download.received.toFloat() / download.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${download.received / 1048576} MiB recibidos" + if (download.total > 0) " de ${download.total / 1048576} MiB" else "")
                    OutlinedButton(onClick = app.wakeDownload::cancel) { Text("Cancelar descarga") }
                } else Button(onClick = { downloadConsent = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (download.invalid) "Reparar reconocimiento de voz en español" else "Descargar reconocimiento de voz en español")
                }
            }
            if (download.status.isNotBlank() && !downloading) Text(download.status)
            if (!compact) {
                Text("Dictado en español de España", style = MaterialTheme.typography.titleMedium)
                listOf(RecognitionMode.PRECISION to "Máxima precisión", RecognitionMode.SPEED to "Máxima rapidez").forEach { (value, label) ->
                    TextButton(onClick = { settings.recognitionMode(value); mode = value }, enabled = !ContinuousWakeService.running) { Text(if (mode == value) "● $label" else label) }
                }
                Text("Precisión: pausas de 1,7–2 s y Whisper large-v3 si autorizas Groq. Rapidez: 1 s y Whisper turbo. La calidad real depende del micrófono y requiere comparación; Vosk sigue siendo español genérico.", style = MaterialTheme.typography.bodySmall)
                listOf(DictationSource.AUTO to "Automático", DictationSource.VOSK to "Vosk español · sin enviar audio",
                    DictationSource.ANDROID_LOCAL to "Android local · es-ES", DictationSource.GROQ_WHISPER to "Groq Whisper · requiere autorizar audio").forEach { (value, label) ->
                    TextButton(onClick = { settings.dictationSource(value); source = value }, enabled = !ContinuousWakeService.running) { Text(if (source == value) "● $label" else label) }
                }
                Text("La selección se aplica también a conversación continua. Android local puede no disponer del idioma: se informa del error sin utilizar reconocimiento de red. Los cambios requieren detener la escucha.", style = MaterialTheme.typography.bodySmall)
            }
            if (running) Button(onClick = { ContinuousWakeService.stop(context) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Desactivar escucha permanente") }
            if (!running || phase == ListeningPhase.SUSPENDED) Button(onClick = {
                if (!microphoneReady) requestMic()
                else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { backgroundConsent = ListeningSettings(context).load().background; consent = true }
            }, enabled = installed && configured && !downloading, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Activar escucha permanente") }
            if (!configured) Text("Guarda un asistente y configura el proveedor elegido antes de activar la escucha.")
            if (!compact && installed && !ContinuousWakeService.running) TextButton(onClick = { app.voskModels.close(); app.wakeDownload.delete(); status = "Modelo eliminado del teléfono." }) { Text("Eliminar modelo de activación") }
            if (compact) TextButton(onClick = configure) { Text("Frase, segundo plano y reconocimiento") }
            if (status.isNotBlank()) Text(status)
            if (!compact) TextButton(onClick = {
                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"))) }
                catch (_: android.content.ActivityNotFoundException) { status = "Abre los ajustes de PbtMetaGPT desde Android para revisar permisos y batería." }
            }) { Text("Ajustes Android de permisos y batería") }
        }
    }
    if (downloadConsent) AlertDialog(onDismissRequest = { downloadConsent = false }, title = { Text("Descargar reconocimiento español") },
        text = { Text("Vosk small-es-0.42: unos 39 MB desde alphacephei.com, licencia Apache 2.0. Necesita 320 MiB libres durante la instalación. Se verificará SHA-256 y podrás cancelar. La descarga no activa el micrófono ni envía tu voz.") },
        confirmButton = { TextButton(onClick = { downloadConsent = false; app.wakeDownload.install() }) { Text("Descargar e instalar") } },
        dismissButton = { TextButton(onClick = { downloadConsent = false }) { Text("Cancelar") } })
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Autorizar escucha local") },
        text = { Column {
            Row { Checkbox(checked = backgroundConsent, onCheckedChange = { backgroundConsent = it }); Text("Continuar con el teléfono bloqueado") }
            Text((if (backgroundConsent)
            "Has habilitado segundo plano: el micrófono seguirá activo al minimizar y bloquear el móvil mientras Android lo permita. "
            else "Segundo plano desactivado: la escucha se detendrá al salir de la aplicación y la conversación quedará pausada. ") +
            "Una frase guardada abrirá el proveedor elegido: GroqCloud Free utiliza cuota gratuita; OpenAI API opcional es facturable. Configura el servicio y su consentimiento antes. Podrás detenerlo desde Inicio y la notificación. No se guardan grabaciones de fondo. Las frases deben estar guardadas y ser del idioma español. Al detener el servicio no se reinicia solo.") } },
        confirmButton = { TextButton(onClick = {
            consent = false
            ListeningSettings(context).let { it.save(it.load().copy(background = backgroundConsent)) }
            stopVisible()
            try { ContinuousWakeService.startVisible(context) }
            catch (_: SecurityException) { status = "Android ha denegado el servicio. Revisa permiso de micrófono y notificaciones." }
            catch (_: IllegalStateException) { status = "Android no permite iniciar el servicio ahora. Vuelve a Inicio e inténtalo con la aplicación visible." }
        }) { Text("Autorizar y activar") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancelar") } })
}
