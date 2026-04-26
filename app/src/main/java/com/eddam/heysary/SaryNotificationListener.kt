package com.eddam.heysary

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SaryNotificationListener : NotificationListenerService() {

    private lateinit var prefs: SaryPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = SaryPreferences.getInstance(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.readNotificationsEnabled) return

        val packageName = sbn.packageName
        if (packageName == this.packageName) return // Ignorar propias notificaciones

        // Ignorar las notificaciones de resumen de grupos (muy comunes en WhatsApp para englobar mensajes)
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            return
        }

        if (!prefs.isPackageAllowed(packageName)) {
            Log.d("SaryNotif", "Package $packageName not in whitelist, ignoring")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Alguien"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        
        if (text.isEmpty()) return

        val pm = packageManager
        val appName = try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        Log.d("SaryNotif", "Nueva notificación de $appName ($title): $text")

        // Enviar al AssistantOverlayService
        val intent = Intent(this, AssistantOverlayService::class.java).apply {
            action = "ACTION_NOTIFICATION_RECEIVED"
            putExtra("APP_NAME", appName)
            putExtra("SENDER", title)
            putExtra("MESSAGE", text)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No necesitamos manejar remociones por ahora
    }
}
