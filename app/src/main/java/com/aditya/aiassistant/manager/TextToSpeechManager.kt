package com.aditya.aiassistant.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aditya.aiassistant.util.PrefsManager
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(
    private val context: Context,
    private val onSpeechDone: (() -> Unit)? = null,
    private val onSpeechStart: (() -> Unit)? = null
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val prefs = PrefsManager(context)
    private val pendingQueue = mutableListOf<String>()

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyVoiceStyle(prefs.voiceStyle)
                isReady = true
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun applyVoiceStyle(style: String) {
        val engine = tts ?: return
        engine.language = Locale.US

        when (style) {
            VOICE_SWEET_GIRL -> {
                selectVoice(engine, listOf("female", "sfg", "tpf"), fallbackPitch = 1.3f)
                engine.setPitch(1.3f)
                engine.setSpeechRate(0.9f)
            }
            VOICE_DEEP_WOMAN -> {
                selectVoice(engine, listOf("female", "sfg"), fallbackPitch = 0.85f)
                engine.setPitch(0.85f)
                engine.setSpeechRate(0.85f)
            }
            VOICE_SOFT_WHISPER -> {
                selectVoice(engine, listOf("female", "sfg", "tpf"), fallbackPitch = 1.5f)
                engine.setPitch(1.5f)
                engine.setSpeechRate(0.75f)
            }
            VOICE_ENERGETIC -> {
                selectVoice(engine, listOf("female", "sfg"), fallbackPitch = 1.2f)
                engine.setPitch(1.2f)
                engine.setSpeechRate(1.15f)
            }
            VOICE_PROFESSIONAL -> {
                selectVoice(engine, listOf("female", "sfg", "gba"), fallbackPitch = 1.0f)
                engine.setPitch(1.0f)
                engine.setSpeechRate(1.0f)
            }
            VOICE_MALE_DEEP -> {
                selectVoice(engine, listOf("male", "smj", "tpc"), fallbackPitch = 0.7f)
                engine.setPitch(0.7f)
                engine.setSpeechRate(0.9f)
            }
            VOICE_MALE_CASUAL -> {
                selectVoice(engine, listOf("male", "smj"), fallbackPitch = 1.0f)
                engine.setPitch(1.0f)
                engine.setSpeechRate(1.0f)
            }
            VOICE_ROBOTIC -> {
                selectVoice(engine, emptyList(), fallbackPitch = 0.6f)
                engine.setPitch(0.6f)
                engine.setSpeechRate(1.2f)
            }
            else -> {
                selectVoice(engine, listOf("female", "sfg", "tpf"), fallbackPitch = prefs.voicePitch)
                engine.setPitch(prefs.voicePitch)
                engine.setSpeechRate(prefs.voiceSpeed)
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeechStart?.invoke()
            }
            override fun onDone(utteranceId: String?) {
                onSpeechDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                onSpeechDone?.invoke()
            }
        })
    }

    private fun selectVoice(engine: TextToSpeech, keywords: List<String>, fallbackPitch: Float) {
        val voices = engine.voices ?: return

        val matchedVoice = if (keywords.isNotEmpty()) {
            voices.filter { voice ->
                !voice.isNetworkConnectionRequired &&
                voice.locale.language == "en" &&
                keywords.any { keyword -> voice.name.contains(keyword, ignoreCase = true) }
            }.minByOrNull { it.quality }
        } else null

        val finalVoice = matchedVoice
            ?: voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }.firstOrNull()

        finalVoice?.let { engine.voice = it }
    }

    fun speak(text: String, flush: Boolean = false) {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun updateVoiceSettings() {
        applyVoiceStyle(prefs.voiceStyle)
    }

    fun getAvailableVoiceNames(): List<String> {
        return tts?.voices?.filter {
            it.locale.language == "en" && !it.isNetworkConnectionRequired
        }?.map { it.name } ?: emptyList()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "TTSManager"
        const val VOICE_SWEET_GIRL = "sweet_girl"
        const val VOICE_DEEP_WOMAN = "deep_woman"
        const val VOICE_SOFT_WHISPER = "soft_whisper"
        const val VOICE_ENERGETIC = "energetic"
        const val VOICE_PROFESSIONAL = "professional"
        const val VOICE_MALE_DEEP = "male_deep"
        const val VOICE_MALE_CASUAL = "male_casual"
        const val VOICE_ROBOTIC = "robotic"
        const val VOICE_CUSTOM = "custom"

        val VOICE_STYLES = listOf(
            VOICE_SWEET_GIRL to "Sweet Girl (Default)",
            VOICE_DEEP_WOMAN to "Deep Woman",
            VOICE_SOFT_WHISPER to "Soft Whisper",
            VOICE_ENERGETIC to "Energetic Girl",
            VOICE_PROFESSIONAL to "Professional",
            VOICE_MALE_DEEP to "Deep Male",
            VOICE_MALE_CASUAL to "Casual Male",
            VOICE_ROBOTIC to "Robotic",
            VOICE_CUSTOM to "Custom (Use sliders)"
        )
    }
}
