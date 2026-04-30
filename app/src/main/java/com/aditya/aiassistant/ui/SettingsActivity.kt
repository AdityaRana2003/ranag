package com.aditya.aiassistant.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aditya.aiassistant.databinding.ActivitySettingsBinding
import com.aditya.aiassistant.manager.TextToSpeechManager
import com.aditya.aiassistant.util.PrefsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PrefsManager
    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PrefsManager(this)

        setupVoiceStyleSpinner()
        loadSettings()
        setupListeners()
    }

    private fun setupVoiceStyleSpinner() {
        val styleNames = TextToSpeechManager.VOICE_STYLES.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, styleNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerVoiceStyle.adapter = adapter

        binding.spinnerVoiceStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedStyle = TextToSpeechManager.VOICE_STYLES[position].first
                if (selectedStyle == TextToSpeechManager.VOICE_CUSTOM) {
                    binding.layoutCustomVoice.visibility = View.VISIBLE
                } else {
                    binding.layoutCustomVoice.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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

        // Set voice style spinner to current selection
        val currentStyleIndex = TextToSpeechManager.VOICE_STYLES.indexOfFirst { it.first == prefs.voiceStyle }
        if (currentStyleIndex >= 0) {
            binding.spinnerVoiceStyle.setSelection(currentStyleIndex)
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnTestVoice.setOnClickListener {
            testCurrentVoice()
        }

        binding.btnOpenAiWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/api-keys"))
            startActivity(intent)
        }

        binding.btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'Aditya AI Assistant' and turn it ON", Toast.LENGTH_LONG).show()
        }
    }

    private fun testCurrentVoice() {
        val position = binding.spinnerVoiceStyle.selectedItemPosition
        val style = TextToSpeechManager.VOICE_STYLES[position].first
        val ownerName = binding.etOwnerName.text.toString().ifEmpty { "Aditya" }
        val assistantName = binding.etAssistantName.text.toString().ifEmpty { "Adi" }

        testTts?.shutdown()
        testTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val manager = TextToSpeechManager(this)
                manager.initialize()
                // Small delay to let TTS engine initialize
                binding.root.postDelayed({
                    manager.applyVoiceStyle(style)
                    manager.speak("Hey $ownerName! I'm $assistantName. How are you doing today? I'm ready to help you with anything!")
                }, 500)
            }
        }
        Toast.makeText(this, "Testing voice...", Toast.LENGTH_SHORT).show()
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

        // Save voice style
        val position = binding.spinnerVoiceStyle.selectedItemPosition
        prefs.voiceStyle = TextToSpeechManager.VOICE_STYLES[position].first

        Toast.makeText(this, "Settings saved! Voice will update now.", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        testTts?.shutdown()
        super.onDestroy()
    }
}
