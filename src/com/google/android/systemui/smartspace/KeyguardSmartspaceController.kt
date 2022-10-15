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
package com.google.android.systemui.smartspace

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.flags.Flags.SMARTSPACE
import javax.inject.Inject

@SysUISingleton
class KeyguardSmartspaceController @Inject constructor(
    context: Context,
    featureFlags: FeatureFlags,
    zenController: KeyguardZenAlarmViewController,
    mediaController: KeyguardMediaViewController
) {
    init {
        when {
            featureFlags.isEnabled(SMARTSPACE) -> {
                zenController.init()
                mediaController.init()
            }
            else -> {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(
                        "com.android.systemui",
                        "com.google.android.systemui.keyguard.KeyguardSliceProviderGoogle"
                    ),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
