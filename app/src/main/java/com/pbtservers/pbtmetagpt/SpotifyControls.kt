package com.pbtservers.pbtmetagpt

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService

class MusicSettings(context: Context) {
    private val preferences = context.getSharedPreferences("musica", Context.MODE_PRIVATE)
    val enabled get() = preferences.getBoolean("spotify", false)
    fun enable(value: Boolean) { check(preferences.edit().putBoolean("spotify", value).commit()) }
}

/** Solo habilita la autorización pública de sesiones. No inspecciona notificaciones, texto ni metadatos. */
class MusicNotificationAccess : NotificationListenerService()

class SpotifyControls internal constructor(private val context: Context,
    private val transport: (MusicAction) -> String = SessionMusicTransport(context, PACKAGE)::execute) {
    fun execute(action: MusicAction): String {
        if (!MusicSettings(context).enabled) return "Activa Controlar Spotify en Ajustes → Dispositivos → Música."
        return transport(action)
    }
    companion object {
        const val PACKAGE = "com.spotify.music"
        fun component(context: Context) = ComponentName(context, MusicNotificationAccess::class.java)
        fun authorized(context: Context) = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context))
    }
}

/** Filtro de paquete antes de acceder a transporte; jamás enviar teclas globales a otra aplicación. */
internal class SessionMusicTransport(private val context: Context, private val targetPackage: String) {
    fun execute(action: MusicAction): String {
        if (!SpotifyControls.authorized(context)) return "Autoriza el acceso de música a notificaciones desde Ajustes de Android."
        return try {
            val controller = context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(SpotifyControls.component(context)).firstOrNull { it.packageName == targetPackage }
                ?: return "No hay una sesión de Spotify disponible. Abre Spotify y reproduce una canción una vez; después puedes bloquear el móvil."
            val supported = controller.playbackState?.actions ?: 0L
            val required = when (action) {
                MusicAction.PLAY -> PlaybackState.ACTION_PLAY
                MusicAction.PAUSE -> PlaybackState.ACTION_PAUSE
                MusicAction.NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
                MusicAction.PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
            }
            if (supported and required == 0L) return "Spotify no ofrece este control en su sesión actual; revisa la reproducción y las condiciones de tu cuenta."
            val controls = controller.transportControls
            when (action) {
                MusicAction.PLAY -> controls.play()
                MusicAction.PAUSE -> controls.pause()
                MusicAction.NEXT -> controls.skipToNext()
                MusicAction.PREVIOUS -> controls.skipToPrevious()
            }
            "Orden enviada a Spotify."
        } catch (_: SecurityException) { "Android ha retirado el acceso a las sesiones de música. Revisa el permiso en Ajustes." }
        catch (_: RuntimeException) { "La sesión de Spotify ya no está disponible. Abre Spotify y vuelve a intentarlo." }
    }
}
