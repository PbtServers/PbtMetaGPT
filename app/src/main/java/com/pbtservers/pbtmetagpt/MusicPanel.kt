package com.pbtservers.pbtmetagpt

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun MusicPanel(context: Context) {
    val settings = remember { MusicSettings(context) }
    val app = context.applicationContext as PbtApplication
    var enabled by remember { mutableStateOf(settings.enabled) }
    var message by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var authorized by remember { mutableStateOf(SpotifyControls.authorized(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) authorized = SpotifyControls.authorized(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun enable(value: Boolean) {
        ContinuousWakeService.stop(context)
        app.hosted.stop(); app.voice.stop()
        settings.enable(value); enabled = value
        message = "Preferencia guardada. Activa de nuevo la escucha para aplicar la convivencia con música."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Música · Spotify", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Controlar Spotify", modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { if (it) consent = true else enable(false) })
        }
        Text("Di «Atento Jarvis, reproduce música», «siguiente canción», «canción anterior» o «pausa la música». En espera, incluye tu frase de activación. Abre Spotify y reproduce una canción una vez antes de bloquear.")
        Text(if (authorized) "Acceso Android a sesiones: autorizado" else "Acceso Android a sesiones: sin autorizar")
        TextButton(onClick = {
            try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: android.content.ActivityNotFoundException) { message = "Abre Ajustes de Android → Acceso especial → Acceso a notificaciones y selecciona PbtMetaGPT · música." }
            catch (_: SecurityException) { message = "Android no permite abrir este ajuste. Revisa las restricciones y el acceso a notificaciones desde Ajustes del sistema." }
        }) { Text("Gestionar acceso de música en Android") }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { app.controlSpotify(MusicAction.PLAY) }) { Text("Reproducir") }
                TextButton(onClick = { app.controlSpotify(MusicAction.PAUSE) }) { Text("Pausar música") }
                TextButton(onClick = { app.controlSpotify(MusicAction.NEXT) }) { Text("Siguiente") }
            }
            Text("La captura Bluetooth HFP puede reducir la calidad de la música. Se comprueban entrada, salida y comandos con tus gafas; no se garantiza audio A2DP mientras se usa su micrófono.", style = MaterialTheme.typography.bodySmall)
        }
        if (message.isNotBlank()) Text(message)
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Autorizar controles de Spotify") },
        text = { Text("Android exige acceso especial a notificaciones para controlar una sesión de otra aplicación. Es una autorización amplia del sistema: PbtMetaGPT no procesa ni guarda el contenido de las notificaciones; solo solicita la sesión de Spotify al recibir una orden. Se detendrá la escucha actual para cambiar el modo de audio; actívala de nuevo después de autorizar. Puedes desactivar esta función y revocar el acceso en Android cuando quieras.") },
        confirmButton = { TextButton(onClick = { consent = false; enable(true) }) { Text("Activar controles") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancelar") } })
}
