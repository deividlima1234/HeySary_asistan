package com.eddam.heysary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

class HeySaryAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        
        // Al activarse el asistente nativo, lanzamos nuestro servicio de superposición
        val intent = Intent(context, AssistantOverlayService::class.java)
        context.startService(intent)
        
        // Cerramos la sesión nativa inmediatamente ya que manejamos la UI con un Overlay propio
        finish()
    }
}
