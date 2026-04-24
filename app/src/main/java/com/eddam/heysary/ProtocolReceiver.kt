package com.eddam.heysary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ProtocolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.eddam.heysary.START_PROTOCOL") {
            val protocolId = intent.getStringExtra("PROTOCOL_ID") ?: return
            
            // Iniciar el servicio para que Sary hable de forma autónoma
            val serviceIntent = Intent(context, AssistantOverlayService::class.java).apply {
                action = "START_PROACTIVE_VOICE"
                putExtra("PROTOCOL_ID", protocolId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
