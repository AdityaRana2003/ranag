package com.aditya.aiassistant.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aditya.aiassistant.databinding.ActivitySettingsBinding
import com.aditya.aiassistant.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.etOwnerName.setText(prefs.ownerName)
        binding.etAssistantName.setText(prefs.assistantName)
        binding.etWakeWord.setText(prefs.wakeWord)
        binding.etApiKey.setText(prefs.openAiApiKey)
        binding.seekVoiceSpeed.progress = (prefs.voiceSpeed * 100).toInt()
        binding.seekVoicePitch.progress = (prefs.voicePitch * 100).toInt()
        binding.switchAutoStart.isChecked = prefs.autoStart
        binding.switchAnnounceCalls.isChecked = prefs.announceCalls
        binding.switchAnnounceSms.isChecked = prefs.announceSms
        binding.switchContinuousListening.isChecked = prefs.continuousListening
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        prefs.ownerName = binding.etOwnerName.text.toString().ifEmpty { "Aditya" }
        prefs.assistantName = binding.etAssistantName.text.toString().ifEmpty { "Adi" }
        prefs.wakeWord = binding.etWakeWord.text.toString().ifEmpty { "hey adi" }
        prefs.openAiApiKey = binding.etApiKey.text.toString()
        prefs.voiceSpeed = binding.seekVoiceSpeed.progress / 100f
        prefs.voicePitch = binding.seekVoicePitch.progress / 100f
        prefs.autoStart = binding.switchAutoStart.isChecked
        prefs.announceCalls = binding.switchAnnounceCalls.isChecked
        prefs.announceSms = binding.switchAnnounceSms.isChecked
        prefs.continuousListening = binding.switchContinuousListening.isChecked

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
