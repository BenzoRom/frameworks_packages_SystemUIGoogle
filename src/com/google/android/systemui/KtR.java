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
package com.android.systemui;

import com.android.systemui.R;

/**
 * Allow building using resources with Kotlin/dagger2.
 * Fixes unresolved reference errors.
 */
public class KtR {
    public static final class array {
        public static int tile_states_rotation = R.array.tile_states_rotation;
    }

    public static final class bool {
        public static int config_battery_index_enabled = R.bool.config_battery_index_enabled;
        public static int config_touch_context_enabled = R.bool.config_touch_context_enabled;
        public static int config_wlc_support_enabled = R.bool.config_wlc_support_enabled;
    }

    public static final class dimen {
        public static int smartspace_icon_shadow = R.dimen.smartspace_icon_shadow;
    }

    public static final class drawable {
        public static int ic_qs_reverse_charging = R.drawable.ic_qs_reverse_charging;
    }

    public static final class id {
        public static int ambient_indication_container = R.id.ambient_indication_container;
    }

    public static final class string {
        public static int extreme_battery_saver_text = R.string.extreme_battery_saver_text;
        public static int low_battery_label = R.string.low_battery_label;
        public static int quick_settings_dark_mode_secondary_label_battery_saver = R.string.quick_settings_dark_mode_secondary_label_battery_saver;
        public static int quick_settings_rotation_posture_folded = R.string.quick_settings_rotation_posture_folded;
        public static int quick_settings_rotation_posture_unfolded = R.string.quick_settings_rotation_posture_unfolded;
        public static int reverse_charging_title = R.string.reverse_charging_title;
        public static int too_hot_label = R.string.too_hot_label;
        public static int wireless_charging_label = R.string.wireless_charging_label;
    }
}
