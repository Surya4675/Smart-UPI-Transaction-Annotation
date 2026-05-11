package com.example.smartupiannotation.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

class AppUsageMonitor(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Checks if the specified package is currently in the foreground.
     */
    fun isAppForeground(packageName: String): Boolean {
        val currentPackage = getForegroundPackage()
        Log.d("AppUsageMonitor", "Target: $packageName, Current: $currentPackage")
        return currentPackage == packageName
    }

    /**
     * Gets the package name of the app currently in the foreground using UsageEvents.
     * This is generally more accurate than queryUsageStats for real-time monitoring.
     */
    fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 1000 * 60, time) // Check last 60 seconds
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }

        if (lastPackage == null) {
            // Fallback to queryUsageStats if no events found in last minute
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 5,
                time
            )
            lastPackage = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        return lastPackage
    }

    fun hasUsageStatsPermission(): Boolean {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return !stats.isNullOrEmpty()
    }
}
