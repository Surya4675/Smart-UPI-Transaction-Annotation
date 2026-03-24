package com.example.smartupiannotation.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.smartupiannotation.service.parser.TransactionParser

class UpiNotificationListener : NotificationListenerService() {

    private val upiApps = setOf(
        "com.google.android.apps.nbu.paisa.user", // GPay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm"                        // Paytm
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        val packageName = sbn?.packageName ?: return
        if (!upiApps.contains(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()

        Log.d("UpiListener", "Notification from $packageName: $title - $text")

        val parsedTransaction = TransactionParser.parseNotification(title, text)
        
        if (parsedTransaction != null) {
            // Trigger the overlay service. 
            // In a real app, we might wait for the user to exit the UPI app using AppUsageMonitor.
            val intent = Intent(this, OverlayForegroundService::class.java).apply {
                putExtra("amount", parsedTransaction.amount)
                putExtra("receiver", parsedTransaction.receiverName)
                putExtra("upi_package", packageName)
            }
            startForegroundService(intent)
        }
    }
}
