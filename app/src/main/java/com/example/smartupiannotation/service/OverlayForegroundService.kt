package com.example.smartupiannotation.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smartupiannotation.MainActivity
import com.example.smartupiannotation.R
import com.example.smartupiannotation.data.local.AppDatabase
import com.example.smartupiannotation.data.local.ParticipantEntity
import com.example.smartupiannotation.data.local.TransactionEntity
import com.example.smartupiannotation.data.remote.SyncManager
import com.example.smartupiannotation.data.repository.TransactionRepository
import com.example.smartupiannotation.ui.OverlayManager
import com.example.smartupiannotation.utils.AppUsageMonitor
import kotlinx.coroutines.*

class OverlayForegroundService : Service() {

    companion object {
        const val TAG = "OverlayService"
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"

        private val UPI_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user", // GPay
            "com.phonepe.app",                        // PhonePe
            "com.naviapp",                            // Navi
            "net.one97.paytm",                        // Paytm
            "com.upi.axispay",                        // BHIM
            "in.amazon.mShop.android.shopping",       // Amazon Pay
            "com.freecharge.android",                 // Freecharge
            "com.msf.kbank.mobile"                    // Kotak
        )

        private val INTERRUPTER_PACKAGES = setOf(
            "com.android.systemui",
            "com.truecaller",
            "com.google.android.apps.messaging",
            "com.whatsapp",
            "android",
            "com.android.settings"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var overlayManager: OverlayManager
    private lateinit var repository: TransactionRepository
    private lateinit var usageMonitor: AppUsageMonitor
    private lateinit var syncManager: SyncManager
    private var isMonitoring = false
    
    private var pendingAmount: Double = 0.0
    private var pendingReceiver: String = ""
    private var pendingAccount: String? = null
    private var monitoredUpiPackage: String = ""

    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.getStringExtra(UpiAccessibilityService.EXTRA_PACKAGE_NAME) ?: return
            handleAppChange(packageName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        val database = AppDatabase.getDatabase(this)
        repository = TransactionRepository(database.transactionDao())
        usageMonitor = AppUsageMonitor(this)
        syncManager = SyncManager(database.transactionDao())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Smart UPI: Monitoring transaction status..."))
        
        LocalBroadcastManager.getInstance(this).registerReceiver(
            appChangeReceiver, 
            IntentFilter(UpiAccessibilityService.ACTION_APP_CHANGED)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_MONITORING) {
            pendingAmount = intent.getDoubleExtra("amount", 0.0)
            pendingReceiver = intent.getStringExtra("receiver") ?: "Unknown"
            monitoredUpiPackage = intent.getStringExtra("upiPackage") ?: ""
            pendingAccount = intent.getStringExtra("account")

            if (isMonitoring || overlayManager.isOverlayVisible()) {
                Log.d(TAG, "Already active or overlay visible.")
                return START_STICKY
            }

            isMonitoring = true
            startMonitoringLogic()
        }
        return START_STICKY
    }

    private fun startMonitoringLogic() {
        serviceScope.launch {
            val searchPackages = if (monitoredUpiPackage.isNotEmpty()) UPI_PACKAGES + monitoredUpiPackage else UPI_PACKAGES
            
            // Initial check: if user is already out of UPI
            val currentApp = usageMonitor.getForegroundPackage()
            if (currentApp != null && !searchPackages.contains(currentApp) && !INTERRUPTER_PACKAGES.any { currentApp.contains(it) }) {
                Log.d(TAG, "Instant Detection: User already exited UPI app. Showing overlay.")
                triggerOverlay()
            }
        }
    }

    private fun handleAppChange(currentPackage: String) {
        if (!isMonitoring) return

        val searchPackages = if (monitoredUpiPackage.isNotEmpty()) UPI_PACKAGES + monitoredUpiPackage else UPI_PACKAGES

        // If the user moves to a non-UPI app and it's not a system interrupter
        if (!searchPackages.contains(currentPackage) && !INTERRUPTER_PACKAGES.any { currentPackage.contains(it) }) {
            Log.d(TAG, "Accessibility Detection: User exited UPI app ($currentPackage). Showing overlay.")
            triggerOverlay()
        }
    }

    private fun triggerOverlay() {
        isMonitoring = false
        showOverlay(pendingAmount, pendingReceiver, pendingAccount)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager.refreshThemeIfVisible(newConfig)
    }

    private fun showOverlay(amount: Double, receiver: String, account: String?) {
        if (overlayManager.isOverlayVisible()) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification("Transaction annotation required - tap to open"))

        overlayManager.showOverlay(
            amount = amount,
            receiver = receiver,
            onSave = { finalAmount, finalReceiver, note, participants ->
                saveTransaction(finalAmount, finalReceiver, account, note, participants)
            },
            onDismiss = {
                Log.d(TAG, "Overlay dismissed")
                stopSelf()
            }
        )
    }

    private fun saveTransaction(amount: Double, receiver: String, account: String?, note: String, participants: List<ParticipantEntity>) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val transaction = TransactionEntity(
                    amount = amount,
                    receiverName = receiver,
                    transactionDate = System.currentTimeMillis(),
                    maskedAccount = account,
                    category = "Uncategorized",
                    note = note
                )
                repository.insertTransaction(transaction, participants)
                syncManager.saveToCloud(transaction, participants)
            } catch (e: Exception) {
                Log.e(TAG, "Save error", e)
            } finally {
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart UPI Annotation")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "UPI Transaction Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(appChangeReceiver)
        serviceScope.cancel()
        overlayManager.cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
