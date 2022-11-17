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
package com.google.android.systemui.qs.tileimpl

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.external.CustomTile
import com.android.systemui.qs.tileimpl.QSFactoryImpl
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.*
import com.android.systemui.util.leak.GarbageMonitor.MemoryTile
import com.google.android.systemui.qs.tiles.BatterySaverTileGoogle
import com.google.android.systemui.qs.tiles.OverlayToggleTile
import com.google.android.systemui.qs.tiles.ReverseChargingTile
import com.google.android.systemui.qs.tiles.RotationLockTileGoogle
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Provider

@SysUISingleton
class QSFactoryImplGoogle @Inject constructor(
    qsHostLazy: Lazy<QSHost>,
    customTileBuilderProvider: Provider<CustomTile.Builder>,
    wifiTileProvider: Provider<WifiTile>,
    internetTileProvider: Provider<InternetTile>,
    bluetoothTileProvider: Provider<BluetoothTile>,
    cellularTileProvider: Provider<CellularTile>,
    dndTileProvider: Provider<DndTile>,
    colorInversionTileProvider: Provider<ColorInversionTile>,
    airplaneModeTileProvider: Provider<AirplaneModeTile>,
    workModeTileProvider: Provider<WorkModeTile>,
    private val rotationLockTileGoogleProvider: Provider<RotationLockTileGoogle>,
    flashlightTileProvider: Provider<FlashlightTile>,
    locationTileProvider: Provider<LocationTile>,
    castTileProvider: Provider<CastTile>,
    hotspotTileProvider: Provider<HotspotTile>,
    private val batterySaverTileGoogleProvider: Provider<BatterySaverTileGoogle>,
    dataSaverTileProvider: Provider<DataSaverTile>,
    nightDisplayTileProvider: Provider<NightDisplayTile>,
    nfcTileProvider: Provider<NfcTile>,
    memoryTileProvider: Provider<MemoryTile>,
    uiModeNightTileProvider: Provider<UiModeNightTile>,
    screenRecordTileProvider: Provider<ScreenRecordTile>,
    private val reverseChargingTileProvider: Provider<ReverseChargingTile>,
    reduceBrightColorsTileProvider: Provider<ReduceBrightColorsTile>,
    cameraToggleTileProvider: Provider<CameraToggleTile>,
    microphoneToggleTileProvider: Provider<MicrophoneToggleTile>,
    deviceControlsTileProvider: Provider<DeviceControlsTile>,
    alarmTileProvider: Provider<AlarmTile>,
    private val overlayToggleTileProvider: Provider<OverlayToggleTile>,
    quickAccessWalletTileProvider: Provider<QuickAccessWalletTile>,
    qrCodeScannerTileProvider: Provider<QRCodeScannerTile>,
    oneHandedModeTileProvider: Provider<OneHandedModeTile>,
    colorCorrectionTileProvider: Provider<ColorCorrectionTile>,
    dreamTileProvider: Provider<DreamTile>,
    syncTileProvider: Provider<SyncTile>,
    caffeineTileProvider: Provider<CaffeineTile>,
    aodTileProvider: Provider<AlwaysOnDisplayTile>
) : QSFactoryImpl(
    qsHostLazy,
    customTileBuilderProvider,
    wifiTileProvider,
    internetTileProvider,
    bluetoothTileProvider,
    cellularTileProvider,
    dndTileProvider,
    colorInversionTileProvider,
    airplaneModeTileProvider,
    workModeTileProvider,
    rotationLockTileGoogleProvider::get,
    flashlightTileProvider,
    locationTileProvider,
    castTileProvider,
    hotspotTileProvider,
    batterySaverTileGoogleProvider::get,
    dataSaverTileProvider,
    nightDisplayTileProvider,
    nfcTileProvider,
    memoryTileProvider,
    uiModeNightTileProvider,
    screenRecordTileProvider,
    reduceBrightColorsTileProvider,
    cameraToggleTileProvider,
    microphoneToggleTileProvider,
    deviceControlsTileProvider,
    alarmTileProvider,
    quickAccessWalletTileProvider,
    qrCodeScannerTileProvider,
    oneHandedModeTileProvider,
    colorCorrectionTileProvider,
    dreamTileProvider,
    syncTileProvider,
    caffeineTileProvider,
    aodTileProvider
) {
    override fun createTileInternal(tileSpec: String): QSTileImpl<*>? {
        return when (tileSpec) {
            "rotation" -> rotationLockTileGoogleProvider.get()
            "ott"      -> overlayToggleTileProvider.get()
            "reverse"  -> reverseChargingTileProvider.get()
            else -> super.createTileInternal(tileSpec)
        }
    }
}
