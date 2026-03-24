package com.example.smartupiannotation.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.smartupiannotation.R
import com.example.smartupiannotation.databinding.LayoutFloatingOverlayBinding
import com.example.smartupiannotation.utils.AppUsageMonitor
import kotlinx.coroutines.*

class OverlayForegroundService : Service() {

    private val CHANNEL_ID = "OverlayServiceChannel"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val amount = intent?.getDoubleExtra("amount", 0.0) ?: 0.0
        val receiver = intent?.getStringExtra("receiver") ?: "Unknown"
        val upiPackage = intent?.getStringExtra("upi_package") ?: ""

        if (upiPackage.isNotEmpty()) {
            waitForAppExit(upiPackage, amount, receiver)
        } else {
            showOverlay(amount, receiver)
        }

        return START_NOT_STICKY
    }

    private fun waitForAppExit(packageName: String, amount: Double, receiver: String) {
        val monitor = AppUsageMonitor(this)
        serviceScope.launch {
            while (true) {
                val currentApp = monitor.getForegroundApp()
                if (currentApp != packageName) {
                    delay(500) // Small delay to ensure user is truly out
                    showOverlay(amount, receiver)
                    break
                }
                delay(2000)
            }
        }
    }

    private fun showOverlay(amount: Double, receiver: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        
        // Using ViewBinding if available, otherwise manual inflation
        overlayView = inflater.inflate(R.layout.layout_floating_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Setup UI interaction logic here...
        
        windowManager?.addView(overlayView, params)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart UPI Annotation")
            .setContentText("Monitoring transaction exit...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (overlayView != null) windowManager?.removeView(overlayView)
    }
}
