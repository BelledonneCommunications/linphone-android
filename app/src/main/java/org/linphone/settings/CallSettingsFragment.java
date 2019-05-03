package org.linphone.settings;

/*
CallSettingsFragment.java
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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.MediaEncryption;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class CallSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private SwitchSetting mDeviceRingtone,
            mVibrateIncomingCall,
            mDtmfSipInfo,
            mDtmfRfc2833,
            mAutoAnswer;
    private ListSetting mMediaEncryption;
    private TextSetting mAutoAnswerTime, mIncomingCallTimeout, mVoiceMailUri;
    private BasicSetting mDndPermissionSettings;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_call, container, false);

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
        mDeviceRingtone = mRootView.findViewById(R.id.pref_device_ringtone);

        mVibrateIncomingCall = mRootView.findViewById(R.id.pref_vibrate_on_incoming_calls);

        mDtmfSipInfo = mRootView.findViewById(R.id.pref_sipinfo_dtmf);

        mDtmfRfc2833 = mRootView.findViewById(R.id.pref_rfc2833_dtmf);

        mAutoAnswer = mRootView.findViewById(R.id.pref_auto_answer);

        mMediaEncryption = mRootView.findViewById(R.id.pref_media_encryption);
        initMediaEncryptionList();

        mAutoAnswerTime = mRootView.findViewById(R.id.pref_auto_answer_time);
        mAutoAnswerTime.setInputType(InputType.TYPE_CLASS_NUMBER);

        mIncomingCallTimeout = mRootView.findViewById(R.id.pref_incoming_call_timeout);
        mAutoAnswerTime.setInputType(InputType.TYPE_CLASS_NUMBER);

        mVoiceMailUri = mRootView.findViewById(R.id.pref_voice_mail);
        mAutoAnswerTime.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mDndPermissionSettings =
                mRootView.findViewById(R.id.pref_grant_read_dnd_settings_permission);
    }

    private void setListeners() {
        mDeviceRingtone.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        int readExternalStorage =
                                getActivity()
                                        .getPackageManager()
                                        .checkPermission(
                                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                                getActivity().getPackageName());
                        if (readExternalStorage == PackageManager.PERMISSION_GRANTED) {
                            mPrefs.enableDeviceRingtone(newValue);
                        } else {
                            ((SettingsActivity) getActivity())
                                    .requestPermissionIfNotGranted(
                                            Manifest.permission.READ_EXTERNAL_STORAGE);
                        }
                    }
                });

        mVibrateIncomingCall.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableIncomingCallVibration(newValue);
                    }
                });

        mDtmfSipInfo.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (newValue) mDtmfRfc2833.setChecked(false);
                        mPrefs.sendDTMFsAsSipInfo(newValue);
                    }
                });

        mDtmfRfc2833.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (newValue) mDtmfSipInfo.setChecked(false);
                        mPrefs.sendDtmfsAsRfc2833(newValue);
                    }
                });

        mAutoAnswer.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableAutoAnswer(newValue);
                        mAutoAnswerTime.setVisibility(
                                mPrefs.isAutoAnswerEnabled() ? View.VISIBLE : View.GONE);
                    }
                });

        mMediaEncryption.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        try {
                            mPrefs.setMediaEncryption(
                                    MediaEncryption.fromInt(Integer.parseInt(newValue)));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mAutoAnswerTime.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setAutoAnswerTime(Integer.parseInt(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mIncomingCallTimeout.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setIncTimeout(Integer.parseInt(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mVoiceMailUri.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setVoiceMailUri(newValue);
                    }
                });

        mDndPermissionSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        startActivity(
                                new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                    }
                });
    }

    private void updateValues() {
        mDeviceRingtone.setChecked(mPrefs.isDeviceRingtoneEnabled());

        mVibrateIncomingCall.setChecked(mPrefs.isIncomingCallVibrationEnabled());

        mDtmfSipInfo.setChecked(mPrefs.useSipInfoDtmfs());

        mDtmfRfc2833.setChecked(mPrefs.useRfc2833Dtmfs());

        mAutoAnswer.setChecked(mPrefs.isAutoAnswerEnabled());

        mMediaEncryption.setValue(mPrefs.getMediaEncryption().toInt());

        mAutoAnswerTime.setValue(mPrefs.getAutoAnswerTime());
        mAutoAnswerTime.setVisibility(mPrefs.isAutoAnswerEnabled() ? View.VISIBLE : View.GONE);

        mIncomingCallTimeout.setValue(mPrefs.getIncTimeout());

        mVoiceMailUri.setValue(mPrefs.getVoiceMailUri());

        mDndPermissionSettings.setVisibility(
                Version.sdkAboveOrEqual(Version.API23_MARSHMALLOW_60) ? View.VISIBLE : View.GONE);

        setListeners();
    }

    private void initMediaEncryptionList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        entries.add(getString(R.string.pref_none));
        values.add(String.valueOf(MediaEncryption.None.toInt()));

        Core core = LinphoneManager.getCore();
        if (core != null
                && !getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
            boolean hasZrtp = core.mediaEncryptionSupported(MediaEncryption.ZRTP);
            boolean hasSrtp = core.mediaEncryptionSupported(MediaEncryption.SRTP);
            boolean hasDtls = core.mediaEncryptionSupported(MediaEncryption.DTLS);

            if (!hasSrtp && !hasZrtp && !hasDtls) {
                mMediaEncryption.setEnabled(false);
            } else {
                if (hasSrtp) {
                    entries.add("SRTP");
                    values.add(String.valueOf(MediaEncryption.SRTP.toInt()));
                }
                if (hasZrtp) {
                    entries.add("ZRTP");
                    values.add(String.valueOf(MediaEncryption.ZRTP.toInt()));
                }
                if (hasDtls) {
                    entries.add("DTLS");
                    values.add(String.valueOf(MediaEncryption.DTLS.toInt()));
                }
            }
        }

        mMediaEncryption.setItems(entries, values);
    }
}
