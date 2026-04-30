package com.aditya.aiassistant.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ownerName: String
        get() = prefs.getString(KEY_OWNER_NAME, "Aditya") ?: "Aditya"
        set(value) = prefs.edit().putString(KEY_OWNER_NAME, value).apply()

    var assistantName: String
        get() = prefs.getString(KEY_ASSISTANT_NAME, "Adi") ?: "Adi"
        set(value) = prefs.edit().putString(KEY_ASSISTANT_NAME, value).apply()

    var wakeWord: String
        get() = prefs.getString(KEY_WAKE_WORD, "hey adi") ?: "hey adi"
        set(value) = prefs.edit().putString(KEY_WAKE_WORD, value).apply()

    var openAiApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var voiceSpeed: Float
        get() = prefs.getFloat(KEY_VOICE_SPEED, 0.9f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_SPEED, value).apply()

    var voicePitch: Float
        get() = prefs.getFloat(KEY_VOICE_PITCH, 1.3f)
        set(value) = prefs.edit().putFloat(KEY_VOICE_PITCH, value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var announceCalls: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_CALLS, true)
        set(value) = prefs.edit().putBoolean(KEY_ANNOUNCE_CALLS, value).apply()

    var announceSms: Boolean
        get() = prefs.getBoolean(KEY_ANNOUNCE_SMS, true)
        set(value) = prefs.edit().putBoolean(KEY_ANNOUNCE_SMS, value).apply()

    var continuousListening: Boolean
        get() = prefs.getBoolean(KEY_CONTINUOUS_LISTENING, true)
        set(value) = prefs.edit().putBoolean(KEY_CONTINUOUS_LISTENING, value).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    companion object {
        private const val PREFS_NAME = "aditya_ai_prefs"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_VOICE_SPEED = "voice_speed"
        private const val KEY_VOICE_PITCH = "voice_pitch"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_ANNOUNCE_CALLS = "announce_calls"
        private const val KEY_ANNOUNCE_SMS = "announce_sms"
        private const val KEY_CONTINUOUS_LISTENING = "continuous_listening"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
