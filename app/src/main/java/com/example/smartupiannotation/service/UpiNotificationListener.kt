package com.example.smartupiannotation.service

import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.smartupiannotation.service.parser.TransactionParser
import com.example.smartupiannotation.utils.AppUsageMonitor

class UpiNotificationListener : NotificationListenerService() {

    private val TAG = "UpiNotificationListener"
    private lateinit var usageMonitor: AppUsageMonitor

    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging"
    )

    override fun onCreate() {
        super.onCreate()
        usageMonitor = AppUsageMonitor(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        val notificationPackage = sbn?.packageName ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullText = "$title $text"

        Log.d(TAG, "Notification from $notificationPackage: $fullText")

        val parsedData = TransactionParser.parse(fullText)
        
        if (parsedData != null) {
            Log.d(TAG, "Parsed Transaction: $parsedData")
            
            var packageToMonitor = notificationPackage
            
            if (SMS_PACKAGES.contains(notificationPackage)) {
                val foregroundApp = usageMonitor.getForegroundPackage()
                if (foregroundApp != null && foregroundApp != notificationPackage) {
                    Log.d(TAG, "SMS notification detected. Switching monitor target to active app: $foregroundApp")
                    packageToMonitor = foregroundApp
                }
            }

            val intent = Intent(this, OverlayForegroundService::class.java).apply {
                action = OverlayForegroundService.ACTION_START_MONITORING
                putExtra("amount", parsedData.amount)
                putExtra("receiver", parsedData.receiverName)
                putExtra("bankName", parsedData.bankName)
                putExtra("account", parsedData.maskedAccount)
                putExtra("upiPackage", packageToMonitor)
            }
            startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}
