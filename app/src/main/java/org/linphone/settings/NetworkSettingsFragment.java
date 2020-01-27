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

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.PushNotificationUtils;

public class NetworkSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private SwitchSetting mWifiOnly, mIpv6, mPush, mRandomPorts, mIce, mTurn;
    private TextSetting mSipPort, mStunServer, mTurnUsername, mTurnPassword;
    private BasicSetting mAndroidBatterySaverSettings;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_network, container, false);

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
        mWifiOnly = mRootView.findViewById(R.id.pref_wifi_only);

        mIpv6 = mRootView.findViewById(R.id.pref_ipv6);

        mPush = mRootView.findViewById(R.id.pref_push_notification);

        mRandomPorts = mRootView.findViewById(R.id.pref_transport_use_random_ports);

        mIce = mRootView.findViewById(R.id.pref_ice_enable);

        mTurn = mRootView.findViewById(R.id.pref_turn_enable);

        mSipPort = mRootView.findViewById(R.id.pref_sip_port);
        mSipPort.setInputType(InputType.TYPE_CLASS_NUMBER);

        mStunServer = mRootView.findViewById(R.id.pref_stun_server);
        mStunServer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mTurnUsername = mRootView.findViewById(R.id.pref_turn_username);

        mTurnPassword = mRootView.findViewById(R.id.pref_turn_passwd);
        mTurnPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        mAndroidBatterySaverSettings =
                mRootView.findViewById(R.id.pref_android_battery_protected_settings);
    }

    private void setListeners() {
        mWifiOnly.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setWifiOnlyEnabled(newValue);
                    }
                });

        mIpv6.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.useIpv6(newValue);
                    }
                });

        mPush.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setPushNotificationEnabled(newValue);
                    }
                });

        mRandomPorts.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.useRandomPort(newValue);
                        mSipPort.setVisibility(
                                mPrefs.isUsingRandomPort() ? View.GONE : View.VISIBLE);
                    }
                });

        mIce.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setIceEnabled(newValue);
                    }
                });

        mTurn.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.setTurnEnabled(newValue);
                        mTurnUsername.setEnabled(mPrefs.isTurnEnabled());
                        mTurnPassword.setEnabled(mPrefs.isTurnEnabled());
                    }
                });

        mSipPort.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setSipPort(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mStunServer.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setStunServer(newValue);
                        mIce.setEnabled(
                                mPrefs.getStunServer() != null
                                        && !mPrefs.getStunServer().isEmpty());
                        mTurn.setEnabled(
                                mPrefs.getStunServer() != null
                                        && !mPrefs.getStunServer().isEmpty());
                        if (newValue == null || newValue.isEmpty()) {
                            mIce.setChecked(false);
                            mTurn.setChecked(false);
                        }
                    }
                });

        mTurnUsername.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setTurnUsername(newValue);
                    }
                });

        mTurnPassword.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setTurnPassword(newValue);
                    }
                });

        mAndroidBatterySaverSettings.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        mPrefs.powerSaverDialogPrompted(true);
                        Intent intent = DeviceUtils.getDevicePowerManagerIntent(getActivity());
                        if (intent != null) {
                            try {
                                startActivity(intent);
                            } catch (SecurityException se) {
                                Log.e("[Network Settings] Security exception: ", se);
                            }
                        }
                    }
                });
    }

    private void updateValues() {
        mWifiOnly.setChecked(mPrefs.isWifiOnlyEnabled());

        mIpv6.setChecked(mPrefs.isUsingIpv6());

        mPush.setChecked(mPrefs.isPushNotificationEnabled());
        mPush.setVisibility(
                PushNotificationUtils.isAvailable(getActivity()) ? View.VISIBLE : View.GONE);

        mRandomPorts.setChecked(mPrefs.isUsingRandomPort());

        mIce.setChecked(mPrefs.isIceEnabled());
        mIce.setEnabled(mPrefs.getStunServer() != null && !mPrefs.getStunServer().isEmpty());

        mTurn.setChecked(mPrefs.isTurnEnabled());
        mTurn.setEnabled(mPrefs.getStunServer() != null && !mPrefs.getStunServer().isEmpty());

        mSipPort.setValue(mPrefs.getSipPort());
        mSipPort.setVisibility(mPrefs.isUsingRandomPort() ? View.GONE : View.VISIBLE);

        mStunServer.setValue(mPrefs.getStunServer());

        mTurnUsername.setValue(mPrefs.getTurnUsername());
        mTurnUsername.setEnabled(mPrefs.isTurnEnabled());
        mTurnPassword.setEnabled(mPrefs.isTurnEnabled());

        mAndroidBatterySaverSettings.setVisibility(
                DeviceUtils.hasDevicePowerManager(getActivity()) ? View.VISIBLE : View.GONE);

        setListeners();
    }
}
