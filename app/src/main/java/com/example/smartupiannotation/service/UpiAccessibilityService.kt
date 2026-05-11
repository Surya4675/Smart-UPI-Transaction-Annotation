package com.example.smartupiannotation.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class UpiAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_APP_CHANGED = "com.example.smartupiannotation.APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            val intent = Intent(ACTION_APP_CHANGED).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Configuration can also be done in XML
    }
}
