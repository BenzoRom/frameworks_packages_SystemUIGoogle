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

import android.app.AlarmManager
import android.app.WallpaperManager
import android.content.Context
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.service.dreams.IDreamManager
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.MetricsLogger
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.ViewMediatorCallback
import com.android.systemui.Dependency.TIME_TICK_HANDLER_NAME
import com.android.systemui.InitController
import com.android.systemui.KtR
import com.android.systemui.accessibility.floatingmenu.AccessibilityFloatingMenuController
import com.android.systemui.animation.ActivityLaunchAnimator
import com.android.systemui.assist.AssistManager
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.charging.WiredChargingRippleController
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.colorextraction.SysuiColorExtractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.flags.FeatureFlags
import com.android.systemui.fragments.FragmentService
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.keyguard.KeyguardViewMediator
import com.android.systemui.keyguard.ScreenLifecycle
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.navigationbar.NavigationBarController
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.PluginDependencyProvider
import com.android.systemui.recents.ScreenPinningRequest
import com.android.systemui.settings.brightness.BrightnessSliderController
import com.android.systemui.shade.ShadeController
import com.android.systemui.shared.plugins.PluginManager
import com.android.systemui.statusbar.*
import com.android.systemui.statusbar.notification.DynamicPrivacyController
import com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator
import com.android.systemui.statusbar.notification.init.NotificationsController
import com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider
import com.android.systemui.statusbar.notification.logging.NotificationLogger
import com.android.systemui.statusbar.notification.row.NotificationGutsManager
import com.android.systemui.statusbar.phone.*
import com.android.systemui.statusbar.phone.dagger.CentralSurfacesComponent
import com.android.systemui.statusbar.phone.ongoingcall.OngoingCallController
import com.android.systemui.statusbar.phone.panelstate.PanelExpansionStateManager
import com.android.systemui.statusbar.policy.*
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.window.StatusBarWindowController
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import com.android.systemui.tuner.TunerService
import com.android.systemui.util.WallpaperController
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.concurrency.MessageRouter
import com.android.systemui.volume.VolumeComponent
import com.android.wm.shell.bubbles.Bubbles
import com.android.wm.shell.startingsurface.StartingSurface
import com.google.android.systemui.NotificationLockscreenUserManagerGoogle
import com.google.android.systemui.ambientmusic.AmbientIndicationContainer
import com.google.android.systemui.ambientmusic.AmbientIndicationService
import com.google.android.systemui.reversecharging.ReverseChargingViewController
import com.google.android.systemui.smartspace.SmartSpaceController
import com.google.android.systemui.statusbar.KeyguardIndicationControllerGoogle
import dagger.Lazy
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named

