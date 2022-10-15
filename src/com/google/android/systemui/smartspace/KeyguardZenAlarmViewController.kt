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
package com.google.android.systemui.smartspace

import android.app.AlarmManager
import android.app.AlarmManager.OnAlarmListener
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.text.format.DateFormat
import android.view.View
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceView
import com.android.systemui.statusbar.policy.NextAlarmController
import com.android.systemui.statusbar.policy.NextAlarmController.NextAlarmChangeCallback
import com.android.systemui.statusbar.policy.ZenModeController
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SysUISingleton
class KeyguardZenAlarmViewController @Inject constructor(
    private val context: Context,
    private val plugin: BcSmartspaceDataPlugin,
    private val zenModeController: ZenModeController,
    private val alarmManager: AlarmManager,
    private val nextAlarmController: NextAlarmController,
    @Main private val handler: Handler,
) {
    private val dndImage = loadDndImage()
    private val nextAlarmCallback = NextAlarmChangeCallback { updateNextAlarm() }
    private val showNextAlarm = OnAlarmListener { showAlarm() }
    private var smartspaceViews = mutableSetOf<SmartspaceView>()
    private val zenModeCallback = object : ZenModeController.Callback {
        override fun onZenChanged(zen: Int) = updateDnd()
    }

    @VisibleForTesting
    fun getSmartspaceViews(): MutableSet<SmartspaceView> = smartspaceViews

    fun init() {
        with(plugin) {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    smartspaceViews.add(v as SmartspaceView)
                    if (smartspaceViews.size == 1) {
                        zenModeController.addCallback(zenModeCallback)
                        nextAlarmController.addCallback(nextAlarmCallback)
                    }
                    refresh()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    smartspaceViews.remove(v as SmartspaceView)
                    if (smartspaceViews.isEmpty()) {
                        zenModeController.removeCallback(zenModeCallback)
                        nextAlarmController.removeCallback(nextAlarmCallback)
                    }
                }
            })
        }
        updateNextAlarm()
    }

    private fun loadDndImage(): Drawable? {
        val drawable = context.resources.getDrawable(
            R.drawable.stat_sys_dnd, null
        ) ?: throw NullPointerException(
            "null cannot be cast to non-null type android.graphics.drawable.InsetDrawable"
        )
        return (drawable as InsetDrawable).drawable
    }

    @VisibleForTesting
    fun updateDnd() {
        when {
            zenModeController.zen != 0 -> {
                smartspaceViews.forEach {
                    it.setDnd(
                        dndImage,
                        context.resources.getString(
                            R.string.accessibility_quick_settings_dnd
                        )
                    )
                }
            }
            else -> {
                smartspaceViews.forEach { it.setDnd(null, null) }
            }
        }
    }

    private fun updateNextAlarm() {
        alarmManager.cancel(showNextAlarm)
        if (zenModeController.nextAlarm > 0) {
            val exactTime = zenModeController.nextAlarm.minus(
                TimeUnit.HOURS.toMillis(12L)
            )
            if (exactTime > 0) {
                alarmManager.setExact(
                    AlarmManager.RTC,
                    exactTime,
                    "lock_screen_next_alarm",
                    showNextAlarm,
                    handler
                )
            }
        }
        showAlarm()
    }

    @VisibleForTesting
    fun showAlarm() {
        when {
            within12Hours(zenModeController.nextAlarm) -> {
                smartspaceViews.forEach {
                    it.setNextAlarm(
                        context.resources.getDrawable(
                            R.drawable.ic_access_alarms_big, null
                        ),
                        DateFormat.format(
                            whichDateFormat, zenModeController.nextAlarm
                        ).toString()
                    )
                }
            }
            else -> {
                smartspaceViews.forEach { it.setNextAlarm(null, null) }
            }
        }
    }

    private val whichDateFormat: String get() =
        when {
            DateFormat.is24HourFormat(context) -> "HH:mm"
            else -> "h:mm"
        }

    private fun within12Hours(alarm: Long): Boolean {
        return alarm > 0 && alarm <= System.currentTimeMillis().plus(
            TimeUnit.HOURS.toMillis(12L)
        )
    }

    private fun refresh() {
        updateDnd()
        updateNextAlarm()
    }
}
