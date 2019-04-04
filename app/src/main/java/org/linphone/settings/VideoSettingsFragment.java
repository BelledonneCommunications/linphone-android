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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.PayloadType;
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
        mVideoCodecs = mRootView.findViewById(R.id.pref_video_codecs);
    }

    protected void setListeners() {}

    protected void updateValues() {

        populateVideoCodecs();

        setListeners();
    }

    private void populateVideoCodecs() {
        mVideoCodecs.removeAllViews();
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core != null) {
            for (final PayloadType pt : core.getVideoPayloadTypes()) {
                final SwitchSetting codec = new SwitchSetting(getActivity());
                codec.setTitle(pt.getMimeType());

                if (pt.enabled()) {
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
}
