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
package com.google.android.systemui.power

import com.android.systemui.power.EnhancedEstimates
import com.android.systemui.power.PowerUI
import com.google.android.systemui.power.EnhancedEstimatesGoogleImpl
import com.google.android.systemui.power.PowerNotificationWarningsGoogleImpl
import dagger.Binds
import dagger.Module

/** Dagger Module for code in the power package. */
@Module
abstract class PowerModuleGoogle {
    @Binds
    abstract fun bindEnhancedEstimates(
        enhancedEstimates: EnhancedEstimatesGoogleImpl
    ): EnhancedEstimates

    @Binds
    abstract fun provideWarningsUi(
        controllerImpl: PowerNotificationWarningsGoogleImpl
    ): PowerUI.WarningsUI
}
