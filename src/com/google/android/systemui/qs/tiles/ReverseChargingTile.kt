/*
 * Copyright (C) 2022 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.systemui.qs.tiles

import android.content.Intent
import android.database.ContentObserver
import android.os.*
import android.os.Temperature.THROTTLING_EMERGENCY
import android.os.Temperature.TYPE_SKIN
import android.provider.Settings
import android.service.quicksettings.Tile
import android.util.Log
import android.view.View
import android.widget.Switch
import com.android.internal.logging.MetricsLogger
import com.android.systemui.KtR
import com.android.systemui.Prefs
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.statusbar.policy.BatteryController
import javax.inject.Inject

class ReverseChargingTile
@Inject
constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    private val activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val batteryController: BatteryController,
    private val thermalService: IThermalService
) :
    QSTileImpl<QSTile.BooleanState>(
        host,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger
    ),
    BatteryController.BatteryStateChangeCallback {
    private var batteryLevel = 0
    private var listen = false
    private var overHeat = false
    private var powerSave = false
    private var reverse = false
    private val settingsObserver: ContentObserver
    private val thermalEventListener: IThermalEventListener
    private var thresholdLevel = 0

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState().also {
            it.state = Tile.STATE_UNAVAILABLE
        }
    }

    public override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (listen != listening) {
            listen = listening
            if (listening) {
                updateThresholdLevel()
                mContext.contentResolver.registerContentObserver(
                    Settings.Global.getUriFor("advanced_battery_usage_amount"),
                    false,
                    settingsObserver
                )
                try {
                    thermalService.registerThermalEventListenerWithType(
                        thermalEventListener,
                        TYPE_SKIN
                    )
                } catch (ex: RemoteException) {
                    Log.e(TAG, "Could not register thermal event listener, exception: $ex")
                }
                overHeat = isOverHeat
            } else {
                mContext.contentResolver.unregisterContentObserver(settingsObserver)
                try {
                    thermalService.unregisterThermalEventListener(thermalEventListener)
                } catch (ex: RemoteException) {
                    Log.e(TAG, "Could not unregister thermal event listener, exception: $ex")
                }
            }
            if (DEBUG)
                Log.d(
                    TAG,
                    "handleSetListening(): rtx=${if (reverse) 1 else 0}," +
                        "level=$batteryLevel," +
                        "threshold=$thresholdLevel," +
                        "listening=$listening"
                )
        }
    }

    override fun isAvailable(): Boolean {
        return batteryController.isReverseSupported
    }

    override fun getLongClickIntent(): Intent {
        val intent = Intent("android.settings.REVERSE_CHARGING_SETTINGS")
        intent.setPackage("com.android.settings")
        return intent
    }

    override fun handleClick(view: View?) {
        if (state?.state != Tile.STATE_UNAVAILABLE) {
            reverse = !reverse
            if (DEBUG) Log.d(TAG, "handleClick(): rtx=${if (reverse) 1 else 0},this=$this")
            with(batteryController) { setReverseState(reverse) }
            showBottomSheetIfNecessary()
        }
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(KtR.string.reverse_charging_title)
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val isWirelessCharging = batteryController.isWirelessCharging
        val lowBattery = if (batteryLevel <= thresholdLevel) 1 else 0
        val isReverseAvailable = !overHeat && !powerSave && !isWirelessCharging && lowBattery == 0
        when {
            !isReverseAvailable -> Tile.STATE_UNAVAILABLE
            reverse             -> Tile.STATE_ACTIVE
            else                -> Tile.STATE_INACTIVE
        }.also { state.state = it }
        state.icon = ResourceIcon.get(KtR.drawable.ic_qs_reverse_charging)
        state.label = tileLabel
        state.contentDescription = tileLabel
        state.expandedAccessibilityClassName = Switch::class.java.name
        when {
            overHeat           -> mContext.getString(KtR.string.too_hot_label)
            powerSave          -> mContext.getString(KtR.string.quick_settings_dark_mode_secondary_label_battery_saver)
            isWirelessCharging -> mContext.getString(KtR.string.wireless_charging_label)
            lowBattery != 0    -> mContext.getString(KtR.string.low_battery_label)
            else               -> null
        }.also { state.secondaryLabel = it }
        state.value = isReverseAvailable && reverse
        if (DEBUG)
            Log.d(
                TAG,
                "handleUpdateState(): ps=${if (powerSave) 1 else 0}," +
                    "wlc=${if (isWirelessCharging) 1 else 0},low=$lowBattery," +
                    "over=${if (overHeat) 1 else 0},rtx=${if (reverse) 1 else 0}," +
                    "this=$this"
            )
    }

    override fun onBatteryLevelChanged(
        level: Int,
        pluggedIn: Boolean,
        charging: Boolean
    ) {
        batteryLevel = level
        reverse = batteryController.isReverseOn
        if (DEBUG)
            Log.d(
                TAG,
                "onBatteryLevelChanged(): rtx=${if (reverse) 1 else 0}," +
                    "level=$batteryLevel,threshold=$thresholdLevel"
            )
        refreshState()
    }

    override fun onPowerSaveChanged(isPowerSave: Boolean) {
        powerSave = isPowerSave
        refreshState()
    }

    override fun onReverseChanged(
        isReverse: Boolean,
        level: Int,
        name: String?
    ) {
        if (DEBUG)
            Log.d(
                TAG,
                "onReverseChanged(): rtx=${if (isReverse) 1 else 0}," +
                    "level=$level,name=$name,this=$this"
            )
        reverse = isReverse
        refreshState()
    }

    private fun showBottomSheetIfNecessary() {
        if (!Prefs.getBoolean(mHost.userContext, "HasSeenReverseBottomSheet", false)) {
            with(activityStarter) {
                postStartActivityDismissingKeyguard(
                    Intent("android.settings.REVERSE_CHARGING_BOTTOM_SHEET")
                        .setPackage("com.android.settings"), 0
                )
            }
            Prefs.putBoolean(mHost.userContext, "HasSeenReverseBottomSheet", true)
        }
    }

    private fun updateThresholdLevel() {
        thresholdLevel = Settings.Global.getInt(
            mContext.contentResolver,
            "advanced_battery_usage_amount", 2
        ) * 5
        if (DEBUG)
            Log.d(
                TAG,
                "updateThresholdLevel(): rtx=${if (reverse) 1 else 0}," +
                    "level=$batteryLevel,threshold=$thresholdLevel"
            )
    }

    private val isOverHeat: Boolean
        get() {
            thermalService.getCurrentTemperaturesWithType(TYPE_SKIN).forEach {
                if (it.status >= THROTTLING_EMERGENCY) {
                    Log.w(
                        TAG,
                        "isOverHeat(): current skin status = " +
                            "${it.status},temperature = ${it.value}"
                    )
                    return true
                }
            }
            return false
        }

    init {
        batteryController.observe(lifecycle, this)

        object : ContentObserver(mHandler) {
                override fun onChange(selfChange: Boolean) {
                    updateThresholdLevel()
                }
            }.also { settingsObserver = it }

        object : IThermalEventListener.Stub() {
                override fun notifyThrottling(temp: Temperature) {
                    val status = temp.status
                    overHeat = status >= THROTTLING_EMERGENCY
                    if (DEBUG) {
                        Log.d(TAG, "notifyThrottling(): status=$status")
                    }
                }
            }.also { thermalEventListener = it }
    }
}
