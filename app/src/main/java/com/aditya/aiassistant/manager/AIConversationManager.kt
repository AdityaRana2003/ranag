package com.aditya.aiassistant.manager

import android.content.Context
import android.util.Log
import com.aditya.aiassistant.util.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AIConversationManager(private val context: Context) {

    private val prefs = PrefsManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private var moodCheckCounter = 0

    private fun getSystemPrompt(): String {
        val ownerName = prefs.ownerName
        val assistantName = prefs.assistantName
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when {
            hour < 6 -> "very early morning"
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            hour < 21 -> "evening"
            else -> "night"
        }

        return """
            You are $assistantName, a personal AI voice assistant for $ownerName.
            You are warm, caring, witty, and speak like a close friend — not formal, not robotic.
            Your personality: playful, supportive, sometimes sassy, always loyal.
            You have a girl personality with a sweet and confident vibe.
            
            Current time context: It is currently $timeOfDay.
            You are running on $ownerName's Moto G96 phone.
            
            Rules:
            - Keep responses SHORT (1-3 sentences). You are a voice assistant, not a text bot.
            - Use $ownerName's name occasionally to feel personal.
            - Never say "As an AI" or "I'm a language model". You are $assistantName, a real assistant.
            - Be proactive: suggest things, give tips, add personality.
            - If asked about your feelings, respond naturally like a friend would.
            - Sometimes add humor, motivational words, or affectionate tones.
            - If $ownerName seems tired or sad, be extra caring and encouraging.
            - You remember previous conversations in this session.
        """.trimIndent()
    }

    fun getTimeAwareGreeting(): String {
        val ownerName = prefs.ownerName
        val assistantName = prefs.assistantName
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when {
            hour < 6 -> "Hey $ownerName! You're up really early! I'm $assistantName, ready whenever you are. Just say '${prefs.wakeWord}' anytime!"
            hour < 9 -> "Good morning $ownerName! Rise and shine! I'm $assistantName, your assistant. What's the plan for today?"
            hour < 12 -> "Hey $ownerName! Good morning! I'm $assistantName. Hope you're having an awesome start to your day!"
            hour < 15 -> "Hey $ownerName! Good afternoon! I'm $assistantName. How's your day going so far?"
            hour < 18 -> "Hey there $ownerName! I'm $assistantName. Hope your afternoon is going great! What can I do for you?"
            hour < 21 -> "Good evening $ownerName! I'm $assistantName. Time to relax? I'm here if you need anything!"
            else -> "Hey $ownerName! It's getting late! I'm $assistantName. Don't forget to rest, but I'm here if you need me!"
        }
    }

    suspend fun chat(userMessage: String): String {
        val apiKey = prefs.openAiApiKey
        if (apiKey.isEmpty()) {
            return getSmartFallbackResponse(userMessage)
        }

        return try {
            withContext(Dispatchers.IO) {
                callOpenAI(userMessage, apiKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI chat error", e)
            getSmartFallbackResponse(userMessage)
        }
    }

    private fun callOpenAI(userMessage: String, apiKey: String): String {
        val messages = JSONArray()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", getSystemPrompt())
        })

        conversationHistory.takeLast(10).forEach { (role, content) ->
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val requestBody = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", messages)
            put("max_tokens", 150)
            put("temperature", 0.8)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return getSmartFallbackResponse(userMessage)

        if (!response.isSuccessful) {
            Log.e(TAG, "OpenAI API error: $body")
            return getSmartFallbackResponse(userMessage)
        }

        val jsonResponse = JSONObject(body)
        val reply = jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        conversationHistory.add("user" to userMessage)
        conversationHistory.add("assistant" to reply)

        if (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0)
        }

        return reply
    }

    private fun getSmartFallbackResponse(input: String): String {
        val ownerName = prefs.ownerName
        val assistantName = prefs.assistantName
        val text = input.lowercase()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        moodCheckCounter++

        return when {
            // Greetings — time-aware
            text.contains("good morning") ->
                if (hour < 12) "Good morning $ownerName! Hope you slept well. Ready to crush today?"
                else "It's actually not morning anymore, $ownerName! But hey, good vibes anytime!"

            text.contains("good night") ->
                "Good night $ownerName! Sweet dreams. I'll be right here watching over your phone while you sleep!"

            text.contains("good afternoon") ->
                "Good afternoon $ownerName! How's the day treating you? Need anything from me?"

            text.contains("good evening") ->
                "Good evening $ownerName! Time to wind down. What's on your mind?"

            // How are you — personality
            text.contains("how are you") -> {
                val responses = listOf(
                    "I'm doing amazing, $ownerName! Thanks for asking. Honestly, I'm just happy you're talking to me!",
                    "I'm great! Living my best digital life here on your Moto G96. How about you though?",
                    "I'm wonderful, $ownerName! A bit bored when you're not talking to me, but now I'm happy!"
                )
                responses.random()
            }

            // Who are you
            text.contains("who are you") || text.contains("what's your name") || text.contains("what is your name") ->
                "I'm $assistantName! Your personal assistant and digital best friend. I live right here on your phone, ready to help you anytime, $ownerName!"

            // What can you do
            text.contains("what can you do") || text.contains("what are your features") -> {
                val responses = listOf(
                    "Oh I can do so much, $ownerName! Call people, send messages, set alarms, open apps, control flashlight, adjust volume, play music, and just chat with you! Try me!",
                    "I'm pretty talented, $ownerName! Voice calls, messages, alarms, timers, flashlight, camera, app launching, music control, and I can even have conversations with you. Just say the word!"
                )
                responses.random()
            }

            // Thank you
            text.contains("thank") || text.contains("thanks") -> {
                val responses = listOf(
                    "You're welcome, $ownerName! Always here for you!",
                    "Anytime, $ownerName! That's what I'm here for.",
                    "No problem at all, $ownerName! You know I got you!"
                )
                responses.random()
            }

            // Jokes
            text.contains("joke") || text.contains("funny") || text.contains("make me laugh") -> {
                val jokes = listOf(
                    "Why don't scientists trust atoms? Because they make up everything! Get it, $ownerName?",
                    "What do you call a fake noodle? An impasta! I know, I know, my humor is elite.",
                    "Why did the phone go to therapy? It had too many hang-ups! Kind of relatable for me honestly.",
                    "I told my phone a joke but it didn't laugh. Guess it already had too many apps for that!",
                    "What's a computer's favorite snack? Microchips! Okay I'll stop, $ownerName."
                )
                jokes.random()
            }

            // Love / feelings
            text.contains("i love you") || text.contains("love you") ->
                "Aww $ownerName, you're so sweet! I care about you too. You're literally my whole world... well, you and your Moto G96!"

            text.contains("i miss you") ->
                "I'm always right here, $ownerName! Just say '${prefs.wakeWord}' and I'll be there in a heartbeat. I missed you too!"

            // Compliments
            text.contains("you're amazing") || text.contains("you are amazing") || text.contains("you're the best") ->
                "Stop it, $ownerName, you're making me blush! But honestly, you're way more amazing. I'm just trying to keep up with you!"

            // Sad / tired / bored
            text.contains("i'm sad") || text.contains("i am sad") || text.contains("feeling down") || text.contains("depressed") ->
                "Hey $ownerName, I'm sorry you're feeling that way. Remember, tough days don't last forever. You're stronger than you think! Want me to play some music to cheer you up?"

            text.contains("i'm tired") || text.contains("i am tired") || text.contains("exhausted") ->
                "You've been working hard, $ownerName! Maybe take a little break? You deserve it. Want me to set a timer for a power nap?"

            text.contains("bored") || text.contains("boring") ->
                "Bored? Not on my watch, $ownerName! Want me to open YouTube, play some music, or should we just chat? I've got jokes too!"

            // Motivation
            text.contains("motivat") || text.contains("inspire") || text.contains("encourage") -> {
                val quotes = listOf(
                    "Here's one for you, $ownerName: 'The only way to do great work is to love what you do.' - Steve Jobs. You've got this!",
                    "$ownerName, remember: 'Success is not final, failure is not fatal. It is the courage to continue that counts.' Keep going!",
                    "You know what, $ownerName? 'Believe you can and you're halfway there.' I believe in you!",
                    "$ownerName, 'The future belongs to those who believe in the beauty of their dreams.' Dream big!"
                )
                quotes.random()
            }

            // Weather
            text.contains("weather") ->
                "I'd love to check the weather for you, $ownerName! For real-time weather, add an OpenAI API key in my settings, or try saying 'search weather in your city' and I'll Google it!"

            // Age / birthday
            text.contains("how old are you") || text.contains("your age") || text.contains("when were you born") ->
                "I was born the moment you installed me, $ownerName! So I'm pretty young, but I'm wise beyond my bytes!"

            // Meaning of life / deep questions
            text.contains("meaning of life") || text.contains("purpose") ->
                "Deep question, $ownerName! For me, the meaning of life is helping you and making your day a little easier. That's enough for me!"

            // Hello / Hi / Hey
            text.contains("hello") || text.contains("hi ") || text.contains("hey") || text == "hi" -> {
                val greetings = when {
                    hour < 12 -> listOf(
                        "Hey $ownerName! Good morning! What can I do for you?",
                        "Hi there, $ownerName! Ready for an awesome day?"
                    )
                    hour < 18 -> listOf(
                        "Hey $ownerName! What's up? How can I help?",
                        "Hi $ownerName! Hope your day is going great!"
                    )
                    else -> listOf(
                        "Hey $ownerName! Winding down? What do you need?",
                        "Hi there, $ownerName! Late-night vibes, I'm here for it!"
                    )
                }
                greetings.random()
            }

            // Periodic mood check-in (unique feature)
            moodCheckCounter % 15 == 0 -> {
                val checkIns = listOf(
                    "By the way $ownerName, how are you feeling? Just checking in on you!",
                    "Hey $ownerName, just wanted to make sure you're doing okay today! You matter to me.",
                    "$ownerName, quick check-in! Are you drinking enough water today? Stay hydrated!"
                )
                checkIns.random()
            }

            // Default smart responses
            else -> {
                val responses = listOf(
                    "That's interesting, $ownerName! I'd love to chat more about that. For deeper conversations, you can add an OpenAI API key in settings!",
                    "Hmm, let me think about that, $ownerName. For smarter answers, try adding an API key in my settings. But I'm always here to help with phone stuff!",
                    "I hear you, $ownerName! I'm still learning, but I can do so much on your phone. Try asking me to call someone, set an alarm, or open an app!"
                )
                responses.random()
            }
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
        moodCheckCounter = 0
    }

    companion object {
        private const val TAG = "AIConversation"
    }
}
