package com.aditya.aiassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aditya.aiassistant.service.VoiceAssistantService
import com.aditya.aiassistant.util.PrefsManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = PrefsManager(context)
            if (prefs.autoStart) {
                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    action = VoiceAssistantService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
