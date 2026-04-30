package com.aditya.aiassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.aditya.aiassistant.manager.PhoneControlManager
import com.aditya.aiassistant.service.VoiceAssistantService
import com.aditya.aiassistant.util.PrefsManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val prefs = PrefsManager(context)
        if (!prefs.isServiceRunning || !prefs.announceCalls) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val callerName = if (phoneNumber != null) {
                    val phoneControl = PhoneControlManager(context)
                    phoneControl.lookupContactName(phoneNumber) ?: phoneNumber
                } else {
                    "Unknown caller"
                }

                Log.d(TAG, "Incoming call from: $callerName")

                val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                    action = VoiceAssistantService.ACTION_ANNOUNCE_CALL
                    putExtra(VoiceAssistantService.EXTRA_CALLER_NAME, callerName)
                }
                context.startForegroundService(serviceIntent)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call answered")
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call ended / idle")
            }
        }
    }

    companion object {
        private const val TAG = "CallReceiver"
    }
}
