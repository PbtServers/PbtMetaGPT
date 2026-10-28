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
    val model = remember { WakeModel(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(model.installed()) }
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Notificaciones autorizadas. Pulsa Activar escucha para continuar." else "Activa las notificaciones para disponer del control de parada fuera de la aplicación."
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu voz abre la conversación", style = MaterialTheme.typography.titleLarge)
            Text(phase.label, color = MaterialTheme.colorScheme.primary)
            if (installed && !compact) Text("Modelo español Vosk 0.42 instalado · reconocimiento acústico pendiente de probar", style = MaterialTheme.typography.bodySmall)
            if (phase == ListeningPhase.LISTENING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (compact) "Activación local · sin enviar audio de fondo." else "Escucha continua local en español, con notificación y parada explícita. Puede aumentar el consumo de batería; duración y acústica pendientes de validar.")
            if (compact && !installed) TextButton(onClick = configure) { Text("Preparar activación local") }
            if (!installed && !compact) {
                Text("Necesita Vosk small-es-0.42 (39 MB, Apache 2.0), descargado desde alphacephei.com. Los nombres fuera del vocabulario requieren adaptar un modelo; no se garantiza cualquier frase.")
                Button(onClick = { scope.launch {
                    downloading = true
                    try { withContext(Dispatchers.IO) { model.install() }; installed = model.installed(); status = "Modelo instalado. La descarga no activa el micrófono." }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: IOException) { status = "No se pudo descargar el modelo. Comprueba la conexión y reintenta." }
                    catch (_: IllegalArgumentException) { status = "El archivo descargado no cumple el formato esperado." }
                    catch (_: IllegalStateException) { status = "No se pudo instalar el modelo. Comprueba el espacio disponible." }
                    finally { downloading = false }
                } }, enabled = !downloading) { Text(if (downloading) "Descargando modelo…" else "Descargar modelo español") }
            }
            if (ContinuousWakeService.running) Button(onClick = { ContinuousWakeService.stop(context) }) { Text("Desactivar escucha") }
            if (!ContinuousWakeService.running || phase == ListeningPhase.SUSPENDED) Button(onClick = {
                if (!microphoneReady) requestMic()
                else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else consent = true
            }, enabled = installed && configured && !downloading) { Text("Activar escucha") }
            if (!configured) Text("Guarda un asistente y configura el proveedor elegido antes de activar la escucha.")
            if (!compact && installed && !ContinuousWakeService.running) TextButton(onClick = { model.delete(); installed = false; status = "Modelo eliminado del teléfono." }) { Text("Eliminar modelo de activación") }
            if (status.isNotBlank()) Text(status)
            if (!compact) TextButton(onClick = {
                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"))) }
                catch (_: android.content.ActivityNotFoundException) { status = "Abre los ajustes de PbtMetaGPT desde Android para revisar permisos y batería." }
            }) { Text("Ajustes Android de permisos y batería") }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Autorizar escucha persistente") },
        text = { Text("El micrófono seguirá activo al minimizar y bloquear el móvil mientras Android lo permita. Una frase guardada abrirá el proveedor elegido: GroqCloud Free utiliza cuota gratuita; OpenAI API opcional es facturable. Configura el servicio y su consentimiento antes. Podrás detenerlo desde Inicio y la notificación. No se guardan grabaciones de fondo. Las frases deben estar guardadas y ser del idioma español. Al detener el servicio no se reinicia solo.") },
        confirmButton = { TextButton(onClick = {
            consent = false; stopVisible()
            ListeningSettings(context).let { it.save(it.load().copy(background = true)) }
            try { ContinuousWakeService.startVisible(context) }
            catch (_: SecurityException) { status = "Android ha denegado el servicio. Revisa permiso de micrófono y notificaciones." }
            catch (_: IllegalStateException) { status = "Android no permite iniciar el servicio ahora. Vuelve a Inicio e inténtalo con la aplicación visible." }
        }) { Text("Autorizar y activar") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancelar") } })
}
