package com.example.smartupiannotation.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
        const val NOTIFICATION_ID = 1001 // Unique ID
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        overlayManager = OverlayManager(this)
        val database = AppDatabase.getDatabase(this)
        repository = TransactionRepository(database.transactionDao())
        usageMonitor = AppUsageMonitor(this)
        syncManager = SyncManager(database.transactionDao())
        createNotificationChannel()
        
        // Start foreground immediately in onCreate for better stability
        startForeground(NOTIFICATION_ID, createNotification("Smart UPI: Monitoring transaction status..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action: ${intent?.action}")

        if (intent?.action == ACTION_START_MONITORING) {
            val amount = intent.getDoubleExtra("amount", 0.0)
            val receiver = intent.getStringExtra("receiver") ?: "Unknown"
            val upiPackage = intent.getStringExtra("upiPackage") ?: ""
            val account = intent.getStringExtra("account")

            if (isMonitoring || overlayManager.isOverlayVisible()) {
                Log.d(TAG, "Already active. Ignoring duplicate request.")
                return START_STICKY
            }

            isMonitoring = true
            monitorAppExit(upiPackage, amount, receiver, account)
        }
        
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayManager.refreshThemeIfVisible(newConfig)
    }

    private fun monitorAppExit(packageHint: String, amount: Double, receiver: String, account: String?) {
        serviceScope.launch {
            Log.d(TAG, "Phase 1: Waiting for user to enter UPI app ($packageHint)")
            
            var hasEnteredUpi = false
            val entryTimeout = 60000L // 1 minute to enter
            val entryStartTime = System.currentTimeMillis()
            
            // Step 1: Wait for entry
            while (System.currentTimeMillis() - entryStartTime < entryTimeout) {
                val currentApp = usageMonitor.getForegroundPackage()
                if (currentApp != null && (UPI_PACKAGES.contains(currentApp) || currentApp == packageHint)) {
                    hasEnteredUpi = true
                    Log.d(TAG, "Phase 1 Complete: User is in UPI app")
                    break
                }
                delay(1000)
            }

            if (!hasEnteredUpi) {
                Log.d(TAG, "Phase 1 Timeout: User never entered UPI app")
                isMonitoring = false
                stopSelf()
                return@launch
            }

            // Step 2: Once entered, wait for confirmed exit (3 consecutive seconds)
            Log.d(TAG, "Phase 2: Waiting for exit from UPI app")
            var exitConfirmedCount = 0
            val requiredConfirmations = 3 
            var retries = 0
            val maxRetries = 300 // 5 minutes total monitoring

            while (exitConfirmedCount < requiredConfirmations && retries < maxRetries) {
                val currentApp = usageMonitor.getForegroundPackage()
                
                // Skip transient interrupter apps (Notifications, System UI)
                if (currentApp == null || INTERRUPTER_PACKAGES.any { currentApp.contains(it) }) {
                    delay(1000)
                    retries++
                    continue
                }

                val isInUpi = currentApp != null && (UPI_PACKAGES.contains(currentApp) || currentApp == packageHint)

                if (!isInUpi) {
                    exitConfirmedCount++
                } else {
                    exitConfirmedCount = 0 // User is still active in UPI
                }

                delay(1000)
                retries++
            }

            isMonitoring = false
            if (exitConfirmedCount >= requiredConfirmations) {
                Log.d(TAG, "Phase 2 Complete: Exit confirmed. Showing persistent overlay.")
                showOverlay(amount, receiver, account)
            } else {
                Log.d(TAG, "Phase 2 Cancelled: Monitoring timed out.")
                if (!overlayManager.isOverlayVisible()) {
                    stopSelf()
                }
            }
        }
    }

    private fun showOverlay(amount: Double, receiver: String, account: String?) {
        // Update notification to indicate user action is needed
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification("Transaction annotation required - tap to open"))

        overlayManager.showOverlay(
            amount = amount,
            receiver = receiver,
            onSave = { finalAmount, finalReceiver, note, participants ->
                saveTransaction(finalAmount, finalReceiver, account, note, participants)
            },
            onDismiss = {
                Log.d(TAG, "Overlay dismissed by user")
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
                Log.d(TAG, "Transaction saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving transaction", e)
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
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "UPI Transaction Monitoring",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps the monitor active until you annotate the transaction"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        serviceScope.cancel()
        overlayManager.cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
