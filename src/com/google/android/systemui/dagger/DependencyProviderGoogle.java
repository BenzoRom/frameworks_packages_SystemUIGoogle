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
package com.google.android.systemui.dagger;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.StatsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.IThermalService;
import android.os.Looper;
import android.os.UserManager;
import android.service.dreams.IDreamManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.app.IBatteryStats;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.BootCompleteCache;
import com.android.systemui.animation.DialogLaunchAnimator;
import com.android.systemui.biometrics.FaceHelpMessageDeferral;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.broadcast.BroadcastSender;
import com.android.systemui.controls.controller.ControlsController;
import com.android.systemui.controls.controller.ControlsTileResourceConfiguration;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dock.DockManager;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.EnhancedEstimates;
import com.android.systemui.settings.UserFileManager;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.NotificationClickNotifier;
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection;
import com.android.systemui.statusbar.notification.collection.render.NotificationVisibilityProvider;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.DeviceProvisionedController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.DeviceConfigProxy;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.settings.SecureSettings;
import com.android.systemui.util.wakelock.WakeLock;
import com.google.android.systemui.GoogleServices;
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle;
import com.google.android.systemui.autorotate.AutorotateDataService;
import com.google.android.systemui.autorotate.DataLogger;
import com.google.android.systemui.assist.AssistGoogleModule;
import com.google.android.systemui.assist.AssistManagerGoogle;
import com.google.android.systemui.columbus.ColumbusModule;
import com.google.android.systemui.columbus.ColumbusServiceWrapper;
import com.google.android.systemui.controls.GoogleControlsTileResourceConfigurationImpl;
import com.google.android.systemui.elmyra.ElmyraModule;
import com.google.android.systemui.elmyra.ServiceConfigurationGoogle;
import com.google.android.systemui.face.FaceNotificationService;
import com.google.android.systemui.power.PowerNotificationWarningsGoogleImpl;
import com.google.android.systemui.power.batteryhealth.HealthManager;
import com.google.android.systemui.power.batteryhealth.HealthService;
import com.google.android.systemui.reversecharging.ReverseChargingController;
import com.google.android.systemui.reversecharging.ReverseChargingViewController;
import com.google.android.systemui.reversecharging.ReverseWirelessCharger;
import com.google.android.systemui.smartspace.SmartSpaceController;
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle;
import com.google.android.systemui.statusbar.phone.WallpaperNotifier;
import com.google.android.systemui.vpn.AdaptivePPNService;
import com.google.android.systemui.vpn.VpnNetworkMonitor;

import java.util.Optional;
import java.util.concurrent.Executor;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

/**
 * Provides dependencies for sysui injection.
 */
@Module(
    includes = {
        AssistGoogleModule.class,
        ColumbusModule.class,
        ElmyraModule.class
    })
public interface DependencyProviderGoogle {
    @Provides
    @SysUISingleton
    static SmartSpaceController provideSmartSpaceController(
            Context context,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            @Background Handler backgroundHandler,
            AlarmManager alarmManager,
            BroadcastSender broadcastSender,
            DumpManager dumpManager) {
        return new SmartSpaceController(
                context,
                keyguardUpdateMonitor,
                backgroundHandler,
                alarmManager,
                broadcastSender,
                dumpManager);
    }

    @Provides
    @SysUISingleton
    static NotificationLockscreenUserManagerGoogle provideLockscreenUserManager(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            DevicePolicyManager devicePolicyManager,
            UserManager userManager,
            Lazy<NotificationVisibilityProvider> visibilityProviderLazy,
            Lazy<CommonNotifCollection> commonNotifCollectionLazy,
            NotificationClickNotifier clickNotifier,
            KeyguardManager keyguardManager,
            StatusBarStateController statusBarStateController,
            @Main Handler mainHandler,
            DeviceProvisionedController deviceProvisionedController,
            KeyguardStateController keyguardStateController,
            Lazy<KeyguardBypassController> keyguardBypassController,
            SmartSpaceController smartSpaceController,
            SecureSettings secureSettings,
            DumpManager dumpManager) {
        return new NotificationLockscreenUserManagerGoogle(
                context,
                broadcastDispatcher,
                devicePolicyManager,
                userManager,
                visibilityProviderLazy,
                commonNotifCollectionLazy,
                clickNotifier,
                keyguardManager,
                statusBarStateController,
                mainHandler,
                deviceProvisionedController,
                keyguardStateController,
                keyguardBypassController,
                smartSpaceController,
                secureSettings,
                dumpManager);
    }

