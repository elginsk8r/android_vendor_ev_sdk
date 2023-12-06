/*
 * Copyright (C) 2020 Paranoid Android
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

package com.evervolv.platform.internal.display;

import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;

import com.android.server.display.color.DisplayTransformManager;
import com.android.server.LocalServices;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.NoSuchElementException;

import evervolv.hardware.LiveDisplayManager;
import evervolv.provider.EVSettings;

import vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight;
import vendor.lineage.livedisplay.V2_0.IAutoContrast;
import vendor.lineage.livedisplay.V2_0.IColorEnhancement;
import vendor.lineage.livedisplay.V2_0.IReadingEnhancement;
import vendor.lineage.livedisplay.V2_1.IAntiFlicker;

import static evervolv.hardware.LiveDisplayManager.FEATURE_ANTI_FLICKER;
import static evervolv.hardware.LiveDisplayManager.FEATURE_AUTO_CONTRAST;
import static evervolv.hardware.LiveDisplayManager.FEATURE_CABC;
import static evervolv.hardware.LiveDisplayManager.FEATURE_COLOR_ENHANCEMENT;
import static evervolv.hardware.LiveDisplayManager.FEATURE_READING_ENHANCEMENT;

public class SimpleDisplayController extends LiveDisplayFeature {

    private static final String TAG = "SimpleDisplayController";

    // settings uris
    private static final Uri DISPLAY_ANTI_FLICKER =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_ANTI_FLICKER);
    private static final Uri DISPLAY_AUTO_CONTRAST =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_AUTO_CONTRAST);
    private static final Uri DISPLAY_CABC =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_CABC);
    private static final Uri DISPLAY_COLOR_ENHANCE =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_COLOR_ENHANCE);
    private static final Uri DISPLAY_READING_MODE =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_READING_MODE);

    // hardware hal
    private IAdaptiveBacklight mAdaptiveBacklight = null;
    private IAntiFlicker mAntiFlicker = null;
    private IAutoContrast mAutoContrast = null;
    private IColorEnhancement mColorEnhancement = null;
    private IReadingEnhancement mReadingEnhancement = null;

    // hardware capabilities
    private final boolean mUseAntiFlicker;
    private final boolean mUseAutoContrast;
    private final boolean mUseCABC;
    private final boolean mUseColorEnhancement;
    private final boolean mUseReaderMode;

    // default values
    private final boolean mDefaultAntiFlicker;
    private final boolean mDefaultAutoContrast;
    private final boolean mDefaultCABC;
    private final boolean mDefaultColorEnhancement;

    /**
     * Matrix and offset used for converting color to grayscale.
     * Copied from com.android.server.accessibility.DisplayAdjustmentUtils.MATRIX_GRAYSCALE
     */
    private final float[] MATRIX_GRAYSCALE = {
            .2126f, .2126f, .2126f, 0,
            .7152f, .7152f, .7152f, 0,
            .0722f, .0722f, .0722f, 0,
                 0,      0,      0, 1
    };

    /** Full color matrix and offset */
    private final float[] MATRIX_NORMAL = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private static final int LEVEL_COLOR_MATRIX_READING = DisplayTransformManager.LEVEL_COLOR_MATRIX_GRAYSCALE + 1;

    public SimpleDisplayController(Context context, Handler handler) {
        super(context, handler);

        try {
            mAdaptiveBacklight = IAdaptiveBacklight.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseCABC = mAdaptiveBacklight != null;

        mDefaultCABC = context.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultCABC);

        try {
            mAntiFlicker = IAntiFlicker.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseAntiFlicker = mAntiFlicker != null;

        mDefaultAntiFlicker = context.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultAntiFlicker);

        try {
            mAutoContrast = IAutoContrast.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseAutoContrast = mAutoContrast != null;
        mDefaultAutoContrast = context.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultAutoContrast);

        try {
            mColorEnhancement = IColorEnhancement.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseColorEnhancement = mColorEnhancement != null;
        mDefaultColorEnhancement = context.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultColorEnhancement);

        try {
            mReadingEnhancement = IReadingEnhancement.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseReaderMode = mReadingEnhancement != null || (mAcceleratedTransform && mTransformManager != null);
    }

    @Override
    public void onStart() {
        final ArrayList<Uri> settings = new ArrayList<Uri>();

        if (mUseCABC) {
            settings.add(DISPLAY_CABC);
        }

        if (mUseAntiFlicker) {
            settings.add(DISPLAY_ANTI_FLICKER);
        }

        if (mUseAutoContrast) {
            settings.add(DISPLAY_AUTO_CONTRAST);
        }

        if (mUseColorEnhancement) {
            settings.add(DISPLAY_COLOR_ENHANCE);
        }

        if (mUseReaderMode) {
            settings.add(DISPLAY_READING_MODE);
        }

        if (settings.size() == 0) {
            return;
        }

        registerSettings(settings.toArray(new Uri[0]));
    }

    @Override
    public boolean getCapabilities(final BitSet caps) {
        if (mUseCABC) {
            caps.set(FEATURE_CABC);
        }

        if (mUseAntiFlicker) {
            caps.set(FEATURE_ANTI_FLICKER);
        }

        if (mUseAutoContrast) {
            caps.set(FEATURE_AUTO_CONTRAST);
        }

        if (mUseColorEnhancement) {
            caps.set(FEATURE_COLOR_ENHANCEMENT);
        }

        if (mUseReaderMode) {
            caps.set(FEATURE_READING_ENHANCEMENT);
        }

        return mUseCABC || mUseAntiFlicker
                || mUseAutoContrast || mUseColorEnhancement
                || mUseReaderMode;
    }

    @Override
    protected synchronized void onSettingsChanged(Uri uri) {
        if (uri == null || uri.equals(DISPLAY_CABC)) {
            updateFeature(FEATURE_CABC);
        }

        if (uri == null || uri.equals(DISPLAY_AUTO_CONTRAST)) {
            updateFeature(FEATURE_AUTO_CONTRAST);
        }

        if (uri == null || uri.equals(DISPLAY_COLOR_ENHANCE)) {
            updateFeature(FEATURE_COLOR_ENHANCEMENT);
        }

        if (uri == null || uri.equals(DISPLAY_READING_MODE)) {
            updateFeature(FEATURE_READING_ENHANCEMENT);
        }

        if (uri == null || uri.equals(DISPLAY_ANTI_FLICKER)) {
            updateFeature(FEATURE_ANTI_FLICKER);
        }
    }

    private synchronized void updateHardware() {
        if (isScreenOn()) {
            updateFeature(FEATURE_CABC);
            updateFeature(FEATURE_AUTO_CONTRAST);
            updateFeature(FEATURE_COLOR_ENHANCEMENT);
            updateFeature(FEATURE_READING_ENHANCEMENT);
            updateFeature(FEATURE_ANTI_FLICKER);
        }
    }

    @Override
    protected void onUpdate() {
        updateHardware();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("SimpleDisplayController Configuration:");
        pw.println("  mUseCABC=" + mUseCABC);
        pw.println("  mUseAutoContrast=" + mUseAutoContrast);
        pw.println("  mUseColorEnhancement="  + mUseColorEnhancement);
        pw.println("  mUseReaderMode=" + mUseReaderMode);
        pw.println("  mUseAntiFlicker=" + mUseAntiFlicker);
        pw.println();
        pw.println("  SimpleDisplayController State:");
        pw.println("    mCABC=" + getFeature(FEATURE_CABC));
        pw.println("    mAutoContrast=" + getFeature(FEATURE_AUTO_CONTRAST));
        pw.println("    mColorEnhancement=" + getFeature(FEATURE_COLOR_ENHANCEMENT));
        pw.println("    mReaderMode=" + getFeature(FEATURE_READING_ENHANCEMENT));
        pw.println("    mAntiFlicker=" + getFeature(FEATURE_ANTI_FLICKER));
    }

    private boolean isSupported(int feature) {
        switch (feature) {
            case FEATURE_CABC:
                return mUseCABC;
            case FEATURE_AUTO_CONTRAST:
                return mUseAutoContrast;
            case FEATURE_COLOR_ENHANCEMENT:
                return mUseColorEnhancement;
            case FEATURE_READING_ENHANCEMENT:
                return mUseReaderMode;
            case FEATURE_ANTI_FLICKER:
                return mUseAntiFlicker;
        }
        return false;
    }

    private void updateFeature(int feature) {
        if (!isSupported(feature)) {
            return;
        }

        try {
            switch (feature) {
                case FEATURE_CABC:
                    mAdaptiveBacklight.setEnabled(getFeature(feature));
                    break;
                case FEATURE_AUTO_CONTRAST:
                    mAutoContrast.setEnabled(getFeature(feature));
                    break;
                case FEATURE_COLOR_ENHANCEMENT:
                    mColorEnhancement.setEnabled(getFeature(feature)
                            && (!isLowPowerMode() || mDefaultColorEnhancement));
                    break;
                case FEATURE_READING_ENHANCEMENT:
                    if (mReadingEnhancement != null) {
                        mReadingEnhancement.setEnabled(getFeature(feature));
                        break;
                    } else if (mAcceleratedTransform && mTransformManager != null) {
                        mTransformManager.setColorMatrix(LEVEL_COLOR_MATRIX_READING,
                                getFeature(feature) ? MATRIX_GRAYSCALE : MATRIX_NORMAL);
                    }
                    break;
                case FEATURE_ANTI_FLICKER:
                    mAntiFlicker.setEnabled(getFeature(feature));
                    break;
            }
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    boolean getFeature(int feature) {
        if (!isSupported(feature)) {
            return false;
        }

        switch (feature) {
            case FEATURE_CABC:
                return getBoolean(EVSettings.System.DISPLAY_CABC, mDefaultCABC);
            case FEATURE_AUTO_CONTRAST:
                return getBoolean(EVSettings.System.DISPLAY_AUTO_CONTRAST, mDefaultAutoContrast);
            case FEATURE_COLOR_ENHANCEMENT:
                return getBoolean(EVSettings.System.DISPLAY_COLOR_ENHANCE, mDefaultColorEnhancement);
            case FEATURE_READING_ENHANCEMENT:
                return getBoolean(EVSettings.System.DISPLAY_READING_MODE, false);
            case FEATURE_ANTI_FLICKER:
                return getBoolean(EVSettings.System.DISPLAY_ANTI_FLICKER, mDefaultAntiFlicker);
        }

        return false;
    }

    boolean setFeature(int feature, boolean enabled) {
        if (!isSupported(feature)) {
            return false;
        }

        switch (feature) {
            case FEATURE_CABC:
                putBoolean(EVSettings.System.DISPLAY_CABC, enabled);
                return true;
            case FEATURE_AUTO_CONTRAST:
                putBoolean(EVSettings.System.DISPLAY_AUTO_CONTRAST, enabled);
                return true;
            case FEATURE_COLOR_ENHANCEMENT:
                putBoolean(EVSettings.System.DISPLAY_COLOR_ENHANCE, enabled);
                return true;
            case FEATURE_READING_ENHANCEMENT:
                putBoolean(EVSettings.System.DISPLAY_READING_MODE, enabled);
                return true;
            case FEATURE_ANTI_FLICKER:
                putBoolean(EVSettings.System.DISPLAY_ANTI_FLICKER, enabled);
                return true;
        }

        return false;
    }

    boolean getDefaultCABC() {
        return mDefaultCABC;
    }

    boolean getDefaultAutoContrast() {
        return mDefaultAutoContrast;
    }

    boolean getDefaultColorEnhancement() {
        return mDefaultColorEnhancement;
    }
}
