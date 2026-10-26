package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var message by mutableStateOf<String?>(null)
    private lateinit var glasses: MetaGlasses
    private lateinit var voice: RealtimeClient
    private var microphoneReady by mutableStateOf(false)
    private var bluetoothReady by mutableStateOf(false)
    private var foreground = false

    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothReady = granted
        if (granted) glasses.initialize() else message = "Sin permiso Bluetooth no se pueden buscar ni conectar las gafas. Puedes conversar con el audio del teléfono."
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneReady = granted
        message = if (granted) "Micrófono autorizado. Pulsa Iniciar conversación cuando quieras hablar." else "El permiso de micrófono es necesario para conversar por voz."
    }
    private val cameraPermission = registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        message = if (result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted)
            "Cámara autorizada. Conecta las gafas si es necesario y vuelve a pulsar Capturar foto."
        else "Meta no ha concedido acceso a la cámara. No se ha capturado ninguna imagen."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        glasses = MetaGlasses(this, lifecycleScope) { message = it }
        voice = RealtimeClient(this, lifecycleScope) { message = it }
        lifecycleScope.launch {
            var previouslyConnected = false
            glasses.state.collect { state ->
                if (previouslyConnected && !state.connected) voice.stop()
                previouslyConnected = state.connected
            }
        }
        microphoneReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        bluetoothReady = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (bluetoothReady) glasses.initialize()
        val preferences = getSharedPreferences("configuracion", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9DE9D0), onPrimary = Color(0xFF00382B),
                secondary = Color(0xFFB6C5FF), background = Color(0xFF0D141D), surface = Color(0xFF17212E))) {
                val gs by glasses.state.collectAsStateWithLifecycle()
                val vs by voice.state.collectAsStateWithLifecycle()
                var backend by remember { mutableStateOf(preferences.getString("backend", "").orEmpty()) }
                var token by remember { mutableStateOf("") }
                var bluetooth by remember { mutableStateOf(false) }
                var captureConsent by remember { mutableStateOf(false) }
                var sendConsent by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF152B38), Color(0xFF0D141D))))
                        .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("PBT / ASISTENTE PERSONAL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text("Tu voz.\nOtra perspectiva.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("PbtMetaGPT · 0.2.0", style = MaterialTheme.typography.titleMedium)
                        Text("Habla con ChatGPT y comparte una foto cuando tú decidas. Integración con gafas pendiente de validación física.")
                        Panel("01 · Tus gafas", "Ray-Ban Meta") {
                            Text(gs.status, color = MaterialTheme.colorScheme.primary)
                            Text(if (gs.registered) "Aplicación registrada en Meta AI" else "Aplicación sin registrar en Meta AI")
                            Text(if (gs.devices.isEmpty()) "No hay dispositivos DAT disponibles." else "Dispositivos detectados por DAT: ${gs.devices.size}")
                            gs.devices.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (!bluetoothReady) Button(onClick = { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Permitir Bluetooth") }
                            else if (!gs.registered) Button(onClick = glasses::register, enabled = gs.initialized) { Text("Registrar con Meta AI") }
                            else {
                                Button(onClick = { if (gs.connected || gs.connecting) { voice.stop(); glasses.disconnect() } else glasses.connect() }) {
                                    Text(if (gs.connected || gs.connecting) "Desconectar gafas" else "Conectar gafas")
                                }
                                TextButton(onClick = { voice.stop(); glasses.unregister() }) { Text("Retirar registro de Meta AI") }
                            }
                            Text("Necesitas Meta AI, gafas emparejadas y modo de desarrollador o una aplicación autorizada por Meta.", style = MaterialTheme.typography.bodySmall)
                        }
                        Panel("02 · Conversación", "Habla con ChatGPT") {
                            Text(vs.status, color = MaterialTheme.colorScheme.primary)
                            Text(vs.route, style = MaterialTheme.typography.bodySmall)
                            if (!vs.active && !vs.connecting) {
                                OutlinedTextField(backend, { backend = it }, label = { Text("Dirección HTTPS de tu backend") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                                OutlinedTextField(token, { token = it }, label = { Text("Token personal del backend") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                                Text("No introduzcas una clave API de OpenAI. El token personal solo se conserva en memoria.", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Preferir audio Bluetooth", modifier = Modifier.weight(1f).padding(top = 12.dp))
                                    Switch(checked = bluetooth, onCheckedChange = { bluetooth = it })
                                }
                                Text("Sin ruta Bluetooth de comunicación se utilizará el teléfono. Comprueba la ruta indicada; el audio de las gafas depende del firmware y de Android.", style = MaterialTheme.typography.bodySmall)
                                if (!microphoneReady) Button(onClick = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Permitir micrófono") }
                                else Button(onClick = {
                                    preferences.edit().putString("backend", backend).apply()
                                    if (bluetooth && !bluetoothReady) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    else voice.start(backend, token.trim(), bluetooth)
                                }, enabled = backend.isNotBlank() && token.isNotBlank()) { Text("Iniciar conversación") }
                            }
                            if (vs.active) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = voice::pauseOrResume) { Text(if (vs.paused) "Reanudar" else "Pausar") }
                                    OutlinedButton(onClick = voice::interrupt, enabled = vs.responding) { Text("Interrumpir") }
                                }
                            }
                            if (vs.active || vs.connecting) OutlinedButton(onClick = voice::stop) { Text(if (vs.connecting) "Cancelar conexión" else "Finalizar conversación") }
                            if (vs.transcript.isNotBlank()) Text(vs.transcript, style = MaterialTheme.typography.bodyLarge)
                            Text("La pausa conserva el contexto. Finalizar o salir cierra la sesión. No hay escucha en segundo plano.", style = MaterialTheme.typography.bodySmall)
                        }
                        Panel("03 · Visión", "¿Qué estoy viendo?") {
                            Text(if (gs.capturing) "Cámara activa · capturando una foto…" else "Cámara apagada", color = MaterialTheme.colorScheme.primary)
                            Text("La captura se muestra aquí antes de enviarla. No se sube vídeo ni se guardan fotos en la galería.")
                            Button(onClick = { captureConsent = true }, enabled = gs.connected && !gs.capturing && gs.photo == null) { Text("Capturar foto con las gafas") }
                            if (gs.capturing) OutlinedButton(onClick = glasses::stopCapture) { Text("Cancelar captura") }
                            gs.photo?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), contentDescription = "Vista previa local de la foto capturada; todavía no enviada", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
                                Button(onClick = { sendConsent = true }, enabled = vs.active && !vs.responding && !vs.sendingImage) {
                                    Text(if (vs.sendingImage) "Enviando…" else "Preguntar a ChatGPT sobre esta foto")
                                }
                                OutlinedButton(onClick = glasses::discardPhoto, enabled = !vs.sendingImage) { Text("Descartar foto") }
                                if (!vs.active) Text("Inicia una conversación para obtener una respuesta hablada.")
                            }
                        }
                        Text("Privacidad bajo tu control", style = MaterialTheme.typography.titleMedium)
                        Text("El audio y las fotos que autorices se envían a OpenAI. La suscripción ChatGPT y la facturación de la API son independientes. Versión pendiente de pruebas con gafas reales.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (captureConsent) AlertDialog(onDismissRequest = { captureConsent = false }, title = { Text("Autorizar captura") },
                    text = { Text("Se abrirá brevemente la cámara de las gafas para una única foto. Revísala antes de autorizar su envío a OpenAI.") },
                    confirmButton = { TextButton(onClick = {
                        captureConsent = false
                        lifecycleScope.launch {
                            Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { status ->
                                if (status == PermissionStatus.Granted && foreground) glasses.capture()
                                else if (foreground) cameraPermission.launch(Permission.CAMERA)
                            }.onFailure { _, _ -> message = "No se pudo comprobar el permiso de cámara en Meta AI." }
                        }
                    }) { Text("Autorizar") } }, dismissButton = { TextButton(onClick = { captureConsent = false }) { Text("Cancelar") } })
                if (sendConsent && gs.photo != null) AlertDialog(onDismissRequest = { sendConsent = false }, title = { Text("Enviar esta foto a OpenAI") },
                    text = { Text("ChatGPT recibirá la imagen y responderá en voz alta a «¿Qué estoy viendo?». Comprueba que no contiene información que no quieras compartir.") },
                    confirmButton = { TextButton(onClick = { sendConsent = false; gs.photo?.let { voice.askImage(it, glasses::discardPhoto) } }) { Text("Enviar y preguntar") } },
                    dismissButton = { TextButton(onClick = { sendConsent = false }) { Text("Cancelar") } })
                message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Información") }, text = { Text(text) },
                    confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } }) }
            }
        }
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onStop() { foreground = false; voice.stop(); glasses.stopCapture(); glasses.disconnect(); super.onStop() }
}

@Composable
private fun Panel(eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(eyebrow, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
