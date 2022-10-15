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
package com.google.android.systemui.statusbar.phone.dagger

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.phone.CentralSurfaces
import com.android.systemui.statusbar.policy.BatteryController
import com.google.android.systemui.reversecharging.ReverseChargingViewController
import com.google.android.systemui.statusbar.phone.CentralSurfacesGoogle
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import java.util.Optional

/**
 * Dagger Module providing [CentralSurfacesGoogle].
 */
@Module
interface StatusBarGooglePhoneModule {
    /**
     * Provides our instance of CentralSurfacesGoogle which is considered optional.
     */
    @Binds
    @SysUISingleton
    fun bindsCentralSurfaces(impl: CentralSurfacesGoogle): CentralSurfaces

    @Module
    companion object {
        /**
         * Provides optional ReverseChargingViewController for CentralSurfacesGoogle.
         */
        @Provides
        @SysUISingleton
        fun provideReverseChargingViewControllerOptional(
            batteryController: BatteryController,
            reverseChargingViewControllerLazy: Lazy<ReverseChargingViewController>
        ): Optional<ReverseChargingViewController> {
            return if (batteryController.isReverseSupported) {
                Optional.of(reverseChargingViewControllerLazy.get())
            } else Optional.empty()
        }
    }
}