    @Provides
    @SysUISingleton
    static WallpaperNotifier provideWallpaperNotifier(
            Context context,
            CommonNotifCollection commonNotifCollection,
            BroadcastDispatcher broadcastDispatcher,
            BroadcastSender broadcastSender) {
        return new WallpaperNotifier(
                context,
                commonNotifCollection,
                broadcastDispatcher,
                broadcastSender);
    }

    @Provides
    @SysUISingleton
    static ReverseChargingController provideReverseChargingController(
            Context context,
            BroadcastDispatcher broadcastDispatcher,
            Optional<ReverseWirelessCharger> rtxChargerManagerOptional,
            AlarmManager alarmManager,
            Optional<UsbManager> usbManagerOptional,
            @Main Executor mainExecutor,
            @Background Executor bgExecutor,
            BootCompleteCache bootCompleteCache,
            IThermalService thermalService) {
        return new ReverseChargingController(
                context,
                broadcastDispatcher,
                rtxChargerManagerOptional,
                alarmManager,
                usbManagerOptional,
                mainExecutor,
                bgExecutor,
                bootCompleteCache,
                thermalService);
    }

    @Provides
    @SysUISingleton
    static ReverseChargingViewController provideReverseChargingViewController(
            Context context,
            BatteryController batteryController,
            Lazy<CentralSurfaces> centralSurfacesLazy,
            StatusBarIconController statusBarIconController,
            BroadcastDispatcher broadcastDispatcher,
            @Main Executor mainExecutor,
            KeyguardIndicationControllerGoogle keyguardIndicationController) {
        return new ReverseChargingViewController(
                context,
                batteryController,
                centralSurfacesLazy,
                statusBarIconController,
                broadcastDispatcher,
                mainExecutor,
                keyguardIndicationController);
    }

    @Provides
    @SysUISingleton
    static KeyguardIndicationControllerGoogle provideKeyguardIndicationController(
            Context context,
            @Main Looper mainLooper,
            WakeLock.Builder wakeLockBuilder,
            KeyguardStateController keyguardStateController,
            StatusBarStateController statusBarStateController,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            DockManager dockManager,
            BroadcastDispatcher broadcastDispatcher,
            DevicePolicyManager devicePolicyManager,
            IBatteryStats iBatteryStats,
            UserManager userManager,
            TunerService tunerService,
            DeviceConfigProxy deviceConfig,
            @Main DelayableExecutor executor,
            @Background DelayableExecutor bgExecutor,
            FalsingManager falsingManager,
            LockPatternUtils lockPatternUtils,
            ScreenLifecycle screenLifecycle,
            KeyguardBypassController keyguardBypassController,
            AccessibilityManager accessibilityManager,
            FaceHelpMessageDeferral faceHelpMessageDeferral) {
        return new KeyguardIndicationControllerGoogle(
                context,
                mainLooper,
                wakeLockBuilder,
                keyguardStateController,
                statusBarStateController,
                keyguardUpdateMonitor,
                dockManager,
                broadcastDispatcher,
                devicePolicyManager,
                iBatteryStats,
                userManager,
                tunerService,
                deviceConfig,
                executor,
                bgExecutor,
                falsingManager,
                lockPatternUtils,
                screenLifecycle,
                keyguardBypassController,
                accessibilityManager,
                faceHelpMessageDeferral);
    }

