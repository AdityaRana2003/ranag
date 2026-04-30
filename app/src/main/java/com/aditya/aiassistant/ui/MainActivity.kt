package com.aditya.aiassistant.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private var ringRotationAnimator: ObjectAnimator? = null
    private var glowAnimator: AnimatorSet? = null
    private var waveAnimators: MutableList<ObjectAnimator> = mutableListOf()
    private var colorCycleAnimator: ValueAnimator? = null

    private enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING }
    private var currentState = AssistantState.IDLE

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
                        setAnimationState(AssistantState.LISTENING)
                    }
                }
            }

            assistantService?.setSpeakingCallback { isSpeaking ->
                runOnUiThread {
                    if (isSpeaking) {
                        setAnimationState(AssistantState.SPEAKING)
                    } else if (isServiceActive) {
                        setAnimationState(AssistantState.LISTENING)
                    }
                }
            }

            assistantService?.setThinkingCallback { isThinking ->
                runOnUiThread {
                    if (isThinking) {
                        setAnimationState(AssistantState.THINKING)
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
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isServiceRunning && !isBound) {
            val serviceIntent = Intent(this, VoiceAssistantService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            isServiceActive = true
            updateUIState(true)
        }
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
                appendToConversation("${prefs.ownerName}: Call")
            }
        }

        binding.chipMessage.setOnClickListener {
            if (isServiceActive) {
                appendToConversation("${prefs.ownerName}: Send a message")
            }
        }

        binding.chipFlash.setOnClickListener {
            if (isServiceActive) {
                assistantService?.let { /* trigger flashlight toggle */ }
            }
        }

        binding.chipAlarm.setOnClickListener {
            if (isServiceActive) {
                appendToConversation("${prefs.ownerName}: Set an alarm")
            }
        }

        updateUIState(false)
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun autoStartIfReady() {
        if (prefs.autoStart && !isServiceActive && hasRequiredPermissions()) {
            startAssistant()
        }
    }

    private fun startAssistant() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Please grant microphone permission first!", Toast.LENGTH_LONG).show()
            checkAndRequestPermissions()
            return
        }

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
        stopAllAnimations()
    }

    private fun updateUIState(active: Boolean) {
        if (active) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
            binding.fabMic.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_active)
            setAnimationState(AssistantState.LISTENING)
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.fabMic.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            setAnimationState(AssistantState.IDLE)
        }
    }

    // ========================
    // SIRI-LIKE ANIMATION SYSTEM
    // ========================

    private fun setAnimationState(state: AssistantState) {
        if (currentState == state) return
        currentState = state

        stopAllAnimations()

        when (state) {
            AssistantState.IDLE -> showIdleState()
            AssistantState.LISTENING -> showListeningState()
            AssistantState.THINKING -> showThinkingState()
            AssistantState.SPEAKING -> showSpeakingState()
        }
    }

    private fun showIdleState() {
        binding.siriGlow.alpha = 0f
        binding.siriRing.alpha = 0f
        binding.waveContainer.alpha = 0f
        binding.tvStateLabel.alpha = 0f
        binding.pulseOuter.alpha = 0.3f
        binding.pulseInner.alpha = 0.5f

        // Gentle idle pulse
        val scaleXOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleX", 1f, 1.05f, 1f)
        val scaleYOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleY", 1f, 1.05f, 1f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleXOuter, scaleYOuter)
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentState == AssistantState.IDLE) start()
                }
            })
            start()
        }
    }

    private fun showListeningState() {
        // Show state label
        binding.tvStateLabel.text = "Listening..."
        binding.tvStateLabel.setTextColor(ContextCompat.getColor(this, R.color.siri_blue))
        binding.tvStateLabel.animate().alpha(1f).setDuration(300).start()

        // Show glow
        binding.siriGlow.animate().alpha(0.6f).setDuration(500).start()

        // Show and rotate the ring
        binding.siriRing.animate().alpha(0.8f).setDuration(500).start()
        ringRotationAnimator = ObjectAnimator.ofFloat(binding.siriRing, "rotation", 0f, 360f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Pulsing circles with Siri colors
        val pulseOuterColor = ContextCompat.getColor(this, R.color.siri_blue)
        (binding.pulseOuter.background as? GradientDrawable)?.setColor(pulseOuterColor)
        (binding.pulseInner.background as? GradientDrawable)?.setColor(pulseOuterColor)

        val scaleXOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleX", 1f, 1.25f, 1f)
        val scaleYOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleY", 1f, 1.25f, 1f)
        val alphaOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "alpha", 0.4f, 0.15f, 0.4f)

        val scaleXInner = ObjectAnimator.ofFloat(binding.pulseInner, "scaleX", 1f, 1.15f, 1f)
        val scaleYInner = ObjectAnimator.ofFloat(binding.pulseInner, "scaleY", 1f, 1.15f, 1f)
        val alphaInner = ObjectAnimator.ofFloat(binding.pulseInner, "alpha", 0.6f, 0.25f, 0.6f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleXOuter, scaleYOuter, alphaOuter, scaleXInner, scaleYInner, alphaInner)
            duration = 1200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentState == AssistantState.LISTENING) start()
                }
            })
            start()
        }

        // Show wave bars
        binding.waveContainer.animate().alpha(1f).setDuration(400).start()
        startWaveAnimation()

        // Color cycling on the glow
        startColorCycle()
    }

    private fun showThinkingState() {
        // State label
        binding.tvStateLabel.text = "Thinking..."
        binding.tvStateLabel.setTextColor(ContextCompat.getColor(this, R.color.siri_orange))
        binding.tvStateLabel.animate().alpha(1f).setDuration(300).start()

        // Glow
        binding.siriGlow.animate().alpha(0.4f).setDuration(300).start()

        // Hide wave bars
        binding.waveContainer.animate().alpha(0f).setDuration(200).start()

        // Ring spins faster when thinking
        binding.siriRing.animate().alpha(1f).setDuration(300).start()
        ringRotationAnimator = ObjectAnimator.ofFloat(binding.siriRing, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Orange pulsing circles
        val orangeColor = ContextCompat.getColor(this, R.color.siri_orange)
        (binding.pulseOuter.background as? GradientDrawable)?.setColor(orangeColor)
        (binding.pulseInner.background as? GradientDrawable)?.setColor(orangeColor)

        val scaleXOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleX", 1f, 1.1f, 0.95f, 1f)
        val scaleYOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleY", 1f, 1.1f, 0.95f, 1f)
        val alphaOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "alpha", 0.3f, 0.5f, 0.2f, 0.3f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleXOuter, scaleYOuter, alphaOuter)
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentState == AssistantState.THINKING) start()
                }
            })
            start()
        }
    }

    private fun showSpeakingState() {
        // State label
        binding.tvStateLabel.text = "Speaking..."
        binding.tvStateLabel.setTextColor(ContextCompat.getColor(this, R.color.siri_green))
        binding.tvStateLabel.animate().alpha(1f).setDuration(300).start()

        // Glow
        binding.siriGlow.animate().alpha(0.5f).setDuration(300).start()

        // Show wave bars for speech visualization
        binding.waveContainer.animate().alpha(1f).setDuration(300).start()
        startSpeakingWaveAnimation()

        // Ring rotates slowly
        binding.siriRing.animate().alpha(0.6f).setDuration(300).start()
        ringRotationAnimator = ObjectAnimator.ofFloat(binding.siriRing, "rotation", 0f, 360f).apply {
            duration = 5000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Green pulsing
        val greenColor = ContextCompat.getColor(this, R.color.siri_green)
        (binding.pulseOuter.background as? GradientDrawable)?.setColor(greenColor)
        (binding.pulseInner.background as? GradientDrawable)?.setColor(greenColor)

        val scaleXOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleX", 1f, 1.15f, 1f)
        val scaleYOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "scaleY", 1f, 1.15f, 1f)
        val alphaOuter = ObjectAnimator.ofFloat(binding.pulseOuter, "alpha", 0.3f, 0.5f, 0.3f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleXOuter, scaleYOuter, alphaOuter)
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (currentState == AssistantState.SPEAKING) start()
                }
            })
            start()
        }
    }

    private fun startWaveAnimation() {
        val bars = listOf(
            binding.waveBar1, binding.waveBar2, binding.waveBar3,
            binding.waveBar4, binding.waveBar5
        )

        val heights = listOf(20f, 40f, 55f, 40f, 20f)
        val delays = listOf(0L, 100L, 200L, 100L, 0L)

        bars.forEachIndexed { index, bar ->
            val minH = dpToPx(10f)
            val maxH = dpToPx(heights[index])

            val anim = ObjectAnimator.ofFloat(bar, "scaleY", 1f, maxH / minH, 0.5f, 1f).apply {
                duration = 600
                startDelay = delays[index]
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            waveAnimators.add(anim)
        }
    }

    private fun startSpeakingWaveAnimation() {
        val bars = listOf(
            binding.waveBar1, binding.waveBar2, binding.waveBar3,
            binding.waveBar4, binding.waveBar5
        )

        bars.forEachIndexed { index, bar ->
            val anim = ObjectAnimator.ofFloat(bar, "scaleY", 0.3f, 1.5f, 0.6f, 1.2f, 0.3f).apply {
                duration = 800 + (index * 50L)
                startDelay = index * 80L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            waveAnimators.add(anim)
        }
    }

    private fun startColorCycle() {
        val blue = ContextCompat.getColor(this, R.color.siri_blue)
        val purple = ContextCompat.getColor(this, R.color.siri_purple)
        val pink = ContextCompat.getColor(this, R.color.siri_pink)
        val cyan = ContextCompat.getColor(this, R.color.siri_cyan)

        colorCycleAnimator = ValueAnimator.ofObject(ArgbEvaluator(), blue, purple, pink, cyan, blue).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                binding.tvStateLabel.setTextColor(color)
            }
            start()
        }
    }

    private fun stopAllAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        ringRotationAnimator?.cancel()
        ringRotationAnimator = null
        glowAnimator?.cancel()
        glowAnimator = null
        colorCycleAnimator?.cancel()
        colorCycleAnimator = null
        waveAnimators.forEach { it.cancel() }
        waveAnimators.clear()

        // Reset views
        binding.pulseOuter.scaleX = 1f
        binding.pulseOuter.scaleY = 1f
        binding.pulseOuter.alpha = 0.3f
        binding.pulseInner.scaleX = 1f
        binding.pulseInner.scaleY = 1f
        binding.pulseInner.alpha = 0.5f

        // Reset pulse colors
        val defaultColor = ContextCompat.getColor(this, R.color.pulse_color)
        (binding.pulseOuter.background as? GradientDrawable)?.setColor(defaultColor)
        (binding.pulseInner.background as? GradientDrawable)?.setColor(defaultColor)

        // Reset wave bars
        listOf(binding.waveBar1, binding.waveBar2, binding.waveBar3, binding.waveBar4, binding.waveBar5).forEach {
            it.scaleY = 1f
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    // ========================
    // CONVERSATION & PERMISSIONS
    // ========================

    private fun appendToConversation(text: String) {
        conversationLog.append(text).append("\n\n")
        binding.tvConversation.text = conversationLog
        binding.scrollConversation.post {
            binding.scrollConversation.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun checkAndRequestPermissions() {
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
            AlertDialog.Builder(this)
                .setTitle("Adi needs permissions")
                .setMessage("Adi needs access to your microphone, contacts, phone, and SMS to work properly. Please tap 'Allow' for each permission.\n\nIf you accidentally tap 'Deny', you can go to Settings > Apps > Adi AI Assistant > Permissions to enable them.")
                .setPositiveButton("OK, Let's Go") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        neededPermissions.toTypedArray(),
                        PERMISSION_REQUEST_CODE
                    )
                }
                .setCancelable(false)
                .show()
        } else {
            requestBatteryOptimizationExemption()
            autoStartIfReady()
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
                AlertDialog.Builder(this)
                    .setTitle("Some permissions denied")
                    .setMessage("Adi needs all permissions to work properly.\n\nDenied: ${denied.joinToString(", ") { it.substringAfterLast(".") }}\n\nYou can enable them in your phone Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Continue Anyway") { _, _ ->
                        requestBatteryOptimizationExemption()
                        autoStartIfReady()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                requestBatteryOptimizationExemption()
                autoStartIfReady()
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
        }
        stopAllAnimations()
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
