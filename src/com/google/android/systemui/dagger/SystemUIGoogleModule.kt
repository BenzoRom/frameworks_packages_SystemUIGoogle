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
package com.google.android.systemui.dagger

import android.content.Context
import android.content.res.Resources
import android.hardware.SensorPrivacyManager
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.IThermalService
import android.os.PowerManager
import android.os.ServiceManager
import com.android.internal.logging.UiEventLogger
import com.android.keyguard.KeyguardViewController
import com.android.systemui.Dependency.*
import com.android.systemui.KtR
import com.android.systemui.biometrics.AlternateUdfpsTouchProvider
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsDisplayModeProvider
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.GlobalRootComponent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.dock.DockManager
import com.android.systemui.dock.DockManagerImpl
import com.android.systemui.doze.DozeHost
import com.android.systemui.dump.DumpManager
import com.android.systemui.media.dagger.MediaModule
import com.android.systemui.navigationbar.gestural.GestureModule
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.qs.QSFactory
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.EnhancedEstimates
import com.android.systemui.recents.Recents
import com.android.systemui.recents.RecentsImplementation
import com.android.systemui.settings.UserContentResolverProvider
import com.android.systemui.shade.NotificationShadeWindowControllerImpl
import com.android.systemui.shade.ShadeController
import com.android.systemui.shade.ShadeControllerImpl
import com.android.systemui.statusbar.*
import com.android.systemui.statusbar.notification.collection.provider.VisualStabilityProvider
import com.android.systemui.statusbar.notification.collection.render.GroupMembershipManager
import com.android.systemui.statusbar.phone.*
import com.android.systemui.statusbar.policy.*
import com.android.systemui.volume.dagger.VolumeModule
import com.android.systemui.util.concurrency.Execution
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle
import com.google.android.systemui.fingerprint.FingerprintExtProvider
import com.google.android.systemui.fingerprint.UdfpsDisplayMode
import com.google.android.systemui.fingerprint.UdfpsTouchProvider
import com.google.android.systemui.gesture.GestureModuleGoogle
import com.google.android.systemui.power.dagger.PowerModuleGoogle
import com.google.android.systemui.qs.dagger.QSModuleGoogle
import com.google.android.systemui.qs.tileimpl.QSFactoryImplGoogle
import com.google.android.systemui.reversecharging.ReverseChargingController
import com.google.android.systemui.reversecharging.ReverseWirelessCharger
import com.google.android.systemui.screenshot.ScreenshotModuleGoogle
import com.google.android.systemui.smartspace.BcSmartspaceDataProvider
import com.google.android.systemui.smartspace.dagger.SmartspaceGoogleModule
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle
import com.google.android.systemui.statusbar.dagger.StartCentralSurfacesGoogleModule
import com.google.android.systemui.statusbar.policy.BatteryControllerImplGoogle
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.*
import javax.inject.Named

/**
 * A dagger module for overriding the default implementations of
 * injected System UI components for System UI Google.
 */
@Module(
    includes = [
        GestureModule::class,
        GestureModuleGoogle::class,
        MediaModule::class,
        PowerModuleGoogle::class,
        QSModuleGoogle::class,
        ScreenshotModuleGoogle::class,
        SmartspaceGoogleModule::class,
        StartCentralSurfacesGoogleModule::class,
        VolumeModule::class
    ],
    subcomponents = []
)
abstract class SystemUIGoogleModule {
    @Binds
    abstract fun bindGlobalRootComponent(
        globalRootComponent: SysUIGoogleGlobalRootComponent
    ): GlobalRootComponent

    @Binds
    abstract fun bindNotificationLockscreenUserManager(
        notificationLockscreenUserManager: NotificationLockscreenUserManagerGoogle
    ): NotificationLockscreenUserManager

    @Binds
    @SysUISingleton
    abstract fun bindQSFactory(qsFactoryImpl: QSFactoryImplGoogle): QSFactory

    @Binds
    abstract fun bindDockManager(dockManager: DockManagerImpl): DockManager

    @Binds
    abstract fun provideShadeController(shadeController: ShadeControllerImpl): ShadeController

    @Binds
    abstract fun bindHeadsUpManagerPhone(headsUpManagerPhone: HeadsUpManagerPhone): HeadsUpManager

    @Binds
    abstract fun bindKeyguardViewController(
        statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    ): KeyguardViewController

    @Binds
    abstract fun bindNotificationShadeController(
        notificationShadeWindowController: NotificationShadeWindowControllerImpl
    ): NotificationShadeWindowController

    @Binds
    abstract fun provideDozeHost(dozeServiceHost: DozeServiceHost): DozeHost

    @Binds
    abstract fun bindKeyguardIndicationControllerGoogle(
        keyguardIndicationController: KeyguardIndicationControllerGoogle
    ): KeyguardIndicationController

    @Binds
    abstract fun bindUdfpsDisplayMode(udfps: UdfpsDisplayMode): UdfpsDisplayModeProvider

