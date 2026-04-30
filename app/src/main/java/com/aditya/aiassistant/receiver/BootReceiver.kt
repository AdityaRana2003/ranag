package com.aditya.aiassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aditya.aiassistant.service.VoiceAssistantService
import com.aditya.aiassistant.util.PrefsManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED
        )

        if (intent.action in validActions) {
            Log.d(TAG, "Boot/update received: ${intent.action}")
            val prefs = PrefsManager(context)
            if (prefs.autoStart) {
                try {
                    val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                        action = VoiceAssistantService.ACTION_START
                    }
                    context.startForegroundService(serviceIntent)
                    Log.d(TAG, "Adi auto-started after boot/update")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-start after boot", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
