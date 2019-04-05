package org.linphone.settings;

/*
ChatSettingsFragment.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.mediastream.Version;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.TextSetting;

public class ChatSettingsFragment extends Fragment {
    protected View mRootView;
    protected LinphonePreferences mPrefs;

    private TextSetting mSharingServer, mMaxSizeForAutoDownloadIncomingFiles;
    private BasicSetting mAndroidNotificationSettings;
    private ListSetting mAutoDownloadIncomingFilesPolicy;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_chat, container, false);

        loadSettings();

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        mPrefs = LinphonePreferences.instance();
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance()
                    .selectMenu(
                            FragmentsAvailable.SETTINGS_SUBLEVEL,
                            getString(R.string.pref_chat_title));
        }

        updateValues();
    }

    protected void loadSettings() {
        mSharingServer = mRootView.findViewById(R.id.pref_image_sharing_server);
        mSharingServer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mMaxSizeForAutoDownloadIncomingFiles =
                mRootView.findViewById(R.id.pref_auto_download_max_size);

        mAutoDownloadIncomingFilesPolicy = mRootView.findViewById(R.id.pref_auto_download_policy);

        mAndroidNotificationSettings = mRootView.findViewById(R.id.pref_android_app_notif_settings);
    }

    protected void setListeners() {
        mSharingServer.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setSharingPictureServerUrl(newValue);
                    }
                });

        mAutoDownloadIncomingFilesPolicy.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        try {
                            int max_size = Integer.valueOf(newValue);
                            mPrefs.setAutoDownloadFileMaxSize(max_size);
                            updateAutoDownloadSettingsFromValue(max_size);
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mMaxSizeForAutoDownloadIncomingFiles.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setAutoDownloadFileMaxSize(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mAndroidNotificationSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        if (Build.VERSION.SDK_INT >= Version.API26_O_80) {
                            Context context = LinphoneActivity.instance();
                            Intent i = new Intent();
                            i.setAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                            i.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                            i.putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    context.getString(R.string.notification_channel_id));
                            i.addCategory(Intent.CATEGORY_DEFAULT);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                            startActivity(i);
                        }
                    }
                });
    }

    protected void updateValues() {
        mSharingServer.setValue(mPrefs.getSharingPictureServerUrl());

        updateAutoDownloadSettingsFromValue(mPrefs.getAutoDownloadFileMaxSize());

        if (Version.sdkStrictlyBelow(Version.API26_O_80)) {
            mAndroidNotificationSettings.setVisibility(View.GONE);
        }

        setListeners();
    }

    private void updateAutoDownloadSettingsFromValue(int max_size) {
        if (max_size == -1) {
            mAutoDownloadIncomingFilesPolicy.setValue(
                    getString(R.string.pref_auto_download_policy_disabled_key));
        } else if (max_size == 0) {
            mAutoDownloadIncomingFilesPolicy.setValue(
                    getString(R.string.pref_auto_download_policy_always_key));
        } else {
            mAutoDownloadIncomingFilesPolicy.setValue(
                    getString(R.string.pref_auto_download_policy_size_key));
        }
        mMaxSizeForAutoDownloadIncomingFiles.setValue(max_size);
        mMaxSizeForAutoDownloadIncomingFiles.setVisibility(max_size > 0 ? View.VISIBLE : View.GONE);
    }
}
