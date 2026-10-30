package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/** Se ejecuta en un segundo bloque de instrumentación con permisos concedidos por CI. */
class MicrophoneServiceInstrumentedTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as PbtApplication
    @After fun cleanup() = runBlocking<Unit> {
        instrumentation.runOnMainSync { ContinuousWakeService.stop(context); app.hosted.stop(); app.voice.stop() }
        await { !ContinuousWakeService.running && app.microphone.owner() == null }
        WakeModel(context).delete()
        ProfileStore(context).save(emptyList(), null)
        context.getSharedPreferences("voz_escucha", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("configuracion", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("medidas_escucha", Context.MODE_PRIVATE).edit().clear().commit()
        MusicSettings(context).enable(false)
    }
    @Test fun servicioCapturaEnOtraTareaYPantallaBloqueadaYNotificacionPausaYDetiene() = runBlocking<Unit> {
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        withContext(Dispatchers.IO) { WakeModel(context).install { instrumentation.context.assets.open("activacion-prueba.zip") } }
        val profile = AssistantProfile(name = "Jarvis").validated()
        ProfileStore(context).save(listOf(profile), profile.id)
        context.getSharedPreferences("configuracion", 0).edit().putBoolean("bienvenida", true).putBoolean("audio-bluetooth", false).commit()
        ListeningSettings(context).save(ListeningOptions(background = true))
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { ContinuousWakeService.startVisible(it) }
            await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
            assertEquals(MicrophoneOwner.CONTINUOUS_LOCAL, app.microphone.owner())
            activity.moveToState(Lifecycle.State.CREATED)
            shell("input keyevent KEYCODE_SLEEP")
            try {
                // Primera ventana completa ya en espera: CPU/PSS del emulador,
                // sin convertirlos en consumo o latencia del teléfono/gafas.
                withTimeout(70000) { while (ListeningMeasurements(context).report() == "[]") delay(100) }
                android.util.Log.i("PbtListenTest", "Servicio pantalla bloqueada: ${ListeningMeasurements(context).report()}")
                assertTrue(ContinuousWakeService.running)
                assertEquals(ListeningPhase.LISTENING, ContinuousWakeService.state.value)
                repeat(3) {
                    shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                    activity.moveToState(Lifecycle.State.RESUMED)
                    assertEquals(MicrophoneOwner.CONTINUOUS_LOCAL, app.microphone.owner())
                    activity.moveToState(Lifecycle.State.CREATED)
                    shell("input keyevent KEYCODE_SLEEP")
                    delay(300)
                    assertEquals(ListeningPhase.LISTENING, ContinuousWakeService.state.value)
                }
                val notifications = context.getSystemService(NotificationManager::class.java)
                val notification = notifications.activeNotifications.single { it.id == 41 }.notification
                assertTrue(notification.actions.any { it.title.toString() == "Pausar" })
                notification.actions.single { it.title.toString() == "Pausar" }.actionIntent.send()
                await { ContinuousWakeService.state.value == ListeningPhase.SUSPENDED && app.microphone.owner() == null }
                // La pausa no puede reanudar por sí sola al volver a la actividad.
                shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                activity.moveToState(Lifecycle.State.RESUMED)
                delay(300)
                assertNull(app.microphone.owner())
                activity.onActivity { ContinuousWakeService.startVisible(it) }
                await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
                notifications.activeNotifications.single { it.id == 41 }.notification.actions
                    .single { it.title.toString() == "Detener escucha y conversación" }.actionIntent.send()
                await { !ContinuousWakeService.running && app.microphone.owner() == null }
                // Sin autorización de segundo plano, cerrar la interfaz detiene el
                // servicio y volver no reactiva sensores. La conversación se pausa.
                ListeningSettings(context).save(ListeningOptions(background = false))
                activity.onActivity { it.setContent {
                    MaterialTheme { ListeningControls(configured = true, microphoneReady = true, requestMic = {}, stopVisible = {}, compact = true) }
                } }
                compose.onNodeWithText("Activar escucha").performClick()
                compose.onNodeWithText("Autorizar y activar").performClick()
                assertFalse("Activar escucha debe respetar segundo plano desactivado", ListeningSettings(context).load().background)
                await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
                activity.moveToState(Lifecycle.State.CREATED)
                await { !ContinuousWakeService.running && app.microphone.owner() == null }
                activity.moveToState(Lifecycle.State.RESUMED)
                delay(300)
                assertFalse(ContinuousWakeService.running); assertNull(app.microphone.owner())
            } finally { shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard") }
        }
    }
    @Test fun otraPeticionDeFocoPausaLaCapturaYNoReanudaAlDevolverElAudio() = runBlocking<Unit> {
        WakeModel(context).install { instrumentation.context.assets.open("activacion-prueba.zip") }
        val profile = AssistantProfile(name = "Jarvis").validated()
        ProfileStore(context).save(listOf(profile), profile.id)
        ListeningSettings(context).save(ListeningOptions(background = true))
        val manager = context.getSystemService(AudioManager::class.java)
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
            .setOnAudioFocusChangeListener({}, android.os.Handler(android.os.Looper.getMainLooper())).build()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { ContinuousWakeService.startVisible(it) }
            await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
            try {
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(other))
                await { ContinuousWakeService.state.value == ListeningPhase.SUSPENDED && app.microphone.owner() == null && !app.audioSession.hasOwners() }
                manager.abandonAudioFocusRequest(other)
                delay(300)
                assertEquals(ListeningPhase.SUSPENDED, ContinuousWakeService.state.value)
                assertNull(app.microphone.owner())
                activity.onActivity { ContinuousWakeService.startVisible(it) }
                await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
            } finally { manager.abandonAudioFocusRequest(other) }
        }
    }
    @Test fun esperaConMusicaCedeElFocoYLoRecuperaSoloDuranteUnTurno() = runBlocking<Unit> {
        WakeModel(context).install { instrumentation.context.assets.open("activacion-prueba.zip") }
        val profile = AssistantProfile(name = "Jarvis").validated()
        ProfileStore(context).save(listOf(profile), profile.id)
        ListeningSettings(context).save(ListeningOptions(background = true))
        MusicSettings(context).enable(true)
        val manager = context.getSystemService(AudioManager::class.java)
        val media = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener({}, android.os.Handler(android.os.Looper.getMainLooper())).build()
        val turn = AudioRoute(context) { fail("La ruta del turno se ha perdido") }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            try {
                activity.onActivity { ContinuousWakeService.startVisible(it) }
                await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
                assertFalse(app.audioSession.hasFocusOwners())
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(media))
                activity.moveToState(Lifecycle.State.CREATED)
                shell("input keyevent KEYCODE_SLEEP")
                delay(500)
                assertEquals(ListeningPhase.LISTENING, ContinuousWakeService.state.value)
                assertEquals(MicrophoneOwner.CONTINUOUS_LOCAL, app.microphone.owner())
                turn.start(false, true)
                assertTrue(app.audioSession.hasFocusOwners())
                turn.close()
                assertFalse(app.audioSession.hasFocusOwners())
                assertEquals(ListeningPhase.LISTENING, ContinuousWakeService.state.value)
            } finally {
                turn.close(); manager.abandonAudioFocusRequest(media); MusicSettings(context).enable(false)
                shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
            }
        }
    }
    @Test fun audioRecordYAudioTrackOperanSimultaneamenteYParadaManualLiberaAmbos() = runBlocking<Unit> {
        val gate = app.microphone
        val lease = requireNotNull(gate.acquire(MicrophoneOwner.COMMAND_LOCAL))
        val route = AudioRoute(context) { fail("La ruta del emulador se ha interrumpido") }
        val player = PcmPlayer()
        var recorder: AudioRecord? = null
        var playback: Job? = null
        var activity: ActivityScenario<MainActivity>? = null
        try {
            activity = ActivityScenario.launch(MainActivity::class.java)
            route.start(false, speaker = true)
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(6400, minimum)).also { recorder = it }
            assertEquals(AudioRecord.STATE_INITIALIZED, record.state)
            record.startRecording()
            val firstFrame = CompletableDeferred<Unit>()
            // Señal de prueba fija; no contiene voz ni conversaciones.
            val pcm = ByteArray(16000 * 2 * 2)
            repeat(pcm.size / 2) { index ->
                val sample = (kotlin.math.sin(index * 2.0 * Math.PI * 440 / 16000) * 500).toInt()
                pcm[index * 2] = sample.toByte(); pcm[index * 2 + 1] = (sample shr 8).toByte()
            }
            playback = launch { player.playStream(flowOf(PcmAudio(pcm, 16000)), firstFrame = { firstFrame.complete(Unit) }) }
            var read = 0
            withContext(Dispatchers.IO) {
                val buffer = ShortArray(320)
                withTimeout(5000) {
                    while (!firstFrame.isCompleted || read == 0) {
                        val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        check(n >= 0); read += n; buffer.fill(0); delay(10)
                    }
                }
            }
            assertTrue(read > 0)
            assertNotNull(record.routedDevice)
            player.stop(); playback.cancelAndJoin(); playback = null
            pcm.fill(0)
            // Se han leído tramas durante la salida real del emulador. No prueba acústica ni Bluetooth.
        } finally {
            withContext(NonCancellable) {
                player.stop(); playback?.cancelAndJoin()
                try { runCatching { recorder?.stop() }; recorder?.release() }
                finally { try { route.close() } finally { gate.release(lease); activity?.close() } }
            }
        }
        assertNull(gate.owner())
    }
    private suspend fun await(condition: () -> Boolean) { withTimeout(20000) { while (!condition()) delay(50) } }
    @Test fun seleccionPersistenteYRutaCompartidaHastaLiberarElUltimoPropietario() = runBlocking<Unit> {
        val settings = AudioInputSettings(context)
        val manager = context.getSystemService(AudioManager::class.java)
        settings.save(MicrophoneChoice.PHONE)
        assertEquals(MicrophoneChoice.PHONE, AudioInputSettings(context).choice)
        assertFalse(settings.useBluetooth(true))
        val pin = AudioRoute(context) { fail("Ruta del emulador interrumpida") }
        val turn = AudioRoute(context) { fail("Ruta del turno interrumpida") }
        ActivityScenario.launch(MainActivity::class.java).use {
            val previous = manager.mode
            try {
                pin.start(false, true)
                await { manager.communicationDevice != null }
                val device = manager.communicationDevice!!.id
                turn.start(false, true)
                turn.close()
                assertEquals(AudioManager.MODE_IN_COMMUNICATION, manager.mode)
                assertEquals(device, manager.communicationDevice!!.id)
                // La ausencia externa nunca se presenta como captura de gafas.
                if (settings.externalDevices().isEmpty()) {
                    pin.close()
                    settings.save(MicrophoneChoice.GLASSES, "Gafas de prueba no disponibles")
                    assertTrue(settings.useBluetooth(false))
                    assertTrue(runCatching { turn.start(false, true) }.isFailure)
                    assertEquals(previous, manager.mode)
                }
            } finally { turn.close(); pin.close(); settings.save(MicrophoneChoice.AUTOMATIC) }
            assertEquals(previous, manager.mode)
        }
    }
    @Test fun moduloWebrtcPublicoEntregaPcmLocalSinEnviarAudioARed() = runBlocking<Unit> {
        val lease = requireNotNull(app.microphone.acquire(MicrophoneOwner.REALTIME))
        val input = CommandPcmQueue()
        val route = AudioRoute(context) { fail("Ruta de prueba interrumpida") }
        var module: JavaAudioDeviceModule? = null
        var factory: PeerConnectionFactory? = null
        var activity: ActivityScenario<MainActivity>? = null
        try {
            activity = ActivityScenario.launch(MainActivity::class.java)
            route.start(false, speaker = true)
            WebRtcBootstrap.ensure(context)
            val audio = JavaAudioDeviceModule.builder(context).setInputSampleRate(16000)
                .setAudioBufferCallback { buffer, format, channels, rate, bytes, time ->
                    input.submit(buffer, format, channels, rate, bytes, android.os.SystemClock.elapsedRealtime()); time
                }.createAudioDeviceModule().also { module = it }
            factory = PeerConnectionFactory.builder().setAudioDeviceModule(audio).createPeerConnectionFactory()
            // Sin PeerConnection, SDP, credenciales ni red: usa la API pública de captura local.
            audio.requestStartRecording()
            val frame = withTimeout(10000) { input.frames.receive() }
            assertEquals(16000, frame.rate); assertTrue(frame.size > 0)
            input.release(frame)
            assertNotNull(audio.platformAudioProcessingState)
            audio.requestStopRecording()
        } finally {
            module?.requestStopRecording(); factory?.dispose(); module?.release()
            input.close(); route.close(); app.microphone.release(lease); activity?.close()
        }
        assertNull(app.microphone.owner())
    }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    } }
}
