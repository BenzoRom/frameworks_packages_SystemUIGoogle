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

import android.app.smartspace.SmartspaceAction
import android.app.smartspace.SmartspaceTarget
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.UserHandle
import android.text.TextUtils
import android.view.View
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.R
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceView
import com.android.systemui.settings.CurrentUserTracker
import com.android.systemui.statusbar.NotificationMediaManager
import com.android.systemui.statusbar.NotificationMediaManager.MediaListener
import com.android.systemui.util.concurrency.DelayableExecutor
import javax.inject.Inject

@SysUISingleton
class KeyguardMediaViewController
@Inject
constructor(
    private val context: Context,
    private val plugin: BcSmartspaceDataPlugin,
    @Main private val uiExecutor: DelayableExecutor,
    private val mediaManager: NotificationMediaManager,
    private val broadcastDispatcher: BroadcastDispatcher
) {
    private var mediaArtist: CharSequence? = null
    private var mediaTitle: CharSequence? = null
    private val mediaComponent: ComponentName
    private val mediaListener: MediaListener
    private var userTracker: CurrentUserTracker? = null
    @get:VisibleForTesting var smartspaceView: SmartspaceView? = null

    init {
        object : MediaListener {
                override fun onPrimaryMetadataOrStateChanged(
                    metadata: MediaMetadata,
                    @PlaybackState.State state: Int
                ) {
                    uiExecutor.execute { updateMediaInfo(metadata, state) }
                }
            }.also { mediaListener = it }
        mediaComponent = ComponentName(context, KeyguardMediaViewController::class.java)
    }

    fun init() {
        with(plugin) {
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        smartspaceView = v as SmartspaceView
                        with(mediaManager) { addCallback(mediaListener) }
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        smartspaceView = null
                        with(mediaManager) { removeCallback(mediaListener) }
                    }
                }
            )
        }
        object : CurrentUserTracker(broadcastDispatcher) {
                override fun onUserSwitched(newUserId: Int) = reset()
            }.also { userTracker = it }
    }

    fun updateMediaInfo(metadata: MediaMetadata?, @PlaybackState.State state: Int) {
        if (!NotificationMediaManager.isPlayingState(state)) {
            reset()
            return
        }
        var unit: Unit? = null
        var title: CharSequence? = null
        if (metadata != null) {
            title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE)
            if (TextUtils.isEmpty(title)) {
                title = context.resources.getString(R.string.music_controls_no_title)
            }
        }
        val artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)
        if (!TextUtils.equals(mediaTitle, title) || !TextUtils.equals(mediaArtist, artist)) {
            mediaTitle = title
            mediaArtist = artist
            if (mediaTitle != null) {
                val deviceMediaTitle: SmartspaceAction =
                    SmartspaceAction.Builder("deviceMediaTitle", mediaTitle as String)
                        .setSubtitle(mediaArtist)
                        .setIcon(mediaManager.mediaIcon)
                        .build()
                val currentUserTracker: CurrentUserTracker = userTracker
                    ?: throw UninitializedPropertyAccessException("userTracker")
                if (smartspaceView != null) {
                    val deviceMedia: SmartspaceTarget.Builder =
                        SmartspaceTarget.Builder(
                                "deviceMedia",
                                mediaComponent,
                                UserHandle.of(currentUserTracker.currentUserId)
                            )
                            .setFeatureType(41)
                            .setHeaderAction(deviceMediaTitle)
                    smartspaceView?.setMediaTarget(deviceMedia.build())
                    unit = Unit
                }
            }
        }
        if (unit == null) reset()
    }

    private fun reset() {
        mediaTitle = null
        mediaArtist = null
        if (smartspaceView != null) {
            smartspaceView?.setMediaTarget(null)
        }
    }
}
