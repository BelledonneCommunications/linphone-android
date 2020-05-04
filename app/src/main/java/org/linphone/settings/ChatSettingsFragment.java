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
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.ChatRoom;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class ChatSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;
    private TextSetting mSharingServer, mMaxSizeForAutoDownloadIncomingFiles;
    private BasicSetting mAndroidNotificationSettings;
    private ListSetting mAutoDownloadIncomingFilesPolicy;
    private SwitchSetting mHideEmptyRooms, mHideRemovedProxiesRooms, mMakeDownloadedImagesPublic;
    private SwitchSetting mEnableEphemeralBeta;

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

        updateValues();
    }

    private void loadSettings() {
        mSharingServer = mRootView.findViewById(R.id.pref_image_sharing_server);
        mSharingServer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mMaxSizeForAutoDownloadIncomingFiles =
                mRootView.findViewById(R.id.pref_auto_download_max_size);

        mAutoDownloadIncomingFilesPolicy = mRootView.findViewById(R.id.pref_auto_download_policy);

        mMakeDownloadedImagesPublic =
                mRootView.findViewById(
                        R.id.pref_android_app_make_downloaded_images_visible_in_native_gallery);

        mAndroidNotificationSettings = mRootView.findViewById(R.id.pref_android_app_notif_settings);

        mHideEmptyRooms = mRootView.findViewById(R.id.pref_android_app_hide_empty_chat_rooms);

        mHideRemovedProxiesRooms =
                mRootView.findViewById(R.id.pref_android_app_hide_chat_rooms_from_removed_proxies);

        mEnableEphemeralBeta =
                mRootView.findViewById(R.id.pref_android_app_enable_ephemeral_messages_beta);
    }

    private void setListeners() {
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

        mMakeDownloadedImagesPublic.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setDownloadedImagesVisibleInNativeGallery(newValue);
                    }
                });

        mAndroidNotificationSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        if (Build.VERSION.SDK_INT >= Version.API26_O_80) {
                            Context context = getActivity();
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

        mHideEmptyRooms.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        LinphonePreferences.instance().setHideEmptyChatRooms(newValue);
                    }
                });

        mHideRemovedProxiesRooms.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        LinphonePreferences.instance().setHideRemovedProxiesChatRooms(newValue);
                    }
                });

        mEnableEphemeralBeta.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        LinphonePreferences.instance().enableEphemeralMessages(newValue);
                        if (!newValue) {
                            for (ChatRoom room : LinphoneManager.getCore().getChatRooms()) {
                                room.enableEphemeral(false);
                            }
                        }
                    }
                });
    }

    private void updateValues() {
        mSharingServer.setValue(mPrefs.getSharingPictureServerUrl());

        updateAutoDownloadSettingsFromValue(mPrefs.getAutoDownloadFileMaxSize());

        if (Version.sdkStrictlyBelow(Version.API26_O_80)) {
            mAndroidNotificationSettings.setVisibility(View.GONE);
        }

        mMakeDownloadedImagesPublic.setChecked(mPrefs.makeDownloadedImagesVisibleInNativeGallery());

        mHideEmptyRooms.setChecked(LinphonePreferences.instance().hideEmptyChatRooms());

        mHideRemovedProxiesRooms.setChecked(
                LinphonePreferences.instance().hideRemovedProxiesChatRooms());

        mEnableEphemeralBeta.setChecked(
                LinphonePreferences.instance().isEphemeralMessagesEnabled());

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
