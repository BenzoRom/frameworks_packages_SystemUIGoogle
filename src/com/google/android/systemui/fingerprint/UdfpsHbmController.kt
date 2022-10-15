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
package com.google.android.systemui.fingerprint

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.util.Log
import android.view.Display
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsHbmProvider
import com.android.systemui.biometrics.dagger.BiometricsBackground
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.concurrency.Execution
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.max

@SysUISingleton
class UdfpsHbmController @Inject constructor(
    private val context: Context,
    private val execution: Execution,
    @Main private val mainHandler: Handler,
    @BiometricsBackground private val biometricExecutor: Executor,
    private val lhbmProvider: UdfpsLhbmProvider,
    private val authController: AuthController,
    private val displayManager: DisplayManager
) : UdfpsHbmProvider, DisplayManager.DisplayListener {
    private var currentRequest: HbmRequest? = null
    private var peakRefreshRate: Float = 0.0f
    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}

    init {
        displayManager.getDisplay(context.getDisplayId()).supportedModes.forEach {
            peakRefreshRate = max(peakRefreshRate, it.refreshRate)
        }
    }

    override fun enableHbm(
        halControlsIllumination: Boolean,
        onHbmEnabled: Runnable?,
    ) {
        with(execution) { isMainThread() }
        Log.v(logTag, "enableHbm")
        if (authController.udfpsHbmListener == null) {
            Log.e(logTag, "enableHbm | mDisplayManagerCallback is null")
        } else if (currentRequest != null) {
            Log.e(logTag, "enableHbm | HBM is already requested")
        } else {
            @DisplayId val displayId: Int = context.getDisplayId()
            val hbmRequest = HbmRequest(mainHandler,
                biometricExecutor,
                authController,
                lhbmProvider,
                halControlsIllumination,
                displayId,
                onHbmEnabled!!)
            currentRequest = hbmRequest
            displayManager.registerDisplayListener(this, mainHandler)
            authController.udfpsHbmListener?.onHbmEnabled(displayId)
            Log.v(logTag, "enableHbm | request freeze refresh rate")
            val display = displayManager.getDisplay(hbmRequest.displayId)
            val refreshRate = display.refreshRate
            if (display.state == Display.STATE_ON) {
                if (refreshRate == peakRefreshRate) {
                    onDisplayChanged(hbmRequest.displayId)
                }
            }
        }
    }

    override fun disableHbm(onHbmDisabled: Runnable?) {
        with(execution) { isMainThread() }
        Log.v(logTag, "disableHbm")
        val hbmRequest = currentRequest
        if (hbmRequest == null) {
            Log.w(logTag, "disableHbm | HBM is already disabled")
            return
        }
        if (hbmRequest.authController.udfpsHbmListener == null) {
            Log.e(logTag, "disableHbm | mDisplayManagerCallback is null")
        }
        displayManager.unregisterDisplayListener(this)
        currentRequest = null
        with(hbmRequest) {
            if (started) {
                if (halControlsIllumination) {
                    biometricExecutor.execute {
                        lhbmProvider.run(UdfpsLhbmProvider::disableLhbm)
                        mainHandler.post {
                            authController.udfpsHbmListener?.onHbmDisabled(displayId)
                            Log.v(logTag, "disableHbm | requested to unfreeze the refresh rate")
                            if (onHbmDisabled != null) {
                                onHbmDisabled.run(Runnable::run)
                            } else {
                                Log.w(logTag, "doDisableHbm | onHbmDisabled is null")
                            }
                        }
                    }
                } else {
                    authController.udfpsHbmListener?.onHbmDisabled(displayId)
                }
            }
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        with(execution) { isMainThread() }
        val hbmRequest = currentRequest
        if (hbmRequest == null) {
            Log.w(logTag, "onDisplayChanged | mHbmRequest is null")
        } else if (displayId != hbmRequest.displayId) {
            Log.w(logTag, "onDisplayChanged | displayId: ${hbmRequest.displayId}")
        } else {
            val display = displayManager.getDisplay(displayId)
            if (display.state != Display.STATE_ON) {
                Log.w(logTag, "onDisplayChanged | state: ${display.state} != ON")
                if (hbmRequest.finishedStarting) {
                    Log.e(logTag, "onDisplayChanged | state changed while HBM is enabled.")
                }
                return
            }
            val refreshRate = display.refreshRate
            if (refreshRate != peakRefreshRate) {
                Log.w(logTag, "onDisplayChanged | hz: $refreshRate != $peakRefreshRate")
                if (hbmRequest.finishedStarting) {
                    Log.e(logTag, "onDisplayChanged | refresh rate changed while HBM is enabled.")
                }
            } else if (!hbmRequest.started) {
                Log.v(logTag, "onDisplayChanged | froze the refresh rate at $refreshRate Hz in state: ${display.state}")
                with(hbmRequest) {
                    started = true
                    if (!halControlsIllumination) {
                        biometricExecutor.execute {
                            lhbmProvider.run(UdfpsLhbmProvider::enableLhbm)
                            mainHandler.post(onHbmEnabled::run)
                        }
                    } else {
                        onHbmEnabled.run(Runnable::run)
                    }
                    finishedStarting = true
                }
            }
        }
    }
    companion object { private const val logTag = "UdfpsHbmController" }
}

class HbmRequest(
    @Main internal val mainHandler: Handler,
    @BiometricsBackground internal val biometricExecutor: Executor,
    internal val authController: AuthController,
    internal val lhbmProvider: UdfpsLhbmProvider,
    internal val halControlsIllumination: Boolean,
    internal val displayId: Int,
    internal val onHbmEnabled: Runnable
) {
    internal var finishedStarting = false
    internal var started = false
}
