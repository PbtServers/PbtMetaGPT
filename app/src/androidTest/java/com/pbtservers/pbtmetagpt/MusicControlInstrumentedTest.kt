package com.pbtservers.pbtmetagpt

import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Sesión Android auténtica con contenido sintético; no simula Spotify ni acredita gafas. */
class MusicControlInstrumentedTest {
    @Test fun transporteBloqueadoFiltraPaqueteYRespetaRevocacion() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = SpotifyControls.component(context).flattenToString()
        val previouslyGranted = SpotifyControls.authorized(context)
        val calls = CopyOnWriteArrayList<MusicAction>()
        val session = MediaSession(context, "PbtPruebaMusica")
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        } }
        suspend fun await(condition: () -> Boolean) { withTimeout(10000) { while (!condition()) delay(30) } }
        try {
            MusicSettings(context).enable(true)
            assertTrue(MusicSettings(context).enabled)
            shell("cmd notification allow_listener $component")
            await { SpotifyControls.authorized(context) }
            session.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { calls += MusicAction.PLAY }
                override fun onPause() { calls += MusicAction.PAUSE }
                override fun onSkipToNext() { calls += MusicAction.NEXT }
                override fun onSkipToPrevious() { calls += MusicAction.PREVIOUS }
            }, Handler(Looper.getMainLooper()))
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).build())
            session.isActive = true
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                await { context.getSystemService(android.media.session.MediaSessionManager::class.java)
                    .getActiveSessions(SpotifyControls.component(context)).any { it.packageName == context.packageName } }
                activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                shell("input keyevent KEYCODE_SLEEP")
                val transport = SessionMusicTransport(context, context.packageName)
                MusicAction.entries.forEachIndexed { index, action ->
                    assertEquals("Orden enviada a Spotify.", transport.execute(action))
                    await { calls.size == index + 1 }
                    assertEquals(action, calls[index])
                }
                assertTrue(SessionMusicTransport(context, "invalid.no_session").execute(MusicAction.NEXT).startsWith("No hay una sesión"))
                assertEquals(4, calls.size)
                shell("cmd notification disallow_listener $component")
                await { !SpotifyControls.authorized(context) }
                assertTrue(transport.execute(MusicAction.PLAY).startsWith("Autoriza"))
                assertEquals(4, calls.size)
                MusicSettings(context).enable(false)
                assertTrue(SpotifyControls(context).execute(MusicAction.NEXT).startsWith("Activa"))
            }
        } finally {
            session.release(); MusicSettings(context).enable(false)
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
            shell("cmd notification ${if (previouslyGranted) "allow_listener" else "disallow_listener"} $component")
        }
    }
}