    @Binds
    abstract fun bindUdfpsTouchProvider(udfpsTouch: UdfpsTouchProvider): AlternateUdfpsTouchProvider

    @Module
    companion object {
        @Provides
        @SysUISingleton
        @Named(LEAK_REPORT_EMAIL_NAME)
        fun provideLeakReportEmail(): String {
            return "buganizer-system+187317@google.com"
        }

        @Provides
        @SysUISingleton
        fun provideBatteryController(
            context: Context,
            enhancedEstimates: EnhancedEstimates,
            powerManager: PowerManager,
            broadcastDispatcher: BroadcastDispatcher,
            demoModeController: DemoModeController,
            dumpManager: DumpManager,
            @Main mainHandler: Handler,
            @Background bgHandler: Handler,
            contentResolver: UserContentResolverProvider,
            reverseChargingController: ReverseChargingController
        ): BatteryController {
            val bC: BatteryController =
                BatteryControllerImplGoogle(
                    context,
                    enhancedEstimates,
                    powerManager,
                    broadcastDispatcher,
                    demoModeController,
                    dumpManager,
                    mainHandler,
                    bgHandler,
                    contentResolver,
                    reverseChargingController
                )
            with(bC) { init() }
            return bC
        }

        @Provides
        @SysUISingleton
        fun provideSensorPrivacyController(
            sensorPrivacyManager: SensorPrivacyManager
        ): SensorPrivacyController {
            val spC: SensorPrivacyController =
                SensorPrivacyControllerImpl(sensorPrivacyManager)
            with(spC) { init() }
            return spC
        }

        @Provides
        @SysUISingleton
        fun provideIndividualSensorPrivacyController(
            sensorPrivacyManager: SensorPrivacyManager
        ): IndividualSensorPrivacyController {
            val spC: IndividualSensorPrivacyController =
                IndividualSensorPrivacyControllerImpl(sensorPrivacyManager)
            with(spC) { init() }
            return spC
        }

        @Provides
        @SysUISingleton
        @Named(ALLOW_NOTIFICATION_LONG_PRESS_NAME)
        fun provideAllowNotificationLongPress(): Boolean = true

        @Provides
        @SysUISingleton
        fun provideReverseWirelessCharger(
            context: Context
        ): Optional<ReverseWirelessCharger> {
            return when {
                context.resources.getBoolean(
                    KtR.bool.config_wlc_support_enabled
                )    -> Optional.of(ReverseWirelessCharger())
                else -> Optional.empty()
            }
        }

        @Provides
        @SysUISingleton
        fun provideUsbManager(context: Context): Optional<UsbManager> {
            return Optional.ofNullable(context.getSystemService(UsbManager::class.java))
        }

        @Provides
        @SysUISingleton
        fun provideBcSmartspaceDataPlugin(): BcSmartspaceDataPlugin {
            return BcSmartspaceDataProvider()
        }

        @Provides
        @SysUISingleton
        fun provideIThermalService(): IThermalService {
            return IThermalService.Stub.asInterface(ServiceManager.getService("thermalservice"))
        }

        @Provides
        @SysUISingleton
        fun provideHeadsUpManagerPhone(
            context: Context,
            headsUpManagerLogger: HeadsUpManagerLogger,
            statusBarStateController: StatusBarStateController,
            bypassController: KeyguardBypassController,
            groupManager: GroupMembershipManager,
            visualStabilityProvider: VisualStabilityProvider,
            configurationController: ConfigurationController,
            @Main handler: Handler,
            accessibilityManagerWrapper: AccessibilityManagerWrapper,
            uiEventLogger: UiEventLogger
        ): HeadsUpManagerPhone {
            return HeadsUpManagerPhone(
                context,
                headsUpManagerLogger,
                statusBarStateController,
                bypassController,
                groupManager,
                visualStabilityProvider,
                configurationController,
                handler,
                accessibilityManagerWrapper,
                uiEventLogger
            )
        }

        @Provides
        @SysUISingleton
        fun provideRecents(
            context: Context,
            recentsImplementation: RecentsImplementation,
            commandQueue: CommandQueue
        ): Recents {
            return Recents(context, recentsImplementation, commandQueue)
        }

        @Provides
        @SysUISingleton
        fun providesDeviceProvisionedController(
            deviceProvisionedController: DeviceProvisionedControllerImpl
        ): DeviceProvisionedController {
            deviceProvisionedController.init()
            return deviceProvisionedController
        }

        @Provides
        fun provideResources(context: Context): Resources {
            return context.resources
        }

        @Provides
        @SysUISingleton
        fun provideFingerprintExtProvider(): FingerprintExtProvider {
            return FingerprintExtProvider()
        }

        @Provides
        @SysUISingleton
        fun provideUdfpsTouchProvider(
            fingerprintExtProvider: FingerprintExtProvider
        ): UdfpsTouchProvider {
            return UdfpsTouchProvider(fingerprintExtProvider)
        }
    }
}
