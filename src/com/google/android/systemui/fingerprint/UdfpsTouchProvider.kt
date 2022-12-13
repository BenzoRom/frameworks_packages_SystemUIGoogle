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

import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import com.android.systemui.biometrics.AlternateUdfpsTouchProvider
import com.android.systemui.dagger.SysUISingleton
import com.google.hardware.biometrics.fingerprint.IFingerprintExt
import javax.inject.Inject

@SysUISingleton
class UdfpsTouchProvider
@Inject constructor(
    private val extensionProvider: FingerprintExtProvider
) : AlternateUdfpsTouchProvider {
    override fun onPointerDown(
        pointerId: Long, x: Int, y: Int, minor: Float, major: Float
    ) {
        try {
            extensionProvider.fingerprintExt?.onPointerDown(
                pointerId, x, y, minor, major
            )
        } catch (ex: RemoteException) {
            Log.e(logTag, "Remote exception while calling onPointerDown")
        }
    }

    override fun onPointerUp(pointerId: Long) {
        try {
            extensionProvider.fingerprintExt?.onPointerUp(pointerId)
        } catch (ex: RemoteException) {
            Log.e(logTag, "Remote exception while calling onPointerUp")
        }
    }

    override fun onUiReady() {
        try {
            extensionProvider.fingerprintExt?.onUiReady()
        } catch (ex: RemoteException) {
            Log.e(logTag, "Remote exception while calling onUiReady")
        }
    }

    companion object { private const val logTag = "UdfpsTouchProvider" }
}

class FingerprintExtProvider @Inject constructor() {
    private var extension: IFingerprintExt? = null
    val fingerprintExt: IFingerprintExt?
        get() {
            var ext = extension
            if (ext == null) {
                val fpService: IBinder = ServiceManager.waitForDeclaredService(
                    "android.hardware.biometrics.fingerprint.IFingerprint/default"
                ).getExtension()
                val extInterface = fpService.queryLocalInterface(IFingerprintExt.DESCRIPTOR)
                when (extInterface) {
                    is IFingerprintExt -> extInterface
                    else               -> IFingerprintExt.Stub.asInterface(fpService)
                }.also { ext = it }
            }
            if (extension == null) {
                extension = ext
            }
            return extension
        }
}
