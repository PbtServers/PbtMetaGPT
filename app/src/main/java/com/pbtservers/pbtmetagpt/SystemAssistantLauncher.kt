package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build

/** Solo acciones del SDK público. No utiliza VOICE_ASSIST, componentes privados ni búsquedas web. */
class SystemAssistantLauncher(private val context: Context) {
    fun launchIntent(): Intent? {
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).setPackage(GOOGLE),
            Intent(Intent.ACTION_ASSIST),
        )
        return candidates.firstOrNull { intent ->
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let {
                it.exported && it.packageName != context.packageName &&
                    (it.permission.isNullOrBlank() || context.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
            } == true
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun open(visible: Boolean): String {
        val intent = launchIntent() ?: return "No hay una acción pública del asistente disponible. En Ajustes de Android → Aplicaciones predeterminadas → Asistente digital, selecciona Google y comprueba su instalación. La escucha queda pausada."
        return try {
            val manager = context.getSystemService(NotificationManager::class.java)
            val allowed = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()
            val destination = ChatGptLaunchPolicy.destination(true, visible, context.getSystemService(KeyguardManager::class.java).isKeyguardLocked, allowed)
            if (destination == ChatGptDestination.APPLICATION) {
                context.startActivity(intent)
                "Apertura del asistente solicitada mediante Android. Google/Gemini depende de tu configuración; su escucha no está confirmada. Al regresar, pulsa Activar escucha."
            } else {
                if (destination == ChatGptDestination.NOTIFICATION_SETTINGS) return "Escucha pausada. Activa las notificaciones o vuelve a PbtMetaGPT y pulsa Abrir asistente del teléfono."
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Asistente del teléfono", NotificationManager.IMPORTANCE_DEFAULT))
                if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                    return "Escucha pausada. Activa el canal Asistente del teléfono o abre el asistente desde la interfaz."
                val pending = PendingIntent.getActivity(context, 73, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(73, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_voice)
                    .setContentTitle("Abrir asistente del teléfono").setContentText("Micrófono liberado. Pulsa para abrir Google o tu asistente configurado.")
                    .setContentIntent(pending).setAutoCancel(true)
                    .addAction(Notification.Action.Builder(null, "Abrir asistente", pending).build()).build())
                "Escucha pausada. Pulsa la notificación para abrir el asistente del teléfono."
            }
        } catch (_: ActivityNotFoundException) { "El asistente del teléfono ya no está disponible. La escucha queda pausada." }
        catch (_: SecurityException) { if (visible) open(false) else "Android ha impedido abrir el asistente. Inténtalo desde Inicio; la escucha queda pausada." }
    }
    companion object { const val GOOGLE = "com.google.android.googlequicksearchbox"; private const val CHANNEL = "asistente-telefono" }
}
