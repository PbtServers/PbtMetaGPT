package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build

/** Únicamente launcher público de la aplicación enlazada por OpenAI en Google Play. */
class OfficialChatGpt(private val context: Context) {
    fun launchIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun installed(): Boolean = launchIntent() != null
    fun open(visible: Boolean): String {
        val intent = launchIntent() ?: return "ChatGPT oficial no está instalado. Instálalo desde Google Play; editor OpenAI. La escucha queda pausada."
        return try {
            if (visible) {
                context.startActivity(intent)
                "ChatGPT abierto. Inicia el modo de voz desde su aplicación si es necesario."
            } else {
                val manager = context.getSystemService(NotificationManager::class.java)
                if ((Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    || !manager.areNotificationsEnabled()) return "Escucha pausada. Vuelve a PbtMetaGPT y pulsa Abrir ChatGPT; Android no permite mostrar la notificación."
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "ChatGPT oficial", NotificationManager.IMPORTANCE_DEFAULT))
                if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                    return "Escucha pausada. Activa el canal ChatGPT oficial o vuelve a Inicio para abrir la aplicación."
                val pending = PendingIntent.getActivity(context, 72, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(72, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_voice)
                    .setContentTitle("Abrir ChatGPT oficial").setContentText("Micrófono liberado. Pulsa para abrir ChatGPT.")
                    .setContentIntent(pending).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Abrir ChatGPT", pending).build()).build())
                "Escucha pausada. Pulsa la notificación para abrir ChatGPT."
            }
        } catch (_: ActivityNotFoundException) { "ChatGPT ya no está disponible. La escucha queda pausada." }
        catch (_: SecurityException) { if (visible) open(visible = false) else "Android no permitió mostrar el acceso a ChatGPT. La escucha queda pausada; inténtalo desde Inicio." }
    }
    companion object {
        const val PACKAGE = "com.openai.chatgpt"
        private const val CHANNEL = "chatgpt-oficial"
    }
}
