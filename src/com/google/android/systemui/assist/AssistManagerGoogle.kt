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
 * limitations under the License
 */
package com.google.android.systemui.assist

import android.content.Context
import android.metrics.LogMaker
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import android.view.IWindowManager
import com.android.internal.app.AssistUtils
import com.android.internal.app.IVoiceInteractionSessionListener
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.KeyguardUpdateMonitorCallback
import com.android.systemui.assist.AssistLogger
import com.android.systemui.assist.AssistManager
import com.android.systemui.assist.AssistantSessionEvent
import com.android.systemui.assist.PhoneStateMonitor
import com.android.systemui.assist.ui.DefaultUiController
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.model.SysUiState
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.recents.OverviewProxyService
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.google.android.systemui.assist.uihints.AssistantPresenceHandler
import com.google.android.systemui.assist.uihints.GoogleDefaultUiController
import com.google.android.systemui.assist.uihints.NgaMessageHandler
import com.google.android.systemui.assist.uihints.NgaUiController
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
open class AssistManagerGoogle
@Inject
constructor(
    deviceProvisionedController: DeviceProvisionedController?,
    context: Context,
    assistUtils: AssistUtils,
    private val ngaUiController: NgaUiController,
    commandQueue: CommandQueue,
    private val opaEnabledReceiver: OpaEnabledReceiver,
    phoneStateMonitor: PhoneStateMonitor,
    overviewProxyService: OverviewProxyService,
    opaEnabledDispatcher: OpaEnabledDispatcher,
    keyguardUpdateMonitor: KeyguardUpdateMonitor,
    navigationModeController: NavigationModeController,
    private val assistantPresenceHandler: AssistantPresenceHandler,
    private val ngaMessageHandler: NgaMessageHandler,
    sysUiState: Lazy<SysUiState>,
    @Main private val uiHandler: Handler,
    defaultUiController: DefaultUiController,
    private val googleDefaultUiController: GoogleDefaultUiController,
    private val windowManagerService: IWindowManager,
    assistLogger: AssistLogger
) :
    AssistManager(
        deviceProvisionedController,
        context,
        assistUtils,
        commandQueue,
        phoneStateMonitor,
        overviewProxyService,
        sysUiState,
        defaultUiController,
        assistLogger,
        uiHandler
    ) {
    private var googleIsAssistant = false
    private var ngaIsAssistant = false
    private var navigationMode: Int
    private var squeezeSetUp = false
    private var uiController: UiController
    private var checkAssistantStatus = true
    private val onProcessBundle = Runnable {
        assistantPresenceHandler.run(
            AssistantPresenceHandler::requestAssistantPresenceUpdate
        )
        checkAssistantStatus = false
    }

    init {
        addOpaEnabledListener(opaEnabledDispatcher)
        keyguardUpdateMonitor.registerCallback(
            object : KeyguardUpdateMonitorCallback() {
                override fun onUserSwitching(newUserId: Int) =
                    opaEnabledReceiver.onUserSwitching(newUserId)
            }
        )
        uiController = googleDefaultUiController
        navigationModeController.addListener { mode: Int -> navigationMode = mode }
            .also { navigationMode = it }
        assistantPresenceHandler.run {
            registerAssistantPresenceChangeListener(::onPresenceChanged)
        }
    }

    private fun onPresenceChanged(isGoogleAssistant: Boolean, isNgaAssistant: Boolean) {
        if (googleIsAssistant != isGoogleAssistant || ngaIsAssistant != isNgaAssistant) {
            if (isNgaAssistant) {
                if (uiController != ngaUiController) {
                    uiController = ngaUiController
                    uiHandler.post(uiController::hide)
                }
            } else {
                if (uiController != googleDefaultUiController) {
                    uiController = googleDefaultUiController
                    uiHandler.post(uiController::hide)
                }
                googleDefaultUiController.run { setGoogleAssistant(isGoogleAssistant) }
            }
            googleIsAssistant = isGoogleAssistant
            ngaIsAssistant = isNgaAssistant
        }
        checkAssistantStatus = false
    }

    open fun shouldUseHomeButtonAnimations(): Boolean {
        return !QuickStepContract.isGesturalMode(navigationMode)
    }

    override fun registerVoiceInteractionSessionListener() {
        mAssistUtils.registerVoiceInteractionSessionListener(
            object : IVoiceInteractionSessionListener.Stub(), IVoiceInteractionSessionListener {
                override fun onVoiceSessionWindowVisibilityChanged(visible: Boolean) {}

                override fun onVoiceSessionShown() {
                    with(mAssistLogger) {
                        reportAssistantSessionEvent(AssistantSessionEvent.ASSISTANT_SESSION_UPDATE)
                    }
                }

                override fun onVoiceSessionHidden() {
                    with(mAssistLogger) {
                        reportAssistantSessionEvent(AssistantSessionEvent.ASSISTANT_SESSION_CLOSE)
                    }
                }

                override fun onSetUiHints(hints: Bundle) {
                    when (hints.getString("action")) {
                        SET_ASSIST_GESTURE_CONSTRAINED_ACTION -> {
                            mSysUiState.get()
                                .setFlag(
                                    QuickStepContract.SYSUI_STATE_ASSIST_GESTURE_CONSTRAINED,
                                    hints.getBoolean("should_constrain", false)
                                ).commitUpdate(0)
                        }
                        SET_ASSISTANT_SHOW_GLOBAL_ACTIONS -> {
                            try {
                                windowManagerService.run(IWindowManager::showGlobalActions)
                            } catch (ex: RemoteException) {
                                Log.e(logTag, "showGlobalActions failed", ex)
                            }
                        }
                        else -> ngaMessageHandler.processBundle(hints, onProcessBundle)
                    }
                }
            })
    }

    override fun onInvocationProgress(type: Int, progress: Float) {
        when (progress) {
            0.0f,
            1.0f -> {
                checkAssistantStatus = true
                when (type) {
                    INVOCATION_TYPE_GESTURE -> checkSqueezeGestureStatus()
                }
            }
        }
        when {
            checkAssistantStatus -> {
                assistantPresenceHandler.run(
                    AssistantPresenceHandler::requestAssistantPresenceUpdate
                )
                checkAssistantStatus = false
            }
        }
        when {
            type != INVOCATION_TYPE_GESTURE || squeezeSetUp -> {
                uiController.run { onInvocationProgress(type, progress) }
            }
        }
    }

    override fun onGestureCompletion(velocity: Float) {
        checkAssistantStatus = true
        uiController.run {
            onGestureCompletion(velocity.div(mContext.resources.displayMetrics.density))
        }
    }

    override fun logStartAssistLegacy(invocationType: Int, phoneState: Int) {
        @Suppress("DEPRECATION")
        MetricsLogger.action(
            LogMaker(MetricsProto.MetricsEvent.ASSISTANT)
                .setType(MetricsProto.MetricsEvent.TYPE_OPEN)
                .setSubtype(
                    when {
                        assistantPresenceHandler.isNgaAssistant -> 1
                        else -> 0
                    } shl 8 or toLoggingSubType(invocationType, phoneState)
                )
        )
    }

    open fun isActiveAssistantNga(): Boolean {
        return ngaIsAssistant
    }

    fun addOpaEnabledListener(listener: OpaEnabledListener?) {
        opaEnabledReceiver.run { addOpaEnabledListener(listener) }
    }

    open fun dispatchOpaEnabledState() {
        opaEnabledReceiver.run(OpaEnabledReceiver::dispatchOpaEnabledState)
    }

    private fun checkSqueezeGestureStatus() {
        squeezeSetUp = Settings.Secure.getInt(
            mContext.contentResolver,
            "assist_gesture_setup_complete",
            0
        ) == 1
    }
}

private const val logTag = "AssistManagerGoogle"
private const val SET_ASSISTANT_SHOW_GLOBAL_ACTIONS = "show_global_actions"
