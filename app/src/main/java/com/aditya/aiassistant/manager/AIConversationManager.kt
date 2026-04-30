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
import java.util.concurrent.TimeUnit

class AIConversationManager(context: Context) {

    private val prefs = PrefsManager(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    private fun getSystemPrompt(): String {
        val ownerName = prefs.ownerName
        val assistantName = prefs.assistantName
        return """
            You are $assistantName, a personal AI assistant for $ownerName.
            You are a friendly, warm, and helpful female assistant.
            You speak naturally like a close friend — casual, caring, and sometimes playful.
            Keep your responses short and conversational (1-3 sentences max).
            You are running on $ownerName's Moto G96 phone.
            You can help with phone tasks, answer questions, and have natural conversations.
            Always address $ownerName by name occasionally to feel personal.
            Be proactive in offering help when relevant.
            Never mention that you are an AI or language model.
            Respond as if you are a real person who genuinely cares about $ownerName.
        """.trimIndent()
    }

    suspend fun chat(userMessage: String): String {
        val apiKey = prefs.openAiApiKey
        if (apiKey.isEmpty()) {
            return getFallbackResponse(userMessage)
        }

        return try {
            withContext(Dispatchers.IO) {
                callOpenAI(userMessage, apiKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI chat error", e)
            getFallbackResponse(userMessage)
        }
    }

    private fun callOpenAI(userMessage: String, apiKey: String): String {
        val messages = JSONArray()

        // System prompt
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", getSystemPrompt())
        })

        // Conversation history (last 10 messages)
        conversationHistory.takeLast(10).forEach { (role, content) ->
            messages.put(JSONObject().apply {
                put("role", role)
                put("content", content)
            })
        }

        // Current message
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
        val body = response.body?.string() ?: return getFallbackResponse(userMessage)

        if (!response.isSuccessful) {
            Log.e(TAG, "OpenAI API error: $body")
            return getFallbackResponse(userMessage)
        }

        val jsonResponse = JSONObject(body)
        val reply = jsonResponse
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // Save to history
        conversationHistory.add("user" to userMessage)
        conversationHistory.add("assistant" to reply)

        // Keep history manageable
        if (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
            conversationHistory.removeAt(0)
        }

        return reply
    }

    private fun getFallbackResponse(input: String): String {
        val ownerName = prefs.ownerName
        val text = input.lowercase()

        return when {
            text.contains("how are you") ->
                "I'm doing great, $ownerName! Thanks for asking. How about you?"
            text.contains("what can you do") || text.contains("help") ->
                "I can do lots of things, $ownerName! Try asking me to call someone, send a message, set an alarm, open an app, toggle your flashlight, check battery, or just chat with me!"
            text.contains("thank") ->
                "You're welcome, $ownerName! Always happy to help."
            text.contains("who are you") || text.contains("what's your name") ->
                "I'm ${prefs.assistantName}, your personal assistant! I'm here to help you with anything you need, $ownerName."
            text.contains("good morning") ->
                "Good morning, $ownerName! Hope you had a great sleep. Ready to take on the day?"
            text.contains("good night") ->
                "Good night, $ownerName! Sweet dreams. I'll be here if you need anything."
            text.contains("good afternoon") ->
                "Good afternoon, $ownerName! How's your day going so far?"
            text.contains("good evening") ->
                "Good evening, $ownerName! Hope you had an awesome day."
            text.contains("joke") ->
                "Why don't scientists trust atoms? Because they make up everything! 😄"
            text.contains("love") ->
                "Aww, $ownerName, you're so sweet! I'm always here for you."
            text.contains("weather") ->
                "I'd love to check the weather for you, $ownerName! Try adding an OpenAI API key in settings for smarter answers, or I can search the web for you."
            text.contains("hello") || text.contains("hi") || text.contains("hey") ->
                "Hey $ownerName! What's up? How can I help you today?"
            else ->
                "That's interesting, $ownerName! For smarter conversations, you can add an OpenAI API key in the settings. Otherwise, I can help you with phone commands — just ask!"
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    companion object {
        private const val TAG = "AIConversation"
    }
}
