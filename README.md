# Adi — Personal AI Voice Assistant for Moto G96

A fully-featured AI voice assistant Android app built specifically for your Moto G96. Adi listens to your voice commands, controls your phone, announces calls and messages with a natural female voice, and can have intelligent conversations powered by AI.

---

## Features

### 🎤 Voice Control
- **Always-listening mode** — continuous speech recognition
- **Wake word activation** — say "Hey Adi" to activate (customizable)
- **Natural language understanding** — speak naturally, Adi understands

### 📞 Call Management
- **Incoming call announcements** — "Hey Aditya, Simarpeet is calling you! Want me to pick up or cut the call?"
- **Voice-controlled answering** — say "pick up" or "reject the call"
- **Make calls** — "Call Mom" / "Dial Simarpeet"

### 💬 Messages
- **SMS announcements** — reads incoming messages aloud
- **Send messages by voice** — "Send a message to Simarpeet saying I'll be there in 10 minutes"

### 🔊 Realistic Female Voice
- Natural-sounding female voice (Adi)
- Adjustable speech rate and pitch
- Speaks like a real person — casual, friendly, and caring

### 📱 Phone Control Commands
| Command | Example |
|---------|---------|
| Open apps | "Open YouTube", "Launch WhatsApp" |
| Set alarm | "Set an alarm for 7:30 AM" |
| Set timer | "Set a timer for 5 minutes" |
| Flashlight | "Turn on the flashlight" |
| WiFi | "Toggle WiFi" |
| Bluetooth | "Turn on Bluetooth" |
| Volume | "Increase volume", "Mute" |
| Time/Date | "What time is it?", "What's today's date?" |
| Battery | "What's my battery level?" |
| Music | "Play music", "Next track", "Pause" |
| Camera | "Open camera", "Take a photo" |
| Screenshot | "Take a screenshot" |
| Search | "Search for weather in Delhi" |

### 🤖 AI Conversations
- Powered by OpenAI GPT-4o-mini (optional)
- Falls back to built-in responses without an API key
- Remembers conversation context
- Responds naturally like a human friend

### ⚙️ System Features
- **Auto-start on boot** — Adi starts automatically when your phone restarts
- **Runs in background** — foreground service with notification
- **Battery optimized** — requests exemption from battery optimization
- **Accessibility service** — deep phone control (gestures, navigation, notifications)

---

## Installation

### Prerequisites
- Android Studio (Arctic Fox or newer)
- Android SDK 34
- Moto G96 with Android 12+ (API 26+ minimum)
- USB debugging enabled on your phone

### Steps

1. **Clone this repository:**
   ```bash
   git clone https://github.com/AdityaRana2003/aditya-ai-assistant.git
   ```

2. **Open in Android Studio:**
   - Open Android Studio
   - Click "Open an Existing Project"
   - Navigate to the cloned folder and select it
   - Wait for Gradle sync to complete

3. **Connect your Moto G96:**
   - Enable Developer Options: Settings → About Phone → Tap "Build Number" 7 times
   - Enable USB Debugging: Settings → Developer Options → USB Debugging
   - Connect your phone via USB cable
   - Accept the debugging prompt on your phone

4. **Build and Install:**
   - Click the green ▶️ Run button in Android Studio
   - Select your Moto G96 from the device list
   - Wait for the app to install and launch

5. **Grant Permissions:**
   - The app will request all necessary permissions on first launch
   - **Grant ALL permissions** for full functionality
   - Enable Accessibility Service: Settings → Accessibility → Adi AI Assistant → Turn On

6. **Optional — Set up AI conversations:**
   - Open Settings in the app (gear icon)
   - Enter your OpenAI API key (get one at https://platform.openai.com/api-keys)
   - This enables smart, context-aware conversations

---

## Setup on Moto G96

### Battery Optimization
The app will automatically request battery optimization exemption. If Adi stops working in the background:
1. Go to Settings → Battery → Battery Optimization
2. Find "Aditya AI Assistant"
3. Select "Don't optimize"

### Accessibility Service
For full phone control (screenshots, navigation, reading notifications):
1. Go to Settings → Accessibility
2. Find "Aditya AI Assistant"
3. Enable it and confirm

### Default Assistant (Optional)
To replace Google Assistant:
1. Go to Settings → Apps → Default Apps → Digital Assistant App
2. Select "Aditya AI Assistant"

---

## Usage

1. **Open the app** and tap the microphone button
2. **Say "Hey Adi"** (or your custom wake word)
3. **Give a command** — "Call Mom", "Open Instagram", "What's the time?"
4. **Have a conversation** — "How are you?", "Tell me a joke"
5. **To sleep** — "Go to sleep" (Adi stops listening until wake word)

### During Calls
When someone calls you, Adi will say:
> "Hey Aditya! Simarpeet is calling you. Would you like me to pick up, or should I cut the call?"

Just say:
- "Pick up" / "Answer" to accept
- "Cut it" / "Reject" to decline

---

## Customization

Open Settings (gear icon in the app) to customize:
- **Your name** — how Adi addresses you
- **Assistant name** — rename Adi to anything you like
- **Wake word** — change "hey adi" to any phrase
- **Voice speed & pitch** — adjust how Adi sounds
- **Feature toggles** — enable/disable call announcements, SMS reading, auto-start, continuous listening

---

## Project Structure

```
app/src/main/java/com/aditya/aiassistant/
├── AdityaAIApp.kt                    # Application class
├── model/
│   └── Command.kt                    # Command types
├── manager/
│   ├── TextToSpeechManager.kt        # Female voice TTS
│   ├── SpeechRecognitionManager.kt   # Voice input
│   ├── CommandParser.kt              # NLP command parsing
│   ├── PhoneControlManager.kt        # Phone actions
│   └── AIConversationManager.kt      # OpenAI integration
├── service/
│   ├── VoiceAssistantService.kt      # Main foreground service
│   └── AssistantAccessibilityService.kt # Accessibility
├── receiver/
│   ├── CallReceiver.kt               # Incoming call detection
│   ├── SmsReceiver.kt                # Incoming SMS detection
│   └── BootReceiver.kt               # Auto-start on boot
├── ui/
│   ├── MainActivity.kt               # Main screen
│   └── SettingsActivity.kt           # Settings screen
└── util/
    └── PrefsManager.kt               # SharedPreferences
```

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Speech Recognition:** Android SpeechRecognizer API
- **Text-to-Speech:** Android TTS with female voice selection
- **AI:** OpenAI GPT-4o-mini (optional)
- **HTTP:** OkHttp 4
- **UI:** Material Design 3, ConstraintLayout, ViewBinding

---

## License

MIT License — feel free to modify and use as you wish!

---

Built with ❤️ for Aditya's Moto G96
