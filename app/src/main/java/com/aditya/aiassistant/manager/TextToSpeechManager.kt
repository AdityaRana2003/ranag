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
    private val onSpeechDone: (() -> Unit)? = null
) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val prefs = PrefsManager(context)
    private val pendingQueue = mutableListOf<String>()

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureFemaleVoice()
                isReady = true
                // Speak any queued messages
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    private fun configureFemaleVoice() {
        val engine = tts ?: return

        engine.language = Locale.US

        // Try to find a female voice — prefer high-quality voices
        val voices = engine.voices
        val femaleVoice = voices?.let { voiceSet ->
            voiceSet.filter { voice ->
                !voice.isNetworkConnectionRequired &&
                voice.locale.language == "en" &&
                (voice.name.contains("female", ignoreCase = true) ||
                 voice.name.contains("en-us-x-sfg", ignoreCase = true) ||
                 voice.name.contains("en-us-x-tpf", ignoreCase = true) ||
                 voice.name.contains("en-gb-x-gba", ignoreCase = true))
            }.minByOrNull { it.quality }
                ?: voiceSet.filter { voice ->
                    voice.locale.language == "en" &&
                    !voice.isNetworkConnectionRequired
                }.firstOrNull()
        }

        femaleVoice?.let { engine.voice = it }

        engine.setSpeechRate(prefs.voiceSpeed)
        engine.setPitch(prefs.voicePitch)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                onSpeechDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                onSpeechDone?.invoke()
            }
        })
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
        tts?.setSpeechRate(prefs.voiceSpeed)
        tts?.setPitch(prefs.voicePitch)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
