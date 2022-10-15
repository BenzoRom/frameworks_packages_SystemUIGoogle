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
package com.google.android.systemui.statusbar.policy

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.power.EnhancedEstimates
import com.android.systemui.settings.UserContentResolverProvider
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback
import com.android.systemui.statusbar.policy.BatteryControllerImpl
import com.google.android.systemui.reversecharging.ReverseChargingController
import java.io.PrintWriter
import java.util.function.Consumer

@SysUISingleton
open class BatteryControllerImplGoogle(
    context: Context,
    enhancedEstimates: EnhancedEstimates,
    powerManager: PowerManager,
    broadcastDispatcher: BroadcastDispatcher,
    demoModeController: DemoModeController,
    @Main mainHandler: Handler,
    @Background bgHandler: Handler,
    private val contentResolverProvider: UserContentResolverProvider,
    private val reverseChargingController: ReverseChargingController
) : BatteryControllerImpl(
    context,
    enhancedEstimates,
    powerManager,
    broadcastDispatcher,
    demoModeController,
    mainHandler,
    bgHandler
), ReverseChargingController.ReverseChargingChangeCallback {
    protected val contentObserver: ContentObserver
    private var extremeSaver = false
    private var reverse = false
    private var rtxLevel = 0
    private var rtxName: String? = null

    init {
        object : ContentObserver(bgHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (DEBUG) {
                    Log.d(TAG, "Change in EBS value $uri")
                }
                setExtremeSaver(isExtremeBatterySaving)
            }
        }.also { contentObserver = it }
    }

    override fun init() {
        super.init()
        resetReverseInfo()
        reverseChargingController.init(this)
        reverseChargingController.addCallback(this as ReverseChargingController.ReverseChargingChangeCallback)
        try {
            contentResolverProvider.userContentResolver.apply {
                registerContentObserver(
                    IS_EBS_ENABLED_OBSERVABLE_URI,
                    false,
                    contentObserver,
                    UserHandle.USER_ALL
                )
            }
            contentObserver.onChange(false, IS_EBS_ENABLED_OBSERVABLE_URI)
        } catch (ex: Exception) {
            Log.w(TAG, "Couldn't register to observe provider", ex)
        }
    }

    override fun onReverseChargingChanged(
        isReverse: Boolean,
        level: Int,
        name: String?
    ) {
        reverse = isReverse
        rtxLevel = level
        rtxName = name
        if (DEBUG) {
            Log.d(
                TAG,
                "onReverseChargingChanged(): rtx=${if (isReverse) 1 else 0} level=$level name=$name this=$this"
            )
        }
        fireReverseChanged()
    }

    override fun addCallback(cb: BatteryStateChangeCallback) {
        super.addCallback(cb)
        cb.run {
            onReverseChanged(reverse, rtxLevel, rtxName)
            onExtremeBatterySaverChanged(extremeSaver)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        reverseChargingController.run { handleIntentForReverseCharging(intent) }
    }

    override fun isReverseSupported(): Boolean {
        return reverseChargingController.isReverseSupported
    }

    override fun isReverseOn(): Boolean {
        return reverse
    }

    override fun setReverseState(isReverse: Boolean) {
        reverseChargingController.run { setReverseState(isReverse) }
    }

    private fun resetReverseInfo() {
        reverse = false
        rtxLevel = -1
        rtxName = null
    }

    open fun setExtremeSaver(isExtreme: Boolean) {
        if (isExtreme != extremeSaver) {
            extremeSaver = isExtreme
            fireExtremeSaverChanged()
        }
    }

    private fun fireExtremeSaverChanged() {
        synchronized(mChangeCallbacks) {
            mChangeCallbacks.indices.forEach {
                mChangeCallbacks[it].onExtremeBatterySaverChanged(extremeSaver)
            }
        }
    }

    private fun fireReverseChanged() {
        synchronized(mChangeCallbacks) {
            mChangeCallbacks.forEach(Consumer { cb: BatteryStateChangeCallback ->
                cb.onReverseChanged(reverse, rtxLevel, rtxName)
            })
        }
    }

    open val isExtremeBatterySaving: Boolean
        get() {
            val bundle = try {
                contentResolverProvider.userContentResolver.call(
                    EBS_STATE_AUTHORITY,
                    "get_flipendo_state",
                    null,
                    Bundle()
                )
            } catch (ex: IllegalArgumentException) {
                Bundle()
            }
            return bundle?.getBoolean("flipendo_state", false) ?: false
        }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        super.dump(pw, args)
        pw.print("  mReverse=")
        pw.println(reverse)
        pw.print("  mExtremeSaver=")
        pw.println(extremeSaver)
    }

    companion object {
        private const val TAG = "BatteryControllerGoogle"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
        const val EBS_STATE_AUTHORITY = "com.google.android.flipendo.api"
        @JvmStatic
        val IS_EBS_ENABLED_OBSERVABLE_URI: Uri =
            Uri.parse("content://com.google.android.flipendo.api/get_flipendo_state")
    }
}
