package com.aditya.aiassistant.manager

import android.app.AlarmManager
import android.app.SearchManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneControlManager(private val context: Context) {

    private var isFlashlightOn = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun callContact(name: String): String {
        val phoneNumber = lookupContactNumber(name)
        return if (phoneNumber != null) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            "Calling $name now"
        } else {
            "Sorry, I couldn't find $name in your contacts"
        }
    }

    fun sendSms(name: String, message: String): String {
        val phoneNumber = lookupContactNumber(name)
        return if (phoneNumber != null) {
            if (message.isNotEmpty()) {
                try {
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                    "Message sent to $name: $message"
                } catch (e: Exception) {
                    "Sorry, I couldn't send the message. ${e.message}"
                }
            } else {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(smsIntent)
                "Opening message to $name. What would you like to say?"
            }
        } else {
            "Sorry, I couldn't find $name in your contacts"
        }
    }

    fun openApp(appName: String): String {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(
            findPackageName(appName) ?: return "Sorry, I couldn't find the app $appName"
        )
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            "Opening $appName"
        } else {
            "Sorry, I couldn't open $appName"
        }
    }

    private fun findPackageName(appName: String): String? {
        val packageManager = context.packageManager
        val apps = packageManager.getInstalledApplications(0)
        val normalized = appName.lowercase()

        // Common app mappings
        val knownApps = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "snapchat" to "com.snapchat.android",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "camera" to "com.motorola.camera3",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "files" to "com.google.android.apps.nbu.files",
            "photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "netflix" to "com.netflix.mediaclient",
            "amazon" to "com.amazon.mShop.android.shopping",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging"
        )

        knownApps[normalized]?.let { return it }

        return apps.firstOrNull { app ->
            val label = packageManager.getApplicationLabel(app).toString().lowercase()
            label.contains(normalized) || normalized.contains(label)
        }?.packageName
    }

    fun setAlarm(hour: Int, minute: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
        return "Alarm set for $displayHour:${"%02d".format(minute)} $period"
    }

    fun setTimer(minutes: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Timer set for $minutes minutes"
    }

    fun toggleFlashlight(): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(cameraId, isFlashlightOn)
            if (isFlashlightOn) "Flashlight is on" else "Flashlight is off"
        } catch (e: CameraAccessException) {
            "Sorry, I couldn't control the flashlight"
        }
    }

    fun toggleWifi(): String {
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Opening WiFi settings for you"
    }

    fun toggleBluetooth(): String {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Opening Bluetooth settings for you"
    }

    fun increaseVolume(): String {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = (current * 100) / max
        return "Volume increased to $percent percent"
    }

    fun decreaseVolume(): String {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = (current * 100) / max
        return "Volume decreased to $percent percent"
    }

    fun muteVolume(): String {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
        return "Volume muted"
    }

    fun getCurrentTime(): String {
        val format = SimpleDateFormat("h:mm a", Locale.US)
        return "It's ${format.format(Date())}"
    }

    fun getCurrentDate(): String {
        val format = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US)
        return "Today is ${format.format(Date())}"
    }

    fun getBatteryLevel(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        return if (isCharging) {
            "Your battery is at $level percent and charging"
        } else {
            "Your battery is at $level percent"
        }
    }

    fun controlMedia(action: String): String {
        val keyCode = when (action) {
            "play" -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "Unknown media action"
        }
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        return when (action) {
            "play" -> "Playing music"
            "pause" -> "Music paused"
            "next" -> "Skipped to next track"
            "previous" -> "Going to previous track"
            else -> "Done"
        }
    }

    fun openCamera(): String {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Opening camera"
    }

    fun searchWeb(query: String): String {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Searching for $query"
    }

    fun answerCall(): String {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.acceptRingingCall()
            "Call answered"
        } catch (e: SecurityException) {
            "I need permission to answer calls"
        }
    }

    fun rejectCall(): String {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
            "Call rejected"
        } catch (e: SecurityException) {
            "I need permission to reject calls"
        }
    }

    fun lookupContactNumber(name: String): String? {
        try {
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    return it.getString(numberIndex)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to read contacts", e)
        }
        return null
    }

    fun lookupContactName(phoneNumber: String): String? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    return it.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name", e)
        }
        return null
    }

    companion object {
        private const val TAG = "PhoneControlMgr"
    }
}
