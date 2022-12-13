package com.google.android.systemui.power

import android.content.Context
import android.content.pm.PackageManager.*
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.util.KeyValueListParser
import android.util.Log
import com.android.settingslib.fuelgauge.Estimate
import com.android.settingslib.utils.PowerUtil
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.power.EnhancedEstimates
import java.time.Duration
import javax.inject.Inject

@SysUISingleton
class EnhancedEstimatesGoogleImpl @Inject constructor(
    private var mContext: Context
) : EnhancedEstimates {
    private val keyParser = KeyValueListParser(',')
    override fun isHybridNotificationEnabled(): Boolean {
        return try {
            when {
                !mContext.packageManager
                    .getPackageInfo(
                        "com.google.android.apps.turbo",
                        PackageInfoFlags.of(MATCH_DISABLED_COMPONENTS.toLong())
                    )
                    .applicationInfo
                    .enabled -> return false
            }
            updateFlags()
            keyParser.getBoolean("hybrid_enabled", true)
        } catch (ex: NameNotFoundException) {
            false
        }
    }

    override fun getEstimate(): Estimate {
        var averageDischargeTime = -1L
        var query: Cursor? = null
        try {
            query =
                mContext.contentResolver.query(
                    Uri.Builder()
                        .scheme("content")
                        .authority("com.google.android.apps.turbo.estimated_time_remaining")
                        .appendPath("time_remaining")
                        .build(),
                    null, null, null, null
                )
        } catch (ex: Exception) {
            Log.d(logTag, "Something went wrong when getting an estimate from Turbo", ex)
        }
        when {
            query == null || !query.moveToFirst() -> {
                query?.close()
                return Estimate(-1L, false, -1L)
            }
            else -> {
                val averageBatteryLife = query.getColumnIndex("average_battery_life").toLong()
                val estimateMillis = query.getColumnIndex("battery_estimate").toLong()
                val isBasedOnUsage = query.getColumnIndex("is_based_on_usage") == 1
                if (averageBatteryLife != -1L) {
                    PowerUtil.roundTimeToNearestThreshold(
                            averageBatteryLife,
                            when {
                                Duration.ofMillis(averageBatteryLife) >= Duration.ofDays(1L) ->
                                    Duration.ofHours(1L).toMillis()
                                else -> Duration.ofMinutes(15L).toMillis()
                            }
                        ).also { averageDischargeTime = it }
                }
                query.close()
                return Estimate(
                    estimateMillis,
                    isBasedOnUsage,
                    averageDischargeTime
                )
            }
        }
    }

    override fun getLowWarningEnabled(): Boolean {
        updateFlags()
        return keyParser.getBoolean("low_warning_enabled", false)
    }

    override fun getLowWarningThreshold(): Long {
        updateFlags()
        return keyParser.getLong("low_threshold", Duration.ofHours(3L).toMillis())
    }

    override fun getSevereWarningThreshold(): Long {
        updateFlags()
        return keyParser.getLong("severe_threshold", Duration.ofHours(1L).toMillis())
    }

    private fun updateFlags() {
        try {
            keyParser.setString(
                Settings.Global.getString(
                    mContext.contentResolver,
                    SYSUI_BATTERY_WARNING_FLAGS
                )
            )
        } catch (ex: IllegalArgumentException) {
            Log.e(logTag, "Bad hybrid sysui warning flags")
        }
    }

    companion object {
        private const val logTag = "EnhancedEstimatesGoogleImpl"
        private const val SYSUI_BATTERY_WARNING_FLAGS = "hybrid_sysui_battery_warning_flags"
    }
}
