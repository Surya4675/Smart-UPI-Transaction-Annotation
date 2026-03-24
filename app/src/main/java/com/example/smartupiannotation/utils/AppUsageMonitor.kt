package com.example.smartupiannotation.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

class AppUsageMonitor(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Checks if a specific app is currently in the foreground.
     * Note: Requires PACKAGE_USAGE_STATS permission.
     */
    fun isAppInForeground(packageName: String): Boolean {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60,
            time
        )
        
        if (stats != null) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                return sortedStats[0].packageName == packageName
            }
        }
        return false
    }

    /**
     * Returns the package name of the current foreground app.
     */
    fun getForegroundApp(): String? {
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
