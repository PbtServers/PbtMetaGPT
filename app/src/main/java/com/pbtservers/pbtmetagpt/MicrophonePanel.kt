package com.pbtservers.pbtmetagpt

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun MicrophonePanel(context: Context, local: LocalAudioDiagnostics, allowed: Boolean, request: () -> Unit, stop: () -> Unit) {
    val settings = remember { AudioInputSettings(context) }
    var choice by remember { mutableStateOf(settings.choice) }
    var selectedName by remember { mutableStateOf(settings.externalName) }
    var external by remember { mutableStateOf(settings.externalDevices()) }
    val input by (context.applicationContext as PbtApplication).audioSession.input.collectAsStateWithLifecycle()
    val test by local.microphoneTest.collectAsStateWithLifecycle()
    val busy by local.busy.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        val manager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { external = settings.externalDevices() }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { external = settings.externalDevices() }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterAudioDeviceCallback(callback); local.stop() }
    }
    fun select(value: MicrophoneChoice, name: String = "") {
        stop(); settings.save(value, name); choice = value; selectedName = name
    }
    Text("Micrófono", style = MaterialTheme.typography.titleLarge)
    Text("Selecciona la entrada antes de activar escucha. Cambiarla detiene la captura; vuelve a activarla expresamente.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(choice == MicrophoneChoice.AUTOMATIC, onClick = { select(MicrophoneChoice.AUTOMATIC) }, label = { Text("Automático") })
        FilterChip(choice == MicrophoneChoice.PHONE, onClick = { select(MicrophoneChoice.PHONE) }, label = { Text("Teléfono") })
    }
    if (external.isEmpty()) Text("Ray-Ban Meta: sin ruta HFP/LE disponible. Comprueba Bluetooth y el audio de llamadas de las gafas en Android. DAT conectado no acredita micrófono.")
    external.forEach { device ->
        FilterChip(choice == MicrophoneChoice.GLASSES && selectedName == device.productName.toString(),
            onClick = { select(MicrophoneChoice.GLASSES, device.productName.toString()) },
            label = { Text("Ray-Ban Meta / Bluetooth · ${device.productName}") })
    }
    Text("Identifica en la lista el nombre que Android asigna a tus gafas. Una ruta Bluetooth también puede corresponder a otro auricular.", style = MaterialTheme.typography.bodySmall)
    Text("Entrada activa: ${input.description}${if (input.sampleRate > 0) " · ${input.sampleRate} Hz" else ""}")
    Button(onClick = {
        if (!allowed) request()
        else { stop(); local.microphone(choice == MicrophoneChoice.GLASSES) }
    }, enabled = !busy && (choice != MicrophoneChoice.GLASSES || external.isNotEmpty())) {
        Text(if (choice == MicrophoneChoice.GLASSES) "Probar micrófono de las gafas" else "Probar micrófono seleccionado")
    }
    if (busy) TextButton(onClick = local::stop) { Text("Detener prueba de micrófono") }
    LinearProgressIndicator(progress = { test.level }, modifier = Modifier.fillMaxWidth())
    Text(test.status)
    Text(test.route, style = MaterialTheme.typography.bodySmall)
    if (test.transcript.isNotBlank()) Text("Transcripción local: ${test.transcript}")
}