    @Provides
    @SysUISingleton
    static GoogleServices provideGoogleServices(
            Context context,
            Lazy<ServiceConfigurationGoogle> serviceConfigurationGoogleLazy,
            UiEventLogger uiEventLogger,
            Lazy<ColumbusServiceWrapper> columbusServiceLazy,
            AutorotateDataService autorotateDataService,
            Lazy<FaceNotificationService> faceNotificationService) {
        return new GoogleServices(
                context,
                serviceConfigurationGoogleLazy,
                uiEventLogger,
                columbusServiceLazy,
                autorotateDataService,
                faceNotificationService);
    }

    @Provides
    @SysUISingleton
    static PowerNotificationWarningsGoogleImpl provideWarningsUiGoogle(
            Context context,
            ActivityStarter activityStarter,
            BroadcastDispatcher broadcastDispatcher,
            BroadcastSender broadcastSender,
            UiEventLogger uiEventLogger,
            Lazy<BatteryController> batteryControllerLazy,
            DialogLaunchAnimator dialogLaunchAnimator,
            EnhancedEstimates enhancedEstimates,
            KeyguardStateController keyguardStateController,
            IDreamManager dreamManager,
            Executor executor) {
        return new PowerNotificationWarningsGoogleImpl(
                context,
                activityStarter,
                broadcastDispatcher,
                broadcastSender,
                uiEventLogger,
                batteryControllerLazy,
                dialogLaunchAnimator,
                enhancedEstimates,
                keyguardStateController,
                dreamManager,
                executor);
    }

    @Provides
    @SysUISingleton
    static AutorotateDataService provideAutorotateDataService(
            Context context,
            SensorManager sensorManager,
            DataLogger dataLogger,
            BroadcastDispatcher broadcastDispatcher,
            DeviceConfigProxy deviceConfig,
            @Main DelayableExecutor mainExecutor) {
        return new AutorotateDataService(
                context,
                sensorManager,
                dataLogger,
                broadcastDispatcher,
                deviceConfig,
                mainExecutor);
    }

    @Provides
    @SysUISingleton
    static DataLogger provideDataLogger(StatsManager statsManager) {
        return new DataLogger(statsManager);
    }

    @Provides
    @SysUISingleton
    static ControlsTileResourceConfiguration
    provideControlsTileResourceConfiguration(ControlsController controlsController) {
        return new GoogleControlsTileResourceConfigurationImpl(controlsController);
    }

    @Provides
    @SysUISingleton
    static HealthService provideHealthService(
            Context context,
            HealthManager healthManager,
            Resources resources) {
        return new HealthService(context, healthManager, resources);
    }

    @Provides
    @SysUISingleton
    static FaceNotificationService providesFaceNotificationService(
            Context context, KeyguardUpdateMonitor keyguardUpdateMonitor) {
        return new FaceNotificationService(context, keyguardUpdateMonitor);
    }

    @Provides
    @SysUISingleton
    static AdaptivePPNService provideAdaptivePPNService(
            ConnectivityManager connectivityManager,
            BroadcastSender broadcastSender,
            BroadcastDispatcher broadcastDispatcher,
            @Main Executor uiExecutor,
            @Background Executor executor,
            UserFileManager userFileManager,
            UserTracker userTracker) {
        return new AdaptivePPNService(
                connectivityManager,
                broadcastSender,
                broadcastDispatcher,
                uiExecutor,
                executor,
                userFileManager,
                userTracker);
    }

    @Provides
    @SysUISingleton
    static VpnNetworkMonitor provideVpnNetworkMonitor(
            Context context,
            Resources resources,
            Lazy<VpnNetworkMonitor> networkMonitor,
            AdaptivePPNService adaptivePPNService) {
        return new VpnNetworkMonitor(
                context,
                resources,
                networkMonitor,
                adaptivePPNService);
    }
}
