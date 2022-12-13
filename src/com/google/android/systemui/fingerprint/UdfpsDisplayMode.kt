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
import android.util.Log
import com.android.systemui.biometrics.AuthController
import com.android.systemui.biometrics.UdfpsDisplayModeProvider
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.DisplayId
import com.android.systemui.util.concurrency.Execution
import javax.inject.Inject

@SysUISingleton
class UdfpsDisplayMode
@Inject
constructor(
    private val context: Context,
    private val execution: Execution,
    private val authController: AuthController
) : UdfpsDisplayModeProvider {
    private var currentRequest: Request? = null
    override fun enable(onEnabled: Runnable?) {
        with(execution) { isMainThread() }
        Log.v(logTag, "enable")
        if (currentRequest != null) {
            Log.e(logTag, "enable | already requested")
        } else if (authController.udfpsHbmListener == null) {
            Log.e(logTag, "enable | mDisplayManagerCallback is null")
        } else {
            @DisplayId val displayId = context.getDisplayId()
            currentRequest = Request(displayId)
            authController.udfpsHbmListener?.onHbmEnabled(displayId)
            Log.v(logTag, "enable | requested optimal refresh rate for UDFPS")
            onEnabled?.run()
        }
    }

    override fun disable(onDisabled: Runnable?) {
        with(execution) { isMainThread() }
        Log.v(logTag, "disable")
        val request = currentRequest
        if (request == null) {
            Log.w(logTag, "disable | already disabled")
            return
        }
        val iUdfpsHbmListener = authController.udfpsHbmListener
        iUdfpsHbmListener?.onHbmDisabled(request.displayId)
        Log.v(logTag, "disable | removed the UDFPS refresh rate request")
        currentRequest = null
        onDisabled?.run()
        Log.w(logTag, "disable | onDisabled is null")
    }
}

class Request(val displayId: Int)
private const val logTag = "UdfpsDisplayMode"
