package com.pbtservers.pbtmetagpt

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri

/** Google Search/navegador son distintos del asistente digital. Solo entradas públicas. */
class WebLauncher(private val context: Context) {
    fun intent(action: WebAction): Intent? {
        val value = when (action.destination) {
            WebDestination.GOOGLE -> context.packageManager.getLaunchIntentForPackage(SystemAssistantLauncher.GOOGLE)
            WebDestination.BROWSER -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))
            WebDestination.SEARCH -> if (action.query.isEmpty()) Intent(Intent.ACTION_WEB_SEARCH)
                else Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query)
        }
        if (value != null && context.packageManager.resolveActivity(value, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.exported == true)
            return value.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action.destination == WebDestination.GOOGLE) return null
        val fallback = Intent(Intent.ACTION_VIEW, if (action.destination == WebDestination.SEARCH && action.query.isNotEmpty())
            Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q", action.query).build()
            else Uri.parse("https://www.google.com/"))
        return fallback.takeIf { context.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.exported == true }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun open(action: WebAction, visible: Boolean): String {
        val intent = intent(action) ?: return if (action.destination == WebDestination.GOOGLE)
            "La aplicación Google no está instalada o no ofrece una entrada pública. Puedes decir «Abre el navegador»."
            else "No hay navegador ni aplicación de búsqueda disponible."
        return try {
            val notifications = context.getSystemService(NotificationManager::class.java)
            if (visible && !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                context.startActivity(intent)
                "Apertura de Google o del navegador solicitada. El asistente digital se abre con «Abre el asistente del teléfono»."
            } else {
                if (!notifications.areNotificationsEnabled()) return "Escucha pausada. Abre PbtMetaGPT para abrir Google; las notificaciones están desactivadas."
                notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Google y búsquedas", NotificationManager.IMPORTANCE_DEFAULT))
                if (notifications.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                    return "Escucha pausada. Activa el canal Google y búsquedas o utiliza Inicio."
                val pending = PendingIntent.getActivity(context, 74, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                notifications.notify(74, Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_voice)
                    .setContentTitle("Abrir Google o navegador").setContentText("Pulsa para continuar. Android puede pedir desbloquear el teléfono.")
                    .setContentIntent(pending).setAutoCancel(true).build())
                "Escucha pausada. Pulsa la notificación para abrir Google o el navegador."
            }
        } catch (_: ActivityNotFoundException) { "La aplicación de destino ya no está disponible." }
        catch (_: SecurityException) { "Android ha impedido la apertura. Abre PbtMetaGPT y repite la orden." }
    }
    companion object { private const val CHANNEL = "google-busquedas" }
}
