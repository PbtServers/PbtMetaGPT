package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Se ejecuta en un segundo bloque de instrumentación con permisos concedidos por CI. */
class MicrophoneServiceInstrumentedTest {
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
                delay(1200)
                assertTrue(ContinuousWakeService.running)
                assertEquals(ListeningPhase.LISTENING, ContinuousWakeService.state.value)
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
            } finally { shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard") }
        }
    }
    @Test fun audioRecordYAudioTrackOperanSimultaneamenteYParadaManualLiberaAmbos() = runBlocking<Unit> {
        val gate = app.microphone
        val lease = requireNotNull(gate.acquire(MicrophoneOwner.COMMAND_LOCAL))
        val route = AudioRoute(context) { fail("La ruta del emulador se ha interrumpido") }
        val player = PcmPlayer()
        var recorder: AudioRecord? = null
        var playback: Job? = null
        try {
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
            player.stop(); playback?.cancelAndJoin()
            runCatching { recorder?.stop() }; recorder?.release(); route.close(); gate.release(lease)
        }
        assertNull(gate.owner())
    }
    private suspend fun await(condition: () -> Boolean) { withTimeout(20000) { while (!condition()) delay(50) } }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    } }
}
