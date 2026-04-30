package com.aditya.aiassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.aditya.aiassistant.manager.PhoneControlManager
import com.aditya.aiassistant.service.VoiceAssistantService
import com.aditya.aiassistant.util.PrefsManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val prefs = PrefsManager(context)
        if (!prefs.isServiceRunning || !prefs.announceSms) return

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format") ?: return

        val messages = pdus.mapNotNull { pdu ->
            SmsMessage.createFromPdu(pdu as ByteArray, format)
        }

        if (messages.isEmpty()) return

        val senderNumber = messages[0].displayOriginatingAddress
        val fullMessage = messages.joinToString("") { it.displayMessageBody }

        val phoneControl = PhoneControlManager(context)
        val senderName = phoneControl.lookupContactName(senderNumber) ?: senderNumber

        Log.d(TAG, "SMS from $senderName: $fullMessage")

        val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_ANNOUNCE_SMS
            putExtra(VoiceAssistantService.EXTRA_SMS_SENDER, senderName)
            putExtra(VoiceAssistantService.EXTRA_SMS_BODY, fullMessage)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
