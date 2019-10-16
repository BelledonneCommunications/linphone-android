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

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class TunnelSettingsFragment extends SettingsFragment {
    private View mRootView;
    private LinphonePreferences mPrefs;

    private TextSetting mHost, mPort, mHost2, mPort2;
    private SwitchSetting mDualMode;
    private ListSetting mMode;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_tunnel, container, false);

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
        mHost = mRootView.findViewById(R.id.pref_tunnel_host);

        mPort = mRootView.findViewById(R.id.pref_tunnel_port);
        mPort.setInputType(InputType.TYPE_CLASS_NUMBER);

        mHost2 = mRootView.findViewById(R.id.pref_tunnel_host_2);

        mPort2 = mRootView.findViewById(R.id.pref_tunnel_port_2);
        mPort2.setInputType(InputType.TYPE_CLASS_NUMBER);

        mMode = mRootView.findViewById(R.id.pref_tunnel_mode);

        mDualMode = mRootView.findViewById(R.id.pref_tunnel_dual_mode);
    }

    private void setListeners() {
        mHost.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setTunnelHost(newValue);
                    }
                });

        mPort.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setTunnelPort(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mHost2.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        mPrefs.setTunnelHost2(newValue);
                    }
                });

        mPort2.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        try {
                            mPrefs.setTunnelPort2(Integer.valueOf(newValue));
                        } catch (NumberFormatException nfe) {
                            Log.e(nfe);
                        }
                    }
                });

        mMode.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        mPrefs.setTunnelMode(newValue);
                    }
                });

        mDualMode.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        mPrefs.enableTunnelDualMode(newValue);
                    }
                });
    }

    private void updateValues() {
        mHost.setValue(mPrefs.getTunnelHost());

        mPort.setValue(mPrefs.getTunnelPort());

        mHost2.setValue(mPrefs.getTunnelHost2());

        mPort2.setValue(mPrefs.getTunnelPort2());

        mMode.setValue(mPrefs.getTunnelMode());

        mDualMode.setChecked(mPrefs.isTunnelDualModeEnabled());

        setListeners();
    }
}
