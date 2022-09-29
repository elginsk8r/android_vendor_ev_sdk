/*
 * Copyright (C) 2013 The CyanogenMod project
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

package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import evervolv.provider.EVSettings;

public class EVSystemCheckBoxPreference extends SelfRemovingCheckBoxPreference {
    public EVSystemCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EVSystemCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EVSystemCheckBoxPreference(Context context) {
        super(context, null);
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        EVSettings.System.putInt(getContext().getContentResolver(), getKey(), value ? 1 : 0);
    }

    @Override
    protected boolean isPersisted() {
        return EVSettings.System.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return EVSettings.System.getInt(getContext().getContentResolver(),
                getKey(), defaultValue ? 1 : 0) != 0;
    }
}
