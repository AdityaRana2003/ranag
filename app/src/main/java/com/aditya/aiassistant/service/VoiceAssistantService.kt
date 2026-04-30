package com.aditya.aiassistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aditya.aiassistant.AdityaAIApp
import com.aditya.aiassistant.R
import com.aditya.aiassistant.manager.AIConversationManager
import com.aditya.aiassistant.manager.CommandParser
import com.aditya.aiassistant.manager.PhoneControlManager
import com.aditya.aiassistant.manager.SpeechRecognitionManager
import com.aditya.aiassistant.manager.TextToSpeechManager
import com.aditya.aiassistant.model.Command
import com.aditya.aiassistant.ui.LockScreenActivity
import com.aditya.aiassistant.ui.MainActivity
import com.aditya.aiassistant.util.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VoiceAssistantService : Service() {

    private val binder = AssistantBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var prefs: PrefsManager
    private lateinit var ttsManager: TextToSpeechManager
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var commandParser: CommandParser
    private lateinit var phoneControl: PhoneControlManager
    private lateinit var aiManager: AIConversationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var isAwake = false
    private var conversationCallback: ((String, Boolean) -> Unit)? = null
    private var listeningCallback: ((Boolean) -> Unit)? = null

    inner class AssistantBinder : Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsManager(this)

        ttsManager = TextToSpeechManager(this) {
            // Resume listening after speaking
            if (prefs.continuousListening && prefs.isServiceRunning) {
                speechManager.startListening()
            }
        }

        speechManager = SpeechRecognitionManager(
            context = this,
            onResult = { text -> handleSpeechResult(text) },
            onListeningStateChanged = { isListening ->
                listeningCallback?.invoke(isListening)
            }
        )

        commandParser = CommandParser()
        phoneControl = PhoneControlManager(this)
        aiManager = AIConversationManager(this)

        ttsManager.initialize()
        speechManager.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAssistant()
            ACTION_STOP -> stopAssistant()
            ACTION_ANNOUNCE_CALL -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Someone"
                wakeUpScreen()
                announceIncomingCall(callerName)
            }
            ACTION_ANNOUNCE_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SMS_SENDER) ?: "Someone"
                val message = intent.getStringExtra(EXTRA_SMS_BODY) ?: ""
                wakeUpScreen()
                announceSms(sender, message)
            }
        }
        return START_STICKY
    }

    private fun startAssistant() {
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        prefs.isServiceRunning = true
        isAwake = false // Start in wake-word listening mode (like Siri)

        val greeting = "Hey ${prefs.ownerName}! I'm ${prefs.assistantName}, your personal assistant. I'm always listening now. Just say '${prefs.wakeWord}' anytime, even when your phone is locked!"
        ttsManager.speak(greeting)
        conversationCallback?.invoke("🤖 ${prefs.assistantName}: $greeting", false)

        // Start always-on listening immediately
        speechManager.startListening()
    }

    private fun stopAssistant() {
        val farewell = "Okay ${prefs.ownerName}, going to sleep now. Say ${prefs.wakeWord} whenever you need me!"
        ttsManager.speak(farewell)
        conversationCallback?.invoke("🤖 ${prefs.assistantName}: $farewell", false)

        speechManager.stopListening()
        releaseWakeLock()
        prefs.isServiceRunning = false
        isAwake = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AdityaAI::AlwaysOnWakeLock"
            ).apply {
                acquire()
            }
            Log.d(TAG, "Wake lock acquired — always-on listening active")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun wakeUpScreen() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) {
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                        or PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or PowerManager.ON_AFTER_RELEASE,
                "AdityaAI::ScreenWake"
            )
            screenWakeLock.acquire(10_000L) // Keep screen on for 10 seconds
            Log.d(TAG, "Screen woken up")
        }

        // Launch lock screen overlay activity
        showLockScreenOverlay()
    }

    private fun showLockScreenOverlay() {
        val overlayIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        startActivity(overlayIntent)
    }

    private fun handleSpeechResult(text: String) {
        Log.d(TAG, "Speech result: $text")
        val lowerText = text.lowercase()

        // Always listening for wake word — even when phone is locked
        if (!isAwake) {
            if (lowerText.contains(prefs.wakeWord.lowercase())) {
                isAwake = true
                wakeUpScreen() // Wake up the screen when wake word is detected
                val response = "Yes, ${prefs.ownerName}? I'm listening!"
                respond(response)
                // Remove wake word from text and process remaining
                val remaining = lowerText.replace(prefs.wakeWord.lowercase(), "").trim()
                if (remaining.isNotEmpty()) {
                    processCommand(remaining)
                }
            }
            return
        }

        // Check for stop/sleep commands
        if (lowerText.matches(Regex(".*(go to sleep|stop listening|shut down|deactivate|goodbye|that's all|thank you bye).*"))) {
            isAwake = false
            val response = "Alright ${prefs.ownerName}, going back to standby. Just say '${prefs.wakeWord}' anytime to wake me up — I'm always here!"
            respond(response)
            return
        }

        conversationCallback?.invoke("🎤 ${prefs.ownerName}: $text", true)
        processCommand(text)
    }

    private fun processCommand(text: String) {
        val command = commandParser.parse(text)

        scope.launch {
            val response = when (command) {
                is Command.CallContact -> phoneControl.callContact(command.name)
                is Command.SendSms -> phoneControl.sendSms(command.name, command.message)
                is Command.OpenApp -> phoneControl.openApp(command.appName)
                is Command.SetAlarm -> phoneControl.setAlarm(command.hour, command.minute)
                is Command.SetTimer -> phoneControl.setTimer(command.minutes)
                is Command.ToggleFlashlight -> phoneControl.toggleFlashlight()
                is Command.ToggleWifi -> phoneControl.toggleWifi()
                is Command.ToggleBluetooth -> phoneControl.toggleBluetooth()
                is Command.IncreaseVolume -> phoneControl.increaseVolume()
                is Command.DecreaseVolume -> phoneControl.decreaseVolume()
                is Command.MuteVolume -> phoneControl.muteVolume()
                is Command.WhatTime -> phoneControl.getCurrentTime()
                is Command.WhatDate -> phoneControl.getCurrentDate()
                is Command.BatteryLevel -> phoneControl.getBatteryLevel()
                is Command.PlayMusic -> phoneControl.controlMedia("play")
                is Command.PauseMusic -> phoneControl.controlMedia("pause")
                is Command.NextTrack -> phoneControl.controlMedia("next")
                is Command.PreviousTrack -> phoneControl.controlMedia("previous")
                is Command.OpenCamera -> phoneControl.openCamera()
                is Command.TakePhoto -> phoneControl.openCamera()
                is Command.SearchWeb -> phoneControl.searchWeb(command.query)
                is Command.PickCall -> phoneControl.answerCall()
                is Command.RejectCall -> phoneControl.rejectCall()
                is Command.TakeScreenshot -> "Opening screenshot feature"
                is Command.ReadNotifications -> "Let me check your notifications"
                is Command.Greet -> aiManager.chat(text)
                is Command.AiChat -> aiManager.chat(command.message)
                is Command.Unknown -> aiManager.chat(text)
            }
            respond(response)
        }
    }

    private fun respond(text: String) {
        conversationCallback?.invoke("🤖 ${prefs.assistantName}: $text", false)
        ttsManager.speak(text)
    }

    fun announceIncomingCall(callerName: String) {
        if (!prefs.announceCalls) return
        speechManager.stopListening()
        val announcement = "Hey ${prefs.ownerName}! $callerName is calling you. Would you like me to pick up, or should I cut the call?"
        ttsManager.speak(announcement, flush = true)
        conversationCallback?.invoke("📞 Incoming call from $callerName", false)
        conversationCallback?.invoke("🤖 ${prefs.assistantName}: $announcement", false)
        // Resume listening for answer/reject command
        isAwake = true
    }

    fun announceSms(sender: String, message: String) {
        if (!prefs.announceSms) return
        wakeUpScreen()
        val announcement = if (message.length > 100) {
            "Hey ${prefs.ownerName}, you got a message from $sender. It says: ${message.take(100)}... Should I read the rest?"
        } else {
            "Hey ${prefs.ownerName}, $sender sent you a message saying: $message"
        }
        ttsManager.speak(announcement)
        conversationCallback?.invoke("💬 Message from $sender: $message", false)
        conversationCallback?.invoke("🤖 ${prefs.assistantName}: $announcement", false)
    }

    fun setConversationCallback(callback: (String, Boolean) -> Unit) {
        conversationCallback = callback
    }

    fun setListeningCallback(callback: (Boolean) -> Unit) {
        listeningCallback = callback
    }

    fun isRunning(): Boolean = prefs.isServiceRunning

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AdityaAIApp.CHANNEL_ID)
            .setContentTitle("${prefs.assistantName} is always listening")
            .setContentText("Say '${prefs.wakeWord}' anytime — even when locked")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        ttsManager.shutdown()
        speechManager.destroy()
        releaseWakeLock()
        prefs.isServiceRunning = false
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart service if swiped away from recents
        val restartIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_START
        }
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val TAG = "VoiceAssistantSvc"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.aditya.aiassistant.START"
        const val ACTION_STOP = "com.aditya.aiassistant.STOP"
        const val ACTION_ANNOUNCE_CALL = "com.aditya.aiassistant.ANNOUNCE_CALL"
        const val ACTION_ANNOUNCE_SMS = "com.aditya.aiassistant.ANNOUNCE_SMS"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_SMS_SENDER = "sms_sender"
        const val EXTRA_SMS_BODY = "sms_body"
    }
}
