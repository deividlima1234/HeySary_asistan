package com.eddam.heysary

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

class ProtocolEngine(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleProtocol(protocol: AutoProtocol) {
        if (!protocol.isEnabled) {
            cancelProtocol(protocol)
            return
        }

        val intent = Intent(context, ProtocolReceiver::class.java).apply {
            action = "com.eddam.heysary.START_PROTOCOL"
            putExtra("PROTOCOL_ID", protocol.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            protocol.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            val (hour, minute) = protocol.currentTime.split(":").map { it.toInt() }
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // Si la hora ya pasó hoy, programar para mañana
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    fun cancelProtocol(protocol: AutoProtocol) {
        val intent = Intent(context, ProtocolReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            protocol.id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
