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
        return currentPackage == packageName
    }

    /**
     * Gets the package name of the app currently in the foreground.
     */
    fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        // Reduced window to 30 seconds for better performance
        val usageEvents = usageStatsManager.queryEvents(time - 1000 * 30, time) 
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }

        if (lastPackage == null) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 5,
                time
            )
            lastPackage = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        return lastPackage
    }

    /**
     * Checks if any of the given packages were in the foreground in the last [durationMs].
     */
    fun wasAnyAppInForegroundRecently(packages: Set<String>, durationMs: Long): Boolean {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - durationMs, time)
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (packages.contains(event.packageName)) {
                    return true
                }
            }
        }
        return false
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
