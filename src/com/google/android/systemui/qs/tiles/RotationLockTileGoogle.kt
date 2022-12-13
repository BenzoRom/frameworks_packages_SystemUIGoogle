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

import android.hardware.SensorPrivacyManager
import android.os.Handler
import android.os.Looper
import com.android.internal.logging.MetricsLogger
import com.android.systemui.KtR
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tiles.RotationLockTile
import com.android.systemui.statusbar.policy.BatteryController
import com.android.systemui.statusbar.policy.DevicePostureController
import com.android.systemui.statusbar.policy.RotationLockController
import com.android.systemui.statusbar.policy.dagger.StatusBarPolicyModule.DEVICE_STATE_ROTATION_LOCK_DEFAULTS
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import javax.inject.Named

class RotationLockTileGoogle
@Inject
constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    rotationLockController: RotationLockController,
    sensorPrivacyManager: SensorPrivacyManager,
    batteryController: BatteryController,
    secureSettings: SecureSettings,
    @Named(DEVICE_STATE_ROTATION_LOCK_DEFAULTS)
    private val deviceStateRotationLockDefaults: Array<String?>,
    private val controller: DevicePostureController,
) :
    RotationLockTile(
        host,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger,
        rotationLockController,
        sensorPrivacyManager,
        batteryController,
        secureSettings
    ) {
    private val isPerDeviceStateRotationLockEnabled: Boolean
        get() = deviceStateRotationLockDefaults.isNotEmpty()

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        super.handleUpdateState(state, arg)
        if (isPerDeviceStateRotationLockEnabled) {
            val secondaryLabelWithPosture = getSecondaryLabelWithPosture(state)
            state.secondaryLabel = secondaryLabelWithPosture
            state.stateDescription = secondaryLabelWithPosture
        }
    }

    private fun getSecondaryLabelWithPosture(state: QSTile.BooleanState): CharSequence {
        val builder = StringBuilder()
        builder.append(
            mContext.resources.getStringArray(KtR.array.tile_states_rotation)[state.state]
        )
        builder.append(" / ")
        when (controller.devicePosture) {
            DevicePostureController.DEVICE_POSTURE_CLOSED -> {
                builder.append(
                    mContext.getString(KtR.string.quick_settings_rotation_posture_folded)
                )
            }
            else -> {
                builder.append(
                    mContext.getString(KtR.string.quick_settings_rotation_posture_unfolded)
                )
            }
        }
        return builder.toString()
    }
}
