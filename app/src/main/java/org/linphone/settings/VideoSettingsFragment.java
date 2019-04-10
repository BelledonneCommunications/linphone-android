package org.linphone.settings;

/*
VideoSettingsFragment.java
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
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.PayloadType;
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class VideoSettingsFragment extends Fragment {
    protected View mRootView;
    protected LinphonePreferences mPrefs;

    private SwitchSetting mEnable, mAutoInitiate, mAutoAccept, mOverlay;
    private ListSetting mPreset, mSize, mFps;
    private TextSetting mBandwidth;
    private LinearLayout mVideoCodecs;
    private TextView mVideoCodecsHeader;

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
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance()
                    .selectMenu(
                            FragmentsAvailable.SETTINGS_SUBLEVEL,
                            getString(R.string.pref_video_title));
        }

        updateValues();
    }

    protected void loadSettings() {
        mEnable = mRootView.findViewById(R.id.pref_video_enable);

        mAutoInitiate = mRootView.findViewById(R.id.pref_video_initiate_call_with_video);

        mAutoAccept = mRootView.findViewById(R.id.pref_video_automatically_accept_video);

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

    protected void setListeners() {
        mEnable.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableVideo(newValue);
                        if (!newValue) {
                            mAutoAccept.setChecked(false);
                            mAutoInitiate.setChecked(false);
                        }
                        updateVideoSettingsVisibility(newValue);
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

        mOverlay.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableOverlay(newValue);
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

    protected void updateValues() {
        mEnable.setChecked(mPrefs.isVideoEnabled());
        updateVideoSettingsVisibility(mPrefs.isVideoEnabled());

        mAutoInitiate.setChecked(mPrefs.shouldInitiateVideoCall());

        mAutoAccept.setChecked(mPrefs.shouldAutomaticallyAcceptVideoRequests());

        mOverlay.setChecked(mPrefs.isOverlayEnabled());

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
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
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
        mAutoInitiate.setVisibility(show ? View.VISIBLE : View.GONE);
        mAutoAccept.setVisibility(show ? View.VISIBLE : View.GONE);
        mOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        mBandwidth.setVisibility(show ? View.VISIBLE : View.GONE);
        mPreset.setVisibility(show ? View.VISIBLE : View.GONE);
        mSize.setVisibility(show ? View.VISIBLE : View.GONE);
        mFps.setVisibility(show ? View.VISIBLE : View.GONE);
        mVideoCodecs.setVisibility(show ? View.VISIBLE : View.GONE);
        mVideoCodecsHeader.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
