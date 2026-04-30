package com.aditya.aiassistant.manager

import com.aditya.aiassistant.model.Command

class CommandParser {

    fun parse(input: String): Command {
        val text = input.lowercase().trim()

        return when {
            // Call handling
            text.matches(Regex("(pick|accept|answer)\\s*(the)?\\s*(call|phone).*")) -> Command.PickCall
            text.matches(Regex("(reject|decline|cut|hang up|ignore)\\s*(the)?\\s*(call|phone).*")) -> Command.RejectCall

            // Calling someone
            text.matches(Regex("(call|dial|ring)\\s+(.+)")) -> {
                val name = text.replace(Regex("^(call|dial|ring)\\s+"), "")
                Command.CallContact(name)
            }

            // SMS
            text.matches(Regex("(send|text)\\s+(a\\s+)?(message|sms|text)\\s+to\\s+(.+?)\\s+(saying|that|message)\\s+(.+)")) -> {
                val match = Regex("(send|text)\\s+(a\\s+)?(message|sms|text)\\s+to\\s+(.+?)\\s+(saying|that|message)\\s+(.+)")
                    .find(text)
                val name = match?.groupValues?.get(4)?.trim() ?: ""
                val message = match?.groupValues?.get(6)?.trim() ?: ""
                Command.SendSms(name, message)
            }
            text.matches(Regex("(send|text)\\s+(a\\s+)?(message|sms|text)\\s+to\\s+(.+)")) -> {
                val name = text.replace(Regex("^(send|text)\\s+(a\\s+)?(message|sms|text)\\s+to\\s+"), "")
                Command.SendSms(name, "")
            }

            // Open App
            text.matches(Regex("(open|launch|start)\\s+(.+)")) -> {
                val appName = text.replace(Regex("^(open|launch|start)\\s+"), "")
                Command.OpenApp(appName)
            }

            // Alarm & Timer
            text.matches(Regex(".*set\\s+(an?\\s+)?alarm.*")) -> {
                val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text)
                var hour = timeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 7
                val minute = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val amPm = timeMatch?.groupValues?.get(3)
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                Command.SetAlarm(hour, minute)
            }
            text.matches(Regex(".*set\\s+(a\\s+)?timer.*")) -> {
                val minutes = Regex("(\\d+)\\s*(minute|min)").find(text)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 5
                Command.SetTimer(minutes)
            }

            // Flashlight
            text.contains("flashlight") || text.contains("torch") || text.contains("flash") ->
                Command.ToggleFlashlight

            // WiFi
            text.matches(Regex(".*(turn|toggle|switch)\\s*(on|off)?\\s*(the)?\\s*wi-?fi.*")) ->
                Command.ToggleWifi

            // Bluetooth
            text.matches(Regex(".*(turn|toggle|switch)\\s*(on|off)?\\s*(the)?\\s*bluetooth.*")) ->
                Command.ToggleBluetooth

            // Volume
            text.matches(Regex(".*(increase|raise|up|louder)\\s*(the)?\\s*volume.*")) ||
            text.contains("volume up") -> Command.IncreaseVolume
            text.matches(Regex(".*(decrease|lower|down|quieter)\\s*(the)?\\s*volume.*")) ||
            text.contains("volume down") -> Command.DecreaseVolume
            text.matches(Regex(".*(mute|silent|silence).*")) -> Command.MuteVolume

            // Time & Date
            text.matches(Regex(".*(what('s|\\s+is)\\s+(the\\s+)?time|tell\\s+(me\\s+)?the\\s+time|current\\s+time).*")) ->
                Command.WhatTime
            text.matches(Regex(".*(what('s|\\s+is)\\s+(the\\s+)?(date|day)|tell\\s+(me\\s+)?the\\s+(date|day)|today('s)?\\s+date).*")) ->
                Command.WhatDate

            // Battery
            text.matches(Regex(".*(battery|charge|power)\\s*(level|percent|status)?.*")) ->
                Command.BatteryLevel

            // Music
            text.matches(Regex(".*(play|resume)\\s*(the)?\\s*(music|song|audio).*")) -> Command.PlayMusic
            text.matches(Regex(".*(pause|stop)\\s*(the)?\\s*(music|song|audio).*")) -> Command.PauseMusic
            text.matches(Regex(".*(next|skip)\\s*(track|song)?.*")) -> Command.NextTrack
            text.matches(Regex(".*(previous|back|last)\\s*(track|song)?.*")) -> Command.PreviousTrack

            // Camera
            text.matches(Regex(".*(take|capture)\\s+(a\\s+)?(photo|picture|selfie|pic).*")) ->
                Command.TakePhoto
            text.matches(Regex(".*(open|launch)\\s*(the)?\\s*camera.*")) -> Command.OpenCamera

            // Screenshot
            text.contains("screenshot") || text.contains("screen shot") ->
                Command.TakeScreenshot

            // Notifications
            text.matches(Regex(".*(read|show|check)\\s*(my)?\\s*notification.*")) ->
                Command.ReadNotifications

            // Search
            text.matches(Regex("(search|google|look up|find)\\s+(for\\s+)?(.+)")) -> {
                val query = text.replace(Regex("^(search|google|look up|find)\\s+(for\\s+)?"), "")
                Command.SearchWeb(query)
            }

            // Greeting
            text.matches(Regex(".*(hello|hi|hey|good\\s*(morning|afternoon|evening|night)|how\\s+are\\s+you).*")) ->
                Command.Greet

            // Default: treat as AI chat
            else -> Command.AiChat(input)
        }
    }
}
