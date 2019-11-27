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

import android.Manifest;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.PayloadType;
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class VideoSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private SwitchSetting mEnable, mAutoInitiate, mAutoAccept, mOverlay, mVideoPreview;
    private ListSetting mPreset, mSize, mFps;
    private TextSetting mBandwidth;
    private LinearLayout mVideoCodecs;
    private TextView mVideoCodecsHeader;
    private ListSetting mCameraDevices;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_video, container, false);

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
        mEnable = mRootView.findViewById(R.id.pref_video_enable);

        mVideoPreview = mRootView.findViewById(R.id.pref_video_preview);

        mAutoInitiate = mRootView.findViewById(R.id.pref_video_initiate_call_with_video);

        mAutoAccept = mRootView.findViewById(R.id.pref_video_automatically_accept_video);

        mCameraDevices = mRootView.findViewById(R.id.pref_video_camera_device);
        initCameraDevicesList();

        mOverlay = mRootView.findViewById(R.id.pref_overlay);

        mPreset = mRootView.findViewById(R.id.pref_video_preset);

        mSize = mRootView.findViewById(R.id.pref_preferred_video_size);
        initVideoSizeList();

        mFps = mRootView.findViewById(R.id.pref_preferred_fps);
        initFpsList();

        mBandwidth = mRootView.findViewById(R.id.pref_bandwidth_limit);
        mBandwidth.setInputType(InputType.TYPE_CLASS_NUMBER);

        mVideoCodecs = mRootView.findViewById(R.id.pref_video_codecs);
        mVideoCodecsHeader = mRootView.findViewById(R.id.pref_video_codecs_header);
    }

    private void setListeners() {
        mEnable.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableVideo(newValue);
                        if (!newValue) {
                            mVideoPreview.setChecked(false);
                            mAutoAccept.setChecked(false);
                            mAutoInitiate.setChecked(false);
                        }
                        updateVideoSettingsVisibility(newValue);
                    }
                });

        mVideoPreview.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (newValue) {
                            if (!((SettingsActivity) getActivity())
                                    .checkPermission(Manifest.permission.CAMERA)) {
                                ((SettingsActivity) getActivity())
                                        .requestPermissionIfNotGranted(Manifest.permission.CAMERA);
                            } else {
                                mPrefs.setVideoPreviewEnabled(true);
                            }
                        } else {
                            mPrefs.setVideoPreviewEnabled(false);
                        }
                    }
                });

        mAutoInitiate.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setInitiateVideoCall(newValue);
                    }
                });

        mAutoAccept.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setAutomaticallyAcceptVideoRequests(newValue);
                    }
                });

        mCameraDevices.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        mPrefs.setCameraDevice(newValue);
                    }
                });

        mOverlay.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableOverlay(
                                newValue
                                        && ((SettingsActivity) getActivity())
                                                .checkAndRequestOverlayPermission());
                    }
                });

        mPreset.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        mPrefs.setVideoPreset(newValue);
                        mFps.setVisibility(newValue.equals("custom") ? View.VISIBLE : View.GONE);
                        mBandwidth.setVisibility(
                                newValue.equals("custom") ? View.VISIBLE : View.GONE);
                    }
                });

        mSize.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        mPrefs.setPreferredVideoSize(newValue);
                    }
                });

        mFps.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        try {
                            mPrefs.setPreferredVideoFps(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mBandwidth.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setBandwidthLimit(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });
    }

    private void updateValues() {
        mEnable.setChecked(mPrefs.isVideoEnabled());
        updateVideoSettingsVisibility(mPrefs.isVideoEnabled());

        mVideoPreview.setChecked(mPrefs.isVideoPreviewEnabled());

        mAutoInitiate.setChecked(mPrefs.shouldInitiateVideoCall());

        mAutoAccept.setChecked(mPrefs.shouldAutomaticallyAcceptVideoRequests());

        mCameraDevices.setValue(mPrefs.getCameraDevice());

        mOverlay.setChecked(mPrefs.isOverlayEnabled());
        if (Version.sdkAboveOrEqual(Version.API26_O_80)
                && getResources().getBoolean(R.bool.allow_pip_while_video_call)) {
            // Disable overlay and use PIP feature
            mOverlay.setVisibility(View.GONE);
        }

        mBandwidth.setValue(mPrefs.getBandwidthLimit());
        mBandwidth.setVisibility(
                mPrefs.getVideoPreset().equals("custom") ? View.VISIBLE : View.GONE);

        mPreset.setValue(mPrefs.getVideoPreset());

        mSize.setValue(mPrefs.getPreferredVideoSize());

        mFps.setValue(mPrefs.getPreferredVideoFps());
        mFps.setVisibility(mPrefs.getVideoPreset().equals("custom") ? View.VISIBLE : View.GONE);

        populateVideoCodecs();

        setListeners();
    }

    private void initVideoSizeList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (VideoDefinition vd : Factory.instance().getSupportedVideoDefinitions()) {
            entries.add(vd.getName());
            values.add(vd.getName());
        }

        mSize.setItems(entries, values);
    }

    private void initFpsList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        entries.add(getString(R.string.pref_none));
        values.add("0");
        for (int i = 5; i <= 30; i += 5) {
            String str = Integer.toString(i);
            entries.add(str);
            values.add(str);
        }

        mFps.setItems(entries, values);
    }

    private void populateVideoCodecs() {
        mVideoCodecs.removeAllViews();
        Core core = LinphoneManager.getCore();
        if (core != null) {
            for (final PayloadType pt : core.getVideoPayloadTypes()) {
                final SwitchSetting codec = new SwitchSetting(getActivity());
                codec.setTitle(pt.getMimeType());

                if (pt.enabled()) {
                    // Never use codec.setChecked(pt.enabled) !
                    codec.setChecked(true);
                }
                codec.setListener(
                        new SettingListenerBase() {
                            @Override
                            public void onBoolValueChanged(boolean newValue) {
                                pt.enable(newValue);
                            }
                        });

                mVideoCodecs.addView(codec);
            }
        }
    }

    private void updateVideoSettingsVisibility(boolean show) {
        mVideoPreview.setVisibility(
                show
                                && getResources().getBoolean(R.bool.isTablet)
                                && getResources()
                                        .getBoolean(R.bool.show_camera_preview_on_dialer_on_tablets)
                        ? View.VISIBLE
                        : View.GONE);
        mAutoInitiate.setVisibility(show ? View.VISIBLE : View.GONE);
        mAutoAccept.setVisibility(show ? View.VISIBLE : View.GONE);
        mCameraDevices.setVisibility(show ? View.VISIBLE : View.GONE);
        mOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        mBandwidth.setVisibility(show ? View.VISIBLE : View.GONE);
        mPreset.setVisibility(show ? View.VISIBLE : View.GONE);
        mSize.setVisibility(show ? View.VISIBLE : View.GONE);
        mFps.setVisibility(show ? View.VISIBLE : View.GONE);
        mVideoCodecs.setVisibility(show ? View.VISIBLE : View.GONE);
        mVideoCodecsHeader.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            if (Version.sdkAboveOrEqual(Version.API26_O_80)
                    && getResources().getBoolean(R.bool.allow_pip_while_video_call)) {
                // Disable overlay and use PIP feature
                mOverlay.setVisibility(View.GONE);
            }
            mBandwidth.setVisibility(
                    mPrefs.getVideoPreset().equals("custom") ? View.VISIBLE : View.GONE);
            mFps.setVisibility(mPrefs.getVideoPreset().equals("custom") ? View.VISIBLE : View.GONE);
        }
    }

    private void initCameraDevicesList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            for (String camera : core.getVideoDevicesList()) {
                entries.add(camera);
                values.add(camera);
            }
        }

        mCameraDevices.setItems(entries, values);
    }
}
