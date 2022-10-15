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

import android.os.*
import android.util.Log
import com.google.hardware.pixel.display.IDisplay
import javax.inject.Inject

class UdfpsLhbmProvider @Inject constructor() : IBinder.DeathRecipient {

    init { displayHal }
    @Volatile var iDisplay: IDisplay? = null

    fun enableLhbm() {
        Log.v(logTag, "enableLhbm")
        when (val displayHal = displayHal) {
            null -> {
                Log.e(logTag, "enableLhbm | displayHal is null")
                return
            }
            else -> try {
                displayHal.setLhbmState(true)
            } catch (ex: RemoteException) {
                Log.e(logTag, "enableLhbm | RemoteException", ex)
            }
        }
    }

    fun disableLhbm() {
        Log.v(logTag, "disableLhbm")
        when (val displayHal = displayHal) {
            null -> {
                Log.e(logTag, "disableLhbm | displayHal is null")
                return
            }
            else -> try {
                displayHal.setLhbmState(false)
            } catch (ex: RemoteException) {
                Log.e(logTag, "disableLhbm | RemoteException", ex)
            }
        }
    }

    private val displayHal: IDisplay?
        get() {
            if (iDisplay != null) return iDisplay
            val displayService: IBinder? =
                ServiceManager.waitForDeclaredService(iDisplayService)
            return when (displayService) {
                null -> {
                    Log.e(logTag, "getDisplayHal | Failed to find the Display HAL")
                    null
                }
                else -> try {
                    displayService.linkToDeath(this, 0)
                    iDisplay = IDisplay.Stub.asInterface(displayService)
                    iDisplay
                } catch (ex: RemoteException) {
                    Log.e(logTag, "getDisplayHal | Failed to link to death", ex)
                    null
                }
            }
        }
    override fun binderDied() {
        Log.e(logTag, "binderDied | Display HAL died")
        iDisplay = null
    }

    companion object {
        private const val logTag = "UdfpsLhbmProvider"
        private const val iDisplayService =
            "com.google.hardware.pixel.display.IDisplay/default"
    }
}
