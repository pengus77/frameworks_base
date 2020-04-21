/*
 * Copyright (C) 2015 The CyanogenMod Project
 * Copyright (C) 2017-2019 The LineageOS Project
 * Copyright (C) 2020 The KowalskiOS Project
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

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.development.DevelopmentSettingsEnabler;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import javax.inject.Inject;

public class AdbTile extends QSTileImpl<BooleanState> {

    private boolean mListening;
    private final KeyguardStateController mKeyguardStateController;
    private final ActivityStarter mActivityStarter;

    private static final Intent SETTINGS_DEVELOPMENT =
            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);

    private final KeyguardStateController.Callback mKeyguardCallback = new KeyguardStateController.Callback() {
        @Override
        public void onKeyguardShowingChanged() {
            refreshState();
        }
    };

    @Inject
    public AdbTile(QSHost host, KeyguardStateController keyguardStateController, ActivityStarter activityStarter) {
        super(host);
        mKeyguardStateController = keyguardStateController;
        mActivityStarter = activityStarter;
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        if (!mKeyguardStateController.isUnlocked()) {
            mActivityStarter.postQSRunnableDismissingKeyguard(this::toggleAction);
        } else {
            toggleAction();
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return SETTINGS_DEVELOPMENT;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = isAdbEnabled();
        state.icon = ResourceIcon.get(R.drawable.ic_qs_adb);
        state.label = mContext.getString(R.string.quick_settings_adb_label);
        if (state.value) {
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.secondaryLabel = null;
            state.state = canEnableAdb() ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_adb_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.KOWALSKI;
    }

    private boolean isAdbEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) == 1;
    }

    private boolean canEnableAdb() {
        return DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(mContext);
    }

    private void toggleAction() {
        final boolean active = getState().value;
        // Always allow toggle off if currently on.
        if (!active && !canEnableAdb()) {
            return;
        }
	Settings.Global.putInt(mContext.getContentResolver(),
		Settings.Global.ADB_ENABLED, (active ? 0 : 1));
    }

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening != listening) {
            mListening = listening;
            if (listening) {
                mContext.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                        false, mObserver);
                mKeyguardStateController.addCallback(mKeyguardCallback);
            } else {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
                mKeyguardStateController.removeCallback(mKeyguardCallback);
            }
        }
    }


}
