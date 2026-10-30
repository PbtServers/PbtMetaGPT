package com.pbtservers.pbtmetagpt

import android.Manifest
import android.bluetooth.BluetoothManager
import android.media.AudioManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        val configuration = android.content.res.Configuration(newBase.resources.configuration)
        configuration.setLocales(android.os.LocaleList.forLanguageTags("es-ES"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    private var message by mutableStateOf<String?>(null)
    private lateinit var glasses: MetaGlasses
    private lateinit var voice: RealtimeClient
    private val app get() = application as PbtApplication
    private val diagnostics get() = app.diagnostics
    private lateinit var localAudio: LocalAudioDiagnostics
    private lateinit var wake: LocalWakeListener
    private lateinit var vault: KeyVault
    private lateinit var photoMemory: PhotoMemory
    private var wakeStart: (AssistantProfile, String) -> Unit = { _, _ -> }
    private var microphoneReady by mutableStateOf(false)
    private var bluetoothReady by mutableStateOf(false)
    private var foreground = false

    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothReady = granted
        refreshDiagnostics()
        if (granted) glasses.initialize() else message = "Sin permiso Bluetooth no se pueden buscar ni conectar las gafas. Puedes conversar con el audio del teléfono."
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneReady = granted
        refreshDiagnostics()
        message = if (granted) "Micrófono autorizado. Pulsa Activar escucha o Iniciar conversación cuando quieras." else "El permiso de micrófono es necesario para conversar por voz."
    }
    private val cameraPermission = registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        diagnostics.record(Check.CAMERA_PERMISSION, if (result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted) CheckStatus.OK else CheckStatus.WARNING,
            "Resultado recibido del permiso de cámara de Meta AI.", if (result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted) "GRANTED" else "DENIED")
        message = if (result.getOrDefault(PermissionStatus.Denied) == PermissionStatus.Granted)
            "Cámara autorizada. Conecta las gafas si es necesario y vuelve a pulsar Capturar foto."
        else "Meta no ha concedido acceso a la cámara. No se ha capturado ninguna imagen."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cacheDir.listFiles().orEmpty().filter { it.name.startsWith("voz-") && it.name.endsWith(".wav") }.forEach { it.delete() }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        photoMemory = app.photoMemory
        glasses = app.glasses
        voice = app.voice
        lifecycleScope.launch { app.message.collect { text ->
            if (text != null) { message = text; if (photoMemory.bitmap != null) photoMemory.error = text; app.clearMessage() }
        } }
        localAudio = LocalAudioDiagnostics(this, lifecycleScope, diagnostics)
        vault = KeyVault(this)
        wake = LocalWakeListener(this, lifecycleScope) { profile, question -> if (foreground) wakeStart(profile, question) }
        // DAT pertenece al proceso autorizado; ocultar la UI solo cancela la cámara.
        microphoneReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        bluetoothReady = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (bluetoothReady) glasses.initialize()
        setContent {
            val themePreferences = remember { getSharedPreferences("configuracion", MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(themePreferences.getString("tema", "sistema").orEmpty()) }
            val darkSystem = androidx.compose.foundation.isSystemInDarkTheme()
            val darkBars = when (themeMode) { "oscuro" -> true; "claro" -> false; else -> darkSystem }
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkBars
                    isAppearanceLightNavigationBars = !darkBars
                }
            }
            PbtTheme(themeMode) {
                val gs by glasses.state.collectAsStateWithLifecycle()
                val vs by voice.state.collectAsStateWithLifecycle()
                val voiceCommands by voice.commandStatus.collectAsStateWithLifecycle()
                val freeVoice by app.hosted.state.collectAsStateWithLifecycle()
                val groqSelected = HostedSettings(this).provider == "groq"
                var captureConsent by remember { mutableStateOf(false) }
                var deviceAdvanced by remember { mutableStateOf(false) }
                val localBusy by localAudio.busy.collectAsStateWithLifecycle()
                AssistantShell(this, vault, voice, wake, microphoneReady,
                    requestMic = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                    stopAll = { ContinuousWakeService.stop(this); wake.stop(); voice.stop(); app.hosted.stop(); localAudio.stop() }, report = { message = it }, bindWakeStart = { wakeStart = it },
                    themeMode = themeMode, setTheme = { themeMode = it; themePreferences.edit().putString("tema", it).apply() },
                    deviceSummary = if (gs.connected) "Sesión DAT activa" else "Sesión DAT sin conectar") { page, configured, backend, token, bluetooth, setBluetooth, start, analyzePhoto ->
                if (page == "Diagnóstico") DiagnosticScreen(diagnostics, voice, localAudio,
                    configured = configured, backend = backend, token = token, bluetooth = bluetooth,
                    microphoneReady = microphoneReady, refresh = ::refreshDiagnostics,
                    requestMic = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                    checkCamera = ::checkCameraPermission, onBack = { onBackPressedDispatcher.onBackPressed() })
                else Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("PBT / ASISTENTE PERSONAL", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text(if (page == "Inicio") "Tu asistente, a tu manera" else page, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(page, style = MaterialTheme.typography.titleMedium)
                        Text("Conversa con el proveedor que elijas. Análisis de fotos mediante Groq Vision u OpenAI API, siempre con confirmación. Gafas pendientes de validación física.")
                        if (page == "Inicio" || page == "Dispositivos") Panel("01 · Tus gafas", "Gafas Meta Wearables") {
                            Text(gs.status, color = MaterialTheme.colorScheme.primary)
                            Text(if (gs.registered) "Aplicación registrada en Meta AI" else "Aplicación sin registrar en Meta AI")
                            Text(if (gs.devices.isEmpty()) "No hay dispositivos DAT disponibles." else "Dispositivos detectados por DAT: ${gs.devices.size}")
                            gs.devices.forEach { device ->
                                ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                                    if (deviceAdvanced) Text("Modelo informado por DAT: ${device.model}")
                                    Text(device.status)
                                    Text(if (gs.connected && gs.selectedDevice == device.id) "Sesión conectada y confirmada por DAT" else "Sin sesión activa en estas gafas")
                                    if (deviceAdvanced) {
                                    if (device.type == com.meta.wearable.dat.core.types.DeviceType.META_GLASSES) Text("El nombre visible, incluido «00R6», no identifica por sí solo el modelo comercial Adventurer.", style = MaterialTheme.typography.bodySmall)
                                    Text(if (device.displayCapable) "DAT declara capacidad de pantalla; esta app no envía contenido a ella." else "DAT no declara capacidad de pantalla.", style = MaterialTheme.typography.bodySmall)
                                    Text("Cámara: se comprueba al solicitar una captura. Micrófono y altavoces: validación física pendiente.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (!gs.connected && !gs.connecting) OutlinedButton(onClick = { glasses.connect(device.id) }, enabled = gs.registered && device.canConnect) { Text("Conectar estas gafas Meta") }
                                } }
                            }
                            if (!bluetoothReady) Button(onClick = { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Permitir Bluetooth") }
                            else if (!gs.registered) Button(onClick = { glasses.register(this@MainActivity) }, enabled = gs.initialized && !gs.registering) { Text(if (gs.registering) "Esperando a Meta AI…" else "Registrar con Meta AI") }
                            else {
                                Button(onClick = { if (gs.connected || gs.connecting) { voice.stop(); glasses.disconnect() } else glasses.connect() }) {
                                    Text(if (gs.connected || gs.connecting) "Desconectar gafas" else "Conectar gafas Meta")
                                }
                                if (deviceAdvanced) TextButton(onClick = { voice.stop(); glasses.unregister(this@MainActivity) }) { Text("Retirar registro de Meta AI") }
                            }
                            if (deviceAdvanced) Text("Necesitas Meta AI, gafas emparejadas y modo de desarrollador o una aplicación autorizada por Meta.", style = MaterialTheme.typography.bodySmall)
                            if (deviceAdvanced) Text("Este paso es opcional. Si Meta AI no abre el enlace, activa de nuevo el modo de desarrollador con las gafas conectadas y comprueba que DAT esté instalado en ellas. Actualiza y reinicia Meta AI antes de reintentar. El registro no borra el Setup.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (page == "Dispositivos") {
                            Panel("Audio", "Móvil y gafas") {
                                Text("El emparejamiento Bluetooth y una sesión DAT son conexiones distintas. La ruta de audio debe probarse por separado.")
                                MicrophonePanel(this@MainActivity, localAudio, microphoneReady,
                                    request = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                                    stop = { ContinuousWakeService.stop(this@MainActivity); wake.stop(); voice.stop(); app.hosted.stop(); localAudio.stop() })
                                Text("Entrada y salida comparten la ruta de comunicación Android. Automático prefiere HFP/LE disponible; la entrada activa se comprueba durante la captura.")
                                Button(onClick = {
                                    if (bluetooth && !bluetoothReady) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    else { voice.stop(); app.hosted.stop(); localAudio.speaker(bluetooth) }
                                }, enabled = !localBusy) { Text("Probar sonido") }
                                if (localBusy) OutlinedButton(onClick = localAudio::stop) { Text("Detener sonido") }
                                MusicPanel(this@MainActivity)
                                TextButton(onClick = { deviceAdvanced = !deviceAdvanced }) { Text(if (deviceAdvanced) "Cerrar opciones de gafas" else "Opciones avanzadas de gafas") }
                            }
                        }
                        if (page == "Inicio" || page == "Conversación") Panel("02 · Conversación", "OpenAI API · integración opcional") {
                            Text(vs.status, color = MaterialTheme.colorScheme.primary)
                            Text(vs.route, style = MaterialTheme.typography.bodySmall)
                            if (vs.phoneFallbackAvailable) OutlinedButton(onClick = { ContinuousWakeService.stop(this@MainActivity, finishConversation = false); AudioInputSettings(this@MainActivity).save(MicrophoneChoice.PHONE); setBluetooth(false); if (vs.active && vs.paused) voice.resumeOnPhone() else start() }) { Text("Usar micrófono y altavoz del móvil") }
                            if (!vs.active && !vs.connecting) {
                                Text("Selecciona tu asistente y configura la conexión en OpenAI. El modo directo no necesita backend.")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Preferir audio Bluetooth", modifier = Modifier.weight(1f).padding(top = 12.dp))
                                    Switch(checked = bluetooth, onCheckedChange = setBluetooth)
                                }
                                Text("La selección de Dispositivos → Micrófono tiene prioridad. Si solicitas gafas y no hay ruta compatible se pausa; selecciona Teléfono expresamente para cambiar.", style = MaterialTheme.typography.bodySmall)
                                if (!microphoneReady) Button(onClick = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Permitir micrófono") }
                                else Button(onClick = {
                                    if (bluetooth && !bluetoothReady) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    else start()
                                }, enabled = configured && !localBusy) { Text("Iniciar conversación") }
                            }
                            if (vs.active && vs.imageAnalysis) Button(onClick = { voice.stop(); start() }) { Text("Finalizar análisis y hablar") }
                            if (vs.active && !vs.imageAnalysis) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = voice::pauseOrResume) { Text(if (vs.paused) "Reanudar" else "Pausar") }
                                    Button(onClick = voice::interrupt, enabled = vs.responding, modifier = Modifier.heightIn(min = 64.dp)) { Text("Parar") }
                                }
                            }
                            if (vs.active || vs.connecting) OutlinedButton(onClick = voice::stop) { Text(if (vs.connecting) "Cancelar conexión" else "Finalizar conversación") }
                            if (voiceCommands.isNotBlank()) Text(voiceCommands, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = app::openChatGpt) { Text("Abrir ChatGPT") }
                            if (vs.transcript.isNotBlank()) Text(vs.transcript, style = MaterialTheme.typography.bodyLarge)
                            Text("La pausa conserva el contexto. Con escucha persistente autorizada, el servicio mantiene el control del audio al salir; puedes detenerlo desde su notificación.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (page == "Inicio" || page == "Dispositivos" && deviceAdvanced) Panel("03 · Visión", "¿Qué estoy viendo?") {
                            Text(if (gs.capturing) "Cámara activa · capturando una foto…" else "Cámara apagada", color = MaterialTheme.colorScheme.primary)
                            Text("La captura se muestra aquí antes de enviarla. No se sube vídeo ni se guardan fotos en la galería.")
                            Text(when (gs.cameraAvailable) { true -> "DAT permite cámara en esta sesión; calidad física sin validar"; false -> "DAT no pudo habilitar cámara. Revisa permisos y compatibilidad antes de reconectar."; null -> "Capacidad de cámara sin comprobar en esta sesión" })
                            Button(onClick = { captureConsent = true }, enabled = gs.connected && gs.cameraAvailable != false && !gs.capturing && gs.photo == null) { Text("Capturar foto con las gafas") }
                            if (gs.capturing) OutlinedButton(onClick = glasses::stopCapture) { Text("Cancelar captura") }
                            PhotoAnalysisPanel(gs.photo, configured, if (groqSelected) freeVoice else vs, photoMemory.error,
                                provider = if (groqSelected) "groq" else "openai",
                                onConfirm = { photoMemory.error = null; analyzePhoto(it) }, discard = glasses::discardPhoto,
                                resume = voice::pauseOrResume, cancel = { if (groqSelected) app.hosted.stop() else voice.stop() })
                        }
                        Text("Privacidad bajo tu control", style = MaterialTheme.typography.titleMedium)
                        Text("Las fotos se envían únicamente al proveedor seleccionado, Groq Vision u OpenAI, tras confirmación explícita. Los límites del plan gratuito de Groq se aplican también a imágenes. Pendiente de pruebas con gafas reales.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
                if (captureConsent) AlertDialog(onDismissRequest = { captureConsent = false }, title = { Text("Autorizar captura") },
                    text = { Text("Se abrirá brevemente la cámara de las gafas para una única foto. Revísala antes de autorizar su envío al proveedor de IA seleccionado.") },
                    confirmButton = { TextButton(onClick = {
                        captureConsent = false
                        lifecycleScope.launch {
                            Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { status ->
                                if (status == PermissionStatus.Granted && foreground) glasses.capture()
                                else if (foreground) cameraPermission.launch(Permission.CAMERA)
                            }.onFailure { _, _ -> message = "No se pudo comprobar el permiso de cámara en Meta AI." }
                        }
                    }) { Text("Autorizar") } }, dismissButton = { TextButton(onClick = { captureConsent = false }) { Text("Cancelar") } })
                message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Información") }, text = { Text(text) },
                    confirmButton = { TextButton(onClick = { message = null }) { Text("Entendido") } }) }
            }
        }
    }
    private fun checkCameraPermission() {
        if (!glasses.state.value.initialized || !glasses.state.value.registered) {
            diagnostics.record(Check.CAMERA_PERMISSION, CheckStatus.WARNING, "Primero permite Bluetooth e inicia el registro en Meta AI.", "REGISTRATION_REQUIRED")
            return
        }
        lifecycleScope.launch {
            Wearables.checkPermissionStatus(Permission.CAMERA).onSuccess { status ->
                diagnostics.record(Check.CAMERA_PERMISSION, if (status == PermissionStatus.Granted) CheckStatus.OK else CheckStatus.WARNING,
                    "Consulta del permiso DAT; no se ha abierto la cámara.", if (status == PermissionStatus.Granted) "GRANTED" else "DENIED")
            }.onFailure { cause, _ -> diagnostics.record(Check.CAMERA_PERMISSION, CheckStatus.ERROR, "No se pudo consultar el permiso de cámara.", Diagnostics.sdkCode(cause)) }
        }
    }
    private fun refreshDiagnostics() {
        microphoneReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        bluetoothReady = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        diagnostics.record(Check.PERMISSIONS, if (microphoneReady && bluetoothReady) CheckStatus.OK else CheckStatus.WARNING,
            "Micrófono Android: ${if (microphoneReady) "concedido" else "sin conceder"}. Bluetooth: ${if (bluetoothReady) "concedido" else "sin conceder"}. El permiso de cámara de las gafas se consulta por separado.")
        if (!bluetoothReady) diagnostics.record(Check.BLUETOOTH, CheckStatus.WARNING, "No se puede consultar el adaptador sin permiso Bluetooth.", "PERMISSION_REQUIRED")
        else runCatching {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            val enabled = adapter?.isEnabled == true
            diagnostics.record(Check.BLUETOOTH, if (enabled) CheckStatus.OK else CheckStatus.WARNING,
                if (adapter == null) "Este dispositivo no tiene adaptador Bluetooth." else if (enabled) "Bluetooth está activado. Esto no demuestra conexión DAT ni audio de las gafas." else "Bluetooth está desactivado.", if (enabled) "ENABLED" else "UNAVAILABLE")
        }.onFailure { diagnostics.record(Check.BLUETOOTH, CheckStatus.ERROR, "Android rechazó la consulta Bluetooth.", "BLUETOOTH_QUERY") }
        runCatching {
            val manager = getSystemService(AudioManager::class.java)
            val count = manager.availableCommunicationDevices.size
            diagnostics.record(Check.ROUTE, CheckStatus.WARNING, "Android informa de $count rutas de comunicación. Ruta actual (tipo Android): ${manager.communicationDevice?.type ?: "sin confirmar"}. No se ha activado audio para esta consulta.")
        }.onFailure { diagnostics.record(Check.ROUTE, CheckStatus.WARNING, "Android no permite consultar las rutas en este momento.", "ROUTE_QUERY") }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (DatRegistrationPolicy.callback(intent.action, intent.data?.scheme)) glasses.registrationReturn()
    }
    override fun onStart() { super.onStart(); foreground = true; app.activityVisible = true; app.stopVisibleWake = { wake.stop() }; refreshDiagnostics(); glasses.registrationReturn() }
    override fun onStop() {
        foreground = false; app.activityVisible = false; app.stopVisibleWake = null; wake.stop(); localAudio.stop()
        if (ContinuousWakeService.running && !ListeningSettings(this).load().background) ContinuousWakeService.stop(this, finishConversation = false)
        if (!ContinuousWakeService.running) {
            voice.suspendForBackground(); app.hosted.suspendConversation()
            if (!voice.state.value.active && !app.hosted.state.value.active) app.connection = null
        }
        glasses.stopCapture()
        if (!DatConnectionPolicy.retain(app.activityVisible, ContinuousWakeService.running, isChangingConfigurations)) glasses.disconnect()
        super.onStop()
    }
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
