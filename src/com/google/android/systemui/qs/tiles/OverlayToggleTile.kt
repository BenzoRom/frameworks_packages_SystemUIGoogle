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
package com.google.android.systemui.qs.tiles

import android.content.Intent
import android.content.om.OverlayInfo
import android.content.om.OverlayManager
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.os.Build.IS_DEBUGGABLE as isDebuggable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.service.quicksettings.Tile
import android.util.Slog
import android.view.View
import com.android.internal.logging.MetricsLogger
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import javax.inject.Inject

class OverlayToggleTile @Inject constructor(
    host: QSHost,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val om: OverlayManager
) : QSTileImpl<QSTile.BooleanState>(
    host, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
) {
    private var overlayLabel: CharSequence? = null
    private var overlayInfosForTarget: List<OverlayInfo> = emptyList()
    private var overlayPackage: String? = null

    override fun getLongClickIntent(): Intent? = null
    override fun getTileLabel(): CharSequence = "Overlay"
    override fun handleLongClick(view: View?) {}
    override fun isAvailable(): Boolean = isDebuggable

    override fun newTileState(): QSTile.BooleanState {
        return QSTile.BooleanState().also {
            it.state = Tile.STATE_UNAVAILABLE
            it.label = "No overlay"
        }
    }

    override fun handleClick(view: View?) {
        val overlay = overlayPackage
        if (overlay != null) {
            val enable = state.state != Tile.STATE_ACTIVE
            Slog.v(TAG, "Setting enable state of $overlayPackage to $enable")
            om.setEnabled(overlay, enable, UserHandle.CURRENT)
            refreshState("Restarting...")
            Thread.sleep(250L)
            Slog.v(TAG, "Restarting System UI to react to overlay changes")
            Process.killProcess(Process.myPid())
        }
    }

    override fun handleUpdateState(state: QSTile.BooleanState, arg: Any?) {
        val packageManager: PackageManager = mContext.packageManager
        var overlayInfo: OverlayInfo? = null
        overlayInfosForTarget = om.getOverlayInfosForTarget(
            "com.android.systemui", UserHandle.CURRENT
        )
        if (overlayInfosForTarget.isNotEmpty()) {
            for (currentOverlay in overlayInfosForTarget) {
                if (currentOverlay.packageName.contains("gxoverlay", true)) {
                    overlayInfo = currentOverlay
                    break
                }
            }
            if (overlayInfo != null) {
                if (overlayPackage != overlayInfo.packageName) {
                    overlayInfo.packageName
                    overlayPackage = overlayInfo.packageName
                    overlayLabel = packageManager.getPackageInfo(
                        overlayInfo.packageName,
                        PackageInfoFlags.of(0)
                    ).applicationInfo.loadLabel(packageManager)
                }
                state.value = overlayInfo.isEnabled
                state.state = when {
                    overlayInfo.isEnabled -> Tile.STATE_ACTIVE
                    else                  -> Tile.STATE_INACTIVE
                }
                state.icon = ResourceIcon.get(com.android.internal.R.drawable.stat_sys_adb)
                state.label = overlayLabel
                state.secondaryLabel = when {
                    arg != null           -> "$arg"
                    overlayInfo.isEnabled -> "Enabled"
                    else                  -> "Disabled"
                }
            }
        }
    }
}
