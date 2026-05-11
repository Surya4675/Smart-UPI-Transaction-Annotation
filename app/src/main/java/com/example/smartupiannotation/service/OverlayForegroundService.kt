package com.example.smartupiannotation.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        val database = AppDatabase.getDatabase(this)
        repository = TransactionRepository(database.transactionDao())
        usageMonitor = AppUsageMonitor(this)
        syncManager = SyncManager(database.transactionDao())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Smart UPI: Monitoring transaction status..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_MONITORING) {
            val amount = intent.getDoubleExtra("amount", 0.0)
            val receiver = intent.getStringExtra("receiver") ?: "Unknown"
            val upiPackage = intent.getStringExtra("upiPackage") ?: ""
            val account = intent.getStringExtra("account")

            if (isMonitoring || overlayManager.isOverlayVisible()) {
                Log.d(TAG, "Already active or overlay visible. Ignoring.")
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
            // Optimization: Check if user was ALREADY in a UPI app very recently (last 30s)
            val searchPackages = if (packageHint.isNotEmpty()) UPI_PACKAGES + packageHint else UPI_PACKAGES
            val wasRecentlyInUpi = usageMonitor.wasAnyAppInForegroundRecently(searchPackages, 30000L)

            var hasEnteredUpi = wasRecentlyInUpi
            
            if (!hasEnteredUpi) {
                Log.d(TAG, "Phase 1: Waiting for user to enter UPI app")
                val entryTimeout = 45000L // Reduced timeout
                val entryStartTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - entryStartTime < entryTimeout) {
                    val currentApp = usageMonitor.getForegroundPackage()
                    if (currentApp != null && (UPI_PACKAGES.contains(currentApp) || currentApp == packageHint)) {
                        hasEnteredUpi = true
                        Log.d(TAG, "Phase 1 Complete: User entered UPI app")
                        break
                    }
                    delay(800) // Faster polling
                }
            } else {
                Log.d(TAG, "Phase 1 Skipped: User was recently in UPI app")
            }

            if (!hasEnteredUpi) {
                Log.d(TAG, "Phase 1 Timeout: No UPI activity detected")
                isMonitoring = false
                stopSelf()
                return@launch
            }

            // Phase 2: Wait for exit
            Log.d(TAG, "Phase 2: Monitoring for exit")
            var exitConfirmedCount = 0
            val requiredConfirmations = 1 // Immediate show on first non-UPI/non-interrupter detection
            var retries = 0
            val maxRetries = 200 // ~3 minutes

            while (exitConfirmedCount < requiredConfirmations && retries < maxRetries) {
                val currentApp = usageMonitor.getForegroundPackage()
                
                // If we can't determine app or it's an interrupter, just wait
                if (currentApp == null || INTERRUPTER_PACKAGES.any { currentApp.contains(it) }) {
                    delay(800)
                    retries++
                    continue
                }

                val isInUpi = UPI_PACKAGES.contains(currentApp) || currentApp == packageHint

                if (!isInUpi) {
                    exitConfirmedCount++
                } else {
                    exitConfirmedCount = 0 
                }

                delay(800)
                retries++
            }

            isMonitoring = false
            if (exitConfirmedCount >= requiredConfirmations) {
                Log.d(TAG, "Phase 2 Complete: Exit detected. Showing overlay.")
                showOverlay(amount, receiver, account)
            } else {
                Log.d(TAG, "Phase 2 Timeout.")
                if (!overlayManager.isOverlayVisible()) stopSelf()
            }
        }
    }

    private fun showOverlay(amount: Double, receiver: String, account: String?) {
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
        serviceScope.cancel()
        overlayManager.cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
