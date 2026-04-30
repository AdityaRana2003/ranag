package com.aditya.aiassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class AdityaAIApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Adi running in the background"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // High-priority channel for call/SMS announcements
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Call and message announcements"
        }
        notificationManager.createNotificationChannel(alertChannel)
    }

    companion object {
        const val CHANNEL_ID = "aditya_ai_channel"
        const val ALERT_CHANNEL_ID = "aditya_ai_alerts"
        lateinit var instance: AdityaAIApp
            private set
    }
}
