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
package com.google.android.systemui

import android.content.Context
import com.android.systemui.KtR
import com.android.systemui.Dumpable
import com.android.systemui.VendorServices
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.DisplayCutoutEmulationAdapter
import com.google.android.systemui.autorotate.AutorotateDataService
import com.google.android.systemui.coversheet.CoversheetService
import com.google.android.systemui.face.FaceNotificationService
import com.google.android.systemui.input.TouchContextService
import java.io.PrintWriter
import javax.inject.Inject

@SysUISingleton
class GoogleServices @Inject constructor(
    context: Context,
    private val autorotateDataService: AutorotateDataService
) : VendorServices(context) {
    private val services = ArrayList<Any>()

    override fun start() {
        addService(DisplayCutoutEmulationAdapter(mContext))
        addService(CoversheetService(mContext))
        with(autorotateDataService, AutorotateDataService::init)
        addService(autorotateDataService)
        if (mContext.packageManager.hasSystemFeature("android.hardware.biometrics.face")) {
            addService(FaceNotificationService(mContext))
        }
        if (mContext.resources.getBoolean(KtR.bool.config_touch_context_enabled)) {
            addService(TouchContextService(mContext))
        }
    }

    private fun addService(service: Any?) {
        when {
            service != null -> with(services) { add(service) }
        }
    }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        services.indices.forEach {
            when {
                services[it] is Dumpable -> (services[it] as Dumpable).dump(pw, args)
            }
        }
    }
}
