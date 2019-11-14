/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import org.linphone.LinphoneContext;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class AdvancedSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private SwitchSetting mDebug, mJavaLogger, mBackgroundMode, mStartAtBoot, mDarkMode;
    private TextSetting mRemoteProvisioningUrl, mDisplayName, mUsername, mDeviceName, mLogUploadUrl;
    private BasicSetting mAndroidAppSettings;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_advanced, container, false);

        loadSettings();

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        mPrefs = LinphonePreferences.instance();

        updateValues();
    }

    private void loadSettings() {
        mDebug = mRootView.findViewById(R.id.pref_debug);

        mJavaLogger = mRootView.findViewById(R.id.pref_java_debug);
        // This is only required for blackberry users for all we know
        mJavaLogger.setVisibility(
                Build.MANUFACTURER.equals("BlackBerry") ? View.VISIBLE : View.GONE);

        mLogUploadUrl = mRootView.findViewById(R.id.pref_log_collection_upload_server_url);
        mLogUploadUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mBackgroundMode = mRootView.findViewById(R.id.pref_background_mode);

        mStartAtBoot = mRootView.findViewById(R.id.pref_autostart);

        mDarkMode = mRootView.findViewById(R.id.pref_dark_mode);
        mDarkMode.setVisibility(
                getResources().getBoolean(R.bool.allow_dark_mode) ? View.VISIBLE : View.GONE);

        mRemoteProvisioningUrl = mRootView.findViewById(R.id.pref_remote_provisioning);
        mRemoteProvisioningUrl.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mDisplayName = mRootView.findViewById(R.id.pref_display_name);

        mUsername = mRootView.findViewById(R.id.pref_user_name);

        mAndroidAppSettings = mRootView.findViewById(R.id.pref_android_app_settings);

        mDeviceName = mRootView.findViewById(R.id.pref_device_name);
    }

    private void setListeners() {
        mDebug.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setDebugEnabled(newValue);
                    }
                });

        mJavaLogger.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setJavaLogger(newValue);
                    }
                });

        mLogUploadUrl.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setLogCollectionUploadServerUrl(newValue);
                    }
                });

        mBackgroundMode.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setServiceNotificationVisibility(newValue);
                        if (newValue) {
                            LinphoneContext.instance().getNotificationManager().startForeground();
                        } else {
                            LinphoneContext.instance().getNotificationManager().stopForeground();
                        }
                    }
                });

        mStartAtBoot.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setAutoStart(newValue);
                    }
                });

        mDarkMode.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableDarkMode(newValue);
                        AppCompatDelegate.setDefaultNightMode(
                                newValue
                                        ? AppCompatDelegate.MODE_NIGHT_YES
                                        : AppCompatDelegate.MODE_NIGHT_NO);
                    }
                });

        mRemoteProvisioningUrl.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setRemoteProvisioningUrl(newValue);
                    }
                });

        mDisplayName.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setDefaultDisplayName(newValue);
                    }
                });

        mUsername.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setDefaultUsername(newValue);
                    }
                });

        mAndroidAppSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        Context context = getActivity();
                        Intent i = new Intent();
                        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.addCategory(Intent.CATEGORY_DEFAULT);
                        i.setData(Uri.parse("package:" + context.getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                        startActivity(i);
                    }
                });

        mDeviceName.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setDeviceName(newValue);
                    }
                });
    }

    private void updateValues() {
        mDebug.setChecked(mPrefs.isDebugEnabled());

        mJavaLogger.setChecked(mPrefs.useJavaLogger());

        mLogUploadUrl.setValue(mPrefs.getLogCollectionUploadServerUrl());

        mBackgroundMode.setChecked(mPrefs.getServiceNotificationVisibility());
        if (Compatibility.isAppUserRestricted(getActivity())) {
            mBackgroundMode.setChecked(false);
            mBackgroundMode.setEnabled(false);
            mBackgroundMode.setSubtitle(getString(R.string.pref_background_mode_warning_desc));
        }

        mStartAtBoot.setChecked(mPrefs.isAutoStartEnabled());

        mDarkMode.setChecked(mPrefs.isDarkModeEnabled());

        mRemoteProvisioningUrl.setValue(mPrefs.getRemoteProvisioningUrl());

        mDisplayName.setValue(mPrefs.getDefaultDisplayName());

        mUsername.setValue(mPrefs.getDefaultUsername());

        mDeviceName.setValue(mPrefs.getDeviceName(getActivity()));

        setListeners();
    }
}
