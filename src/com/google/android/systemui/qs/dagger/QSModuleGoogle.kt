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
package com.google.android.systemui.qs.dagger

import android.content.Context
import android.hardware.display.NightDisplayListener
import android.os.Handler
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.qs.AutoAddTracker
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QSTileHost
import com.android.systemui.qs.ReduceBrightColorsController
import com.android.systemui.qs.dagger.QSFlagsModule
import com.android.systemui.qs.dagger.QSFragmentComponent
import com.android.systemui.qs.external.QSExternalModule
import com.android.systemui.statusbar.phone.AutoTileManager
import com.android.systemui.statusbar.phone.ManagedProfileController
import com.android.systemui.statusbar.policy.*
import com.android.systemui.util.settings.SecureSettings
import com.google.android.systemui.statusbar.phone.AutoTileManagerGoogle
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Named

/** Module for Google QS dependencies */
@Module(
    subcomponents = [QSFragmentComponent::class],
    includes = [
        MediaModule::class,
        QSExternalModule::class,
        QSFlagsModule::class
    ])
interface QSModuleGoogle {
    /** QSTileHost */
    @Binds fun provideQsHost(controllerImpl: QSTileHost): QSHost

    @Module
    companion object {
        @Provides
        fun provideAutoTileManager(
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
            batteryController: BatteryController,
            reduceBrightColorsController: ReduceBrightColorsController,
            deviceControlsController: DeviceControlsController,
            walletController: WalletController,
            safetyController: SafetyController,
            @Named(QSFlagsModule.RBC_AVAILABLE) isReduceBrightColorsAvailable: Boolean
        ): AutoTileManager {
            return AutoTileManagerGoogle(
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
                    batteryController,
                    reduceBrightColorsController,
                    deviceControlsController,
                    walletController,
                    safetyController,
                    isReduceBrightColorsAvailable
                ).also(AutoTileManagerGoogle::init)
        }
    }
}
