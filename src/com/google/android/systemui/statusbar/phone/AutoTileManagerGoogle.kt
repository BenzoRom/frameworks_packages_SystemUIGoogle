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
package com.google.android.systemui.statusbar.phone

import android.content.Context
import android.hardware.display.NightDisplayListener
import android.os.Build.IS_DEBUGGABLE as isDebuggable
import android.os.Handler
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.AutoAddTracker
import com.android.systemui.qs.QSTileHost
import com.android.systemui.qs.ReduceBrightColorsController
import com.android.systemui.qs.dagger.QSFlagsModule
import com.android.systemui.statusbar.phone.AutoTileManager
import com.android.systemui.statusbar.phone.ManagedProfileController
import com.android.systemui.statusbar.policy.*
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Named

class AutoTileManagerGoogle(
    context: Context,
    autoAddTrackerBuilder: AutoAddTracker.Builder,
    host: QSTileHost,
    @Background handler: Handler,
    secureSettings: SecureSettings,
    hotspotController: HotspotController,
    dataSaverController: DataSaverController,
    managedProfileController: ManagedProfileController,
    nightDisplayListener: NightDisplayListener,
    castController: CastController,
    private val batteryController: BatteryController,
    reduceBrightColorsController: ReduceBrightColorsController,
    deviceControlsController: DeviceControlsController,
    walletController: WalletController,
    safetyController: SafetyController,
    @Named(QSFlagsModule.RBC_AVAILABLE) isReduceBrightColorsAvailable: Boolean
) :
    AutoTileManager(
        context,
        autoAddTrackerBuilder,
        host,
        handler,
        secureSettings,
        hotspotController,
        dataSaverController,
        managedProfileController,
        nightDisplayListener,
        castController,
        reduceBrightColorsController,
        deviceControlsController,
        walletController,
        safetyController,
        isReduceBrightColorsAvailable
    ) {
    private val batteryControllerCallback: BatteryStateChangeCallback
        get() =
            object : BatteryStateChangeCallback {
                override fun onReverseChanged(isReverse: Boolean, level: Int, name: String?) {
                    if (!mAutoTracker.isAdded(reverseChargingTile) && isReverse) {
                        with(mHost) { addTile(reverseChargingTile) }
                        with(mAutoTracker) { setTileAdded(reverseChargingTile) }
                        mHandler.post { batteryController.removeCallback(this) }
                    }
                }
            }
    override fun init() {
        super.init()
        if (!mAutoTracker.isAdded(overlayToggleTile) && isDebuggable) {
            with(mHost) { addTile(overlayToggleTile) }
            with(mAutoTracker) { setTileAdded(overlayToggleTile) }
        }
    }

    public override fun startControllersAndSettingsListeners() {
        super.startControllersAndSettingsListeners()
        if (!mAutoTracker.isAdded(reverseChargingTile)) {
            with(batteryController) { addCallback(batteryControllerCallback) }
        }
    }

    public override fun stopListening() {
        super.stopListening()
        with(batteryController) { removeCallback(batteryControllerCallback) }
    }

    companion object {
        private const val overlayToggleTile: String = "ott"
        private const val reverseChargingTile: String = "reverse"
    }
}
