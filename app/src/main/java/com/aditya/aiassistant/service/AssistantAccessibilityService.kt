package com.aditya.aiassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val text = event.text?.joinToString(" ") ?: return
                val packageName = event.packageName?.toString() ?: ""
                Log.d(TAG, "Notification from $packageName: $text")
                notificationCallback?.invoke(packageName, text)
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className?.toString() ?: ""
                val packageName = event.packageName?.toString() ?: ""
                Log.d(TAG, "Window changed: $packageName / $className")
            }

            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected — full phone control active")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // --- Touch & Gesture Controls ---

    fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun scrollDown() {
        performSwipe(540f, 1500f, 540f, 500f, 500)
    }

    fun scrollUp() {
        performSwipe(540f, 500f, 540f, 1500f, 500)
    }

    // --- Global Actions ---

    fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun openRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openNotificationShade() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings() {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    fun takeScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun openPowerDialog() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }

    fun splitScreen() {
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    }

    // --- UI Interaction ---

    fun findAndClickButton(buttonText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val found = findNodeByText(rootNode, buttonText)
        if (found != null) {
            found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            found.recycle()
            rootNode.recycle()
            return true
        }
        rootNode.recycle()
        return false
    }

    fun findAndTypeText(fieldHint: String, textToType: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val editField = findEditableField(rootNode, fieldHint)
        if (editField != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
            }
            editField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            editField.recycle()
            rootNode.recycle()
            return true
        }
        rootNode.recycle()
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(text, ignoreCase = true) || contentDesc.contains(text, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun findEditableField(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.isEditable) {
            val nodeHint = node.hintText?.toString() ?: node.text?.toString() ?: ""
            if (hint.isEmpty() || nodeHint.contains(hint, ignoreCase = true)) {
                return AccessibilityNodeInfo.obtain(node)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findEditableField(child, hint)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    // --- Read Screen ---

    fun getScreenText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val texts = mutableListOf<String>()
        collectAllTexts(rootNode, texts)
        rootNode.recycle()
        return texts.joinToString(", ")
    }

    fun getNotificationTexts(): List<String> {
        val notifications = mutableListOf<String>()
        try {
            val rootNode = rootInActiveWindow ?: return notifications
            collectNotificationTexts(rootNode, notifications)
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading notifications", e)
        }
        return notifications
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { if (it.isNotBlank()) texts.add(it.toString()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllTexts(child, texts)
            child.recycle()
        }
    }

    private fun collectNotificationTexts(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { texts.add(it.toString()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNotificationTexts(child, texts)
            child.recycle()
        }
    }

    companion object {
        private const val TAG = "AccessibilitySvc"
        var instance: AssistantAccessibilityService? = null
            private set

        var notificationCallback: ((String, String) -> Unit)? = null
    }
}
