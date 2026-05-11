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
        return getForegroundPackage() == packageName
    }

    /**
     * Gets the package name of the app currently in the foreground.
     * Uses a very small window (10s) for maximum speed.
     */
    fun getForegroundPackage(): String? {
        val time = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(time - 1000 * 10, time) 
        val event = UsageEvents.Event()
        var lastPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            // Catching both MOVE_TO_FOREGROUND and ACTIVITY_RESUMED for better compatibility
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }

        if (lastPackage == null) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
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
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || 
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
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
            time - 1000 * 5,
            time
        )
        return !stats.isNullOrEmpty()
    }
}
