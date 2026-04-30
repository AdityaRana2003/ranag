package com.aditya.aiassistant.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aditya.aiassistant.R
import com.aditya.aiassistant.databinding.ActivityMainBinding
import com.aditya.aiassistant.service.VoiceAssistantService
import com.aditya.aiassistant.util.PrefsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private var assistantService: VoiceAssistantService? = null
    private var isBound = false
    private var isServiceActive = false
    private var pulseAnimator: AnimatorSet? = null

    private val conversationLog = SpannableStringBuilder()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceAssistantService.AssistantBinder
            assistantService = binder.getService()
            isBound = true

            assistantService?.setConversationCallback { text, isUser ->
                runOnUiThread {
                    appendToConversation(text)
                }
            }

            assistantService?.setListeningCallback { isListening ->
                runOnUiThread {
                    if (isListening) {
                        binding.tvStatus.text = getString(R.string.status_active)
                        startPulseAnimation()
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            assistantService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        setupUI()
        requestPermissions()
        requestBatteryOptimizationExemption()
    }

    private fun setupUI() {
        binding.fabMic.setOnClickListener {
            if (isServiceActive) {
                stopAssistant()
            } else {
                startAssistant()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.chipCall.setOnClickListener {
            if (isServiceActive) {
                appendToConversation("🎤 ${prefs.ownerName}: Call")
                // Service will handle the rest
            }
        }

        binding.chipMessage.setOnClickListener {
            if (isServiceActive) {
                appendToConversation("🎤 ${prefs.ownerName}: Send a message")
            }
        }

        binding.chipFlash.setOnClickListener {
            if (isServiceActive) {
                assistantService?.let { /* trigger flashlight toggle */ }
            }
        }

        binding.chipAlarm.setOnClickListener {
            if (isServiceActive) {
                appendToConversation("🎤 ${prefs.ownerName}: Set an alarm")
            }
        }

        // Initial state
        updateUIState(false)
    }

    private fun startAssistant() {
        val serviceIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_START
        }
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        isServiceActive = true
        updateUIState(true)
        conversationLog.clear()
    }

    private fun stopAssistant() {
        val serviceIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_STOP
        }
        startService(serviceIntent)

        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }

        isServiceActive = false
        updateUIState(false)
        stopPulseAnimation()
    }

    private fun updateUIState(active: Boolean) {
        if (active) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
            binding.fabMic.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_active)
            startPulseAnimation()
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.fabMic.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            stopPulseAnimation()
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()

        val scaleXOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleX", 1f, 1.3f, 1f)
        val scaleYOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleY", 1f, 1.3f, 1f)
        val alphaOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "alpha", 0.3f, 0.1f, 0.3f)

        val scaleXInner = ObjectAnimator.ofFloat(binding.pulseInner, "scaleX", 1f, 1.15f, 1f)
        val scaleYInner = ObjectAnimator.ofFloat(binding.pulseInner, "scaleY", 1f, 1.15f, 1f)
        val alphaInner = ObjectAnimator.ofFloat(binding.pulseInner, "alpha", 0.5f, 0.2f, 0.5f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleXOuter, scaleYOuter, alphaOuter, scaleXInner, scaleYInner, alphaInner)
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isServiceActive) {
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.pulseOuter.scaleX = 1f
        binding.pulseOuter.scaleY = 1f
        binding.pulseOuter.alpha = 0.3f
        binding.pulseInner.scaleX = 1f
        binding.pulseInner.scaleY = 1f
        binding.pulseInner.alpha = 0.5f
    }

    private fun appendToConversation(text: String) {
        conversationLog.append(text).append("\n\n")
        binding.tvConversation.text = conversationLog
        binding.scrollConversation.post {
            binding.scrollConversation.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                Toast.makeText(
                    this,
                    "Some permissions were denied. Adi may not work fully.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
        }
        stopPulseAnimation()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