@SysUISingleton
class CentralSurfacesGoogle
@Inject
constructor(
    private val smartSpaceController: SmartSpaceController,
    private val wallpaperNotifier: WallpaperNotifier,
    private val reverseChargingViewControllerOptional: Optional<ReverseChargingViewController>,
    context: Context,
    notificationsController: NotificationsController,
    fragmentService: FragmentService,
    lightBarController: LightBarController,
    autoHideController: AutoHideController,
    statusBarWindowController: StatusBarWindowController,
    statusBarWindowStateController: StatusBarWindowStateController,
    keyguardUpdateMonitor: KeyguardUpdateMonitor,
    statusBarSignalPolicy: StatusBarSignalPolicy,
    pulseExpansionHandler: PulseExpansionHandler,
    notificationWakeUpCoordinator: NotificationWakeUpCoordinator,
    keyguardBypassController: KeyguardBypassController,
    keyguardStateController: KeyguardStateController,
    headsUpManagerPhone: HeadsUpManagerPhone,
    dynamicPrivacyController: DynamicPrivacyController,
    falsingManager: FalsingManager,
    falsingCollector: FalsingCollector,
    broadcastDispatcher: BroadcastDispatcher,
    notificationGutsManager: NotificationGutsManager,
    notificationLogger: NotificationLogger,
    notificationInterruptStateProvider: NotificationInterruptStateProvider,
    panelExpansionStateManager: PanelExpansionStateManager,
    keyguardViewMediator: KeyguardViewMediator,
    displayMetrics: DisplayMetrics,
    metricsLogger: MetricsLogger,
    @UiBackground uiBgExecutor: Executor,
    notificationMediaManager: NotificationMediaManager,
    private val lockscreenUserManagerGoogle: NotificationLockscreenUserManagerGoogle,
    remoteInputManager: NotificationRemoteInputManager,
    userSwitcherController: UserSwitcherController,
    private val batteryController: BatteryController,
    colorExtractor: SysuiColorExtractor,
    screenLifecycle: ScreenLifecycle,
    wakefulnessLifecycle: WakefulnessLifecycle,
    statusBarStateController: SysuiStatusBarStateController,
    bubblesOptional: Optional<Bubbles>,
    deviceProvisionedController: DeviceProvisionedController,
    navigationBarController: NavigationBarController,
    accessibilityFloatingMenuController: AccessibilityFloatingMenuController,
    assistManagerLazy: Lazy<AssistManager>,
    configurationController: ConfigurationController,
    notificationShadeWindowController: NotificationShadeWindowController,
    dozeParameters: DozeParameters,
    scrimController: ScrimController,
    lockscreenWallpaperLazy: Lazy<LockscreenWallpaper>,
    biometricUnlockControllerLazy: Lazy<BiometricUnlockController>,
    dozeServiceHost: DozeServiceHost,
    powerManager: PowerManager,
    screenPinningRequest: ScreenPinningRequest,
    dozeScrimController: DozeScrimController,
    volumeComponent: VolumeComponent,
    commandQueue: CommandQueue,
    centralSurfacesComponentFactory: CentralSurfacesComponent.Factory,
    pluginManager: PluginManager,
    shadeController: ShadeController,
    statusBarKeyguardViewManager: StatusBarKeyguardViewManager,
    viewMediatorCallback: ViewMediatorCallback,
    initController: InitController,
    @Named(TIME_TICK_HANDLER_NAME) timeTickHandler: Handler,
    pluginDependencyProvider: PluginDependencyProvider,
    keyguardDismissUtil: KeyguardDismissUtil,
    extensionController: ExtensionController,
    userInfoControllerImpl: UserInfoControllerImpl,
    phoneStatusBarPolicy: PhoneStatusBarPolicy,
    keyguardIndicationControllerGoogle: KeyguardIndicationControllerGoogle,
    demoModeController: DemoModeController,
    notificationShadeDepthControllerLazy: Lazy<NotificationShadeDepthController>,
    statusBarTouchableRegionManager: StatusBarTouchableRegionManager,
    notificationIconAreaController: NotificationIconAreaController,
    brightnessSliderFactory: BrightnessSliderController.Factory,
    screenOffAnimationController: ScreenOffAnimationController,
    wallpaperController: WallpaperController,
    ongoingCallController: OngoingCallController,
    statusBarHideIconsForBouncerManager: StatusBarHideIconsForBouncerManager,
    lockscreenShadeTransitionController: LockscreenShadeTransitionController,
    featureFlags: FeatureFlags,
    keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    @Main mainHandler: Handler,
    @Main delayableExecutor: DelayableExecutor,
    @Main messageRouter: MessageRouter,
    wallpaperManager: WallpaperManager,
    startingSurfaceOptional: Optional<StartingSurface>,
    activityLaunchAnimator: ActivityLaunchAnimator,
    private val alarmManager: AlarmManager,
    jankMonitor: InteractionJankMonitor,
    deviceStateManager: DeviceStateManager,
    wiredChargingRippleController: WiredChargingRippleController,
    dreamManager: IDreamManager,
    tunerService: TunerService
) :
    CentralSurfacesImpl(
        context,
        notificationsController,
        fragmentService,
        lightBarController,
        autoHideController,
        statusBarWindowController,
        statusBarWindowStateController,
        keyguardUpdateMonitor,
        statusBarSignalPolicy,
        pulseExpansionHandler,
        notificationWakeUpCoordinator,
        keyguardBypassController,
        keyguardStateController,
        headsUpManagerPhone,
        dynamicPrivacyController,
        falsingManager,
        falsingCollector,
        broadcastDispatcher,
        notificationGutsManager,
        notificationLogger,
        notificationInterruptStateProvider,
        panelExpansionStateManager,
        keyguardViewMediator,
        displayMetrics,
        metricsLogger,
        uiBgExecutor,
        notificationMediaManager,
        lockscreenUserManagerGoogle,
        remoteInputManager,
        userSwitcherController,
        batteryController,
        colorExtractor,
        screenLifecycle,
        wakefulnessLifecycle,
        statusBarStateController,
        bubblesOptional,
        deviceProvisionedController,
        navigationBarController,
        accessibilityFloatingMenuController,
        assistManagerLazy,
        configurationController,
        notificationShadeWindowController,
        dozeParameters,
        scrimController,
        lockscreenWallpaperLazy,
        biometricUnlockControllerLazy,
        dozeServiceHost,
        powerManager,
        screenPinningRequest,
        dozeScrimController,
        volumeComponent,
        commandQueue,
        centralSurfacesComponentFactory,
        pluginManager,
        shadeController,
        statusBarKeyguardViewManager,
        viewMediatorCallback,
        initController,
        timeTickHandler,
        pluginDependencyProvider,
        keyguardDismissUtil,
        extensionController,
        userInfoControllerImpl,
        phoneStatusBarPolicy,
        keyguardIndicationControllerGoogle,
        demoModeController,
        notificationShadeDepthControllerLazy,
        statusBarTouchableRegionManager,
        notificationIconAreaController,
        brightnessSliderFactory,
        screenOffAnimationController,
        wallpaperController,
        ongoingCallController,
        statusBarHideIconsForBouncerManager,
        lockscreenShadeTransitionController,
        featureFlags,
        keyguardUnlockAnimationController,
        mainHandler,
        delayableExecutor,
        messageRouter,
        wallpaperManager,
        startingSurfaceOptional,
        activityLaunchAnimator,
        jankMonitor,
        deviceStateManager,
        wiredChargingRippleController,
        dreamManager,
        tunerService
    ) {
    private var animStartTime: Long = 0
    private var chargingAnimShown = false
    private var receivingBatteryLevel = 0
    private var reverseChargingAnimShown = false
    private val batteryStateChangeCallback: BatteryStateChangeCallback
        get() =
            object : BatteryStateChangeCallback {
                override fun onBatteryLevelChanged(
                    level: Int,
                    pluggedIn: Boolean,
                    charging: Boolean
                ) {
                    receivingBatteryLevel = level
                    if (!batteryController.isWirelessCharging) {
                        val uptimeMillis = SystemClock.uptimeMillis()
                        if (uptimeMillis - animStartTime > 1500) {
                            chargingAnimShown = false
                        }
                        reverseChargingAnimShown = false
                    }
                    if (DEBUG) {
                        Log.d(
                            TAG,
                            "onBatteryLevelChanged(): level=$level," +
                                "wlc=${if (batteryController.isWirelessCharging) 1 else 0}," +
                                "wlcs=$chargingAnimShown,rtxs=$reverseChargingAnimShown,this=$this"
                        )
                    }
                }

                override fun onReverseChanged(isReverse: Boolean, level: Int, name: String?) {
                    if (!isReverse && level >= 0 && !TextUtils.isEmpty(name)) {
                        if (
                            batteryController.isWirelessCharging &&
                                chargingAnimShown &&
                                !reverseChargingAnimShown
                        ) {
                            reverseChargingAnimShown = true
                            val uptimeMillis = SystemClock.uptimeMillis() - animStartTime
                            val animationDelay =
                                if (uptimeMillis > 1500) 0L else 1500 - uptimeMillis
                            showChargingAnimation(receivingBatteryLevel, level, animationDelay)
                        }
                    }
                    if (DEBUG) {
                        Log.d(
                            TAG,
                            "onReverseChanged(): rtx=${if (isReverse) 1 else 0}," +
                                "rxlevel=$receivingBatteryLevel,level=${level},name=$name," +
                                "wlc=${if (batteryController.isWirelessCharging) 1 else 0}," +
                                "wlcs=$chargingAnimShown,rtxs=$reverseChargingAnimShown,this=$this"
                        )
                    }
                }
            }

    override fun start() {
        super.start()
        batteryController.observe(lifecycle, batteryStateChangeCallback)
        lockscreenUserManagerGoogle.run(
            NotificationLockscreenUserManagerGoogle::updateSmartSpaceVisibilitySettings
        )
        reverseChargingViewControllerOptional.ifPresent(ReverseChargingViewController::initialize)
        wallpaperNotifier.run(WallpaperNotifier::attach)
        val ambientIndicationContainer =
            notificationShadeWindowView.findViewById<AmbientIndicationContainer>(
                KtR.id.ambient_indication_container
            )
        ambientIndicationContainer.initializeView(this)
        val ambientIndicationService =
            AmbientIndicationService(mContext, ambientIndicationContainer, alarmManager)
        ambientIndicationService.run(AmbientIndicationService::start)
    }

    override fun setLockscreenUser(newUserId: Int) {
        super.setLockscreenUser(newUserId)
        smartSpaceController.run(SmartSpaceController::reloadData)
    }

    override fun showWirelessChargingAnimation(batteryLevel: Int) {
        if (DEBUG) Log.d(TAG, "showWirelessChargingAnimation()")
        chargingAnimShown = true
        super.showWirelessChargingAnimation(batteryLevel)
        animStartTime = SystemClock.uptimeMillis()
    }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        super.dump(pw, args)
        smartSpaceController.run { dump(pw, args) }
    }
}
