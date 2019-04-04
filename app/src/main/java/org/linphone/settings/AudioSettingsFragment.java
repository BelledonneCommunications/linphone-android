package org.linphone.settings;

/*
AudioSettingsFragment.java
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
import androidx.annotation.Nullable;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.PayloadType;
import org.linphone.fragments.FragmentsAvailable;

public class AudioSettingsFragment extends Fragment {
    protected View mRootView;
    protected LinphonePreferences mPrefs;

    private SwitchSetting mEchoCanceller, mAdaptiveRateControl;
    private TextSetting mMicGain, mSpeakerGain;
    private ListSetting mCodecBitrateLimit;
    private BasicSetting mEchoCalibration, mEchoTester;
    private LinearLayout mAudioCodecs;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_audio, container, false);

        loadSettings();
        setListeners();

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        mPrefs = LinphonePreferences.instance();
        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS_SUBLEVEL);
        }

        updateValues();
    }

    protected void loadSettings() {
        mEchoCanceller = mRootView.findViewById(R.id.pref_echo_cancellation);

        mAdaptiveRateControl = mRootView.findViewById(R.id.pref_adaptive_rate_control);

        mMicGain = mRootView.findViewById(R.id.pref_mic_gain_db);
        mMicGain.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        mSpeakerGain = mRootView.findViewById(R.id.pref_playback_gain_db);
        mSpeakerGain.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        mCodecBitrateLimit = mRootView.findViewById(R.id.pref_codec_bitrate_limit);

        mEchoCalibration = mRootView.findViewById(R.id.pref_echo_canceller_calibration);

        mEchoTester = mRootView.findViewById(R.id.pref_echo_tester);

        mAudioCodecs = mRootView.findViewById(R.id.pref_audio_codecs);
    }

    protected void setListeners() {
        // TODO
    }

    protected void updateValues() {
        mEchoCanceller.setChecked(mPrefs.echoCancellationEnabled());

        mAdaptiveRateControl.setChecked(mPrefs.adaptiveRateControlEnabled());

        mMicGain.setValue(mPrefs.getMicGainDb());

        mSpeakerGain.setValue(mPrefs.getPlaybackGainDb());

        mCodecBitrateLimit.setValue(mPrefs.getCodecBitrateLimit());

        mAudioCodecs.removeAllViews();
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core != null) {
            for (final PayloadType pt : core.getAudioPayloadTypes()) {
                SwitchSetting codec = new SwitchSetting(getActivity());
                codec.setTitle(pt.getMimeType());
                /* Special case */
                if (pt.getMimeType().equals("mpeg4-generic")) {
                    codec.setTitle("AAC-ELD");
                }

                codec.setSubtitle(pt.getClockRate() + " Hz");
                codec.setChecked(pt.enabled());

                mAudioCodecs.addView(codec);
            }
        }
    }
}
