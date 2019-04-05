package org.linphone.settings;

/*
AccountSettingsFragment.java
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
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.R;
import org.linphone.core.TransportType;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;

public class AccountSettingsFragment extends Fragment {
    protected View mRootView;
    protected LinphonePreferences mPrefs;

    private TextSetting mUsername,
            mUserId,
            mPassword,
            mDomain,
            mDisplayName,
            mProxy,
            mStun,
            mExpire,
            mPrefix,
            mAvpfInterval;
    private SwitchSetting mDisable,
            mUSeAsDefault,
            mOutboundProxy,
            mIce,
            mAvpf,
            mReplacePlusBy00,
            mPush;
    private BasicSetting mChangePassword, mDeleteAccount, mLinkAccount;
    private ListSetting mTransport;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.settings_account, container, false);

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
                            getString(R.string.pref_sipaccount));
        }

        updateValues();
    }

    protected void loadSettings() {
        mUsername = mRootView.findViewById(R.id.pref_username);

        mUserId = mRootView.findViewById(R.id.pref_auth_userid);

        mPassword = mRootView.findViewById(R.id.pref_passwd);
        mPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        mDomain = mRootView.findViewById(R.id.pref_domain);

        mDisplayName = mRootView.findViewById(R.id.pref_display_name);
        mDisplayName.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);

        mProxy = mRootView.findViewById(R.id.pref_proxy);
        mProxy.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mStun = mRootView.findViewById(R.id.pref_stun_server);
        mStun.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        mExpire = mRootView.findViewById(R.id.pref_expire);
        mExpire.setInputType(InputType.TYPE_CLASS_NUMBER);

        mPrefix = mRootView.findViewById(R.id.pref_prefix);
        mPrefix.setInputType(InputType.TYPE_CLASS_NUMBER);

        mAvpfInterval = mRootView.findViewById(R.id.pref_avpf_rr_interval);
        mAvpfInterval.setInputType(InputType.TYPE_CLASS_NUMBER);

        mDisable = mRootView.findViewById(R.id.pref_disable_account);

        mUSeAsDefault = mRootView.findViewById(R.id.pref_default_account);

        mOutboundProxy = mRootView.findViewById(R.id.pref_enable_outbound_proxy);

        mIce = mRootView.findViewById(R.id.pref_ice_enable);

        mAvpf = mRootView.findViewById(R.id.pref_avpf);

        mReplacePlusBy00 = mRootView.findViewById(R.id.pref_escape_plus);

        mPush = mRootView.findViewById(R.id.pref_push_notification);

        mChangePassword = mRootView.findViewById(R.id.pref_change_password);

        mDeleteAccount = mRootView.findViewById(R.id.pref_delete_account);

        mLinkAccount = mRootView.findViewById(R.id.pref_link_account);

        mTransport = mRootView.findViewById(R.id.pref_transport);
        initTransportList();
    }

    protected void setListeners() {
        mUsername.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mUserId.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mPassword.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mDomain.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mDisplayName.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mProxy.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mStun.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mExpire.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mPrefix.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mAvpfInterval.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {}
                });

        mDisable.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mUSeAsDefault.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mOutboundProxy.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mIce.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mAvpf.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mReplacePlusBy00.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mPush.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {}
                });

        mChangePassword.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {}
                });

        mDeleteAccount.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {}
                });

        mLinkAccount.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {}
                });

        mTransport.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(
                            int position, String newLabel, String newValue) {}
                });
    }

    protected void updateValues() {
        mUsername.setValue("");

        mUserId.setValue("");

        mPassword.setValue("");

        mDomain.setValue("");

        mDisplayName.setValue("");

        mProxy.setValue("");

        mStun.setValue("");

        mExpire.setValue(0);

        mPrefix.setValue(0);

        mAvpfInterval.setValue(0);

        mDisable.setChecked(false);

        mUSeAsDefault.setChecked(false);

        mOutboundProxy.setChecked(false);

        mIce.setChecked(false);

        mAvpf.setChecked(false);

        mReplacePlusBy00.setChecked(false);

        mPush.setChecked(false);

        mTransport.setValue(0);

        setListeners();
    }

    private void initTransportList() {
        List<String> entries = new ArrayList<>();
        List<String> values = new ArrayList<>();

        entries.add(getString(R.string.pref_transport_udp));
        values.add(String.valueOf(TransportType.Udp.toInt()));
        entries.add(getString(R.string.pref_transport_tcp));
        values.add(String.valueOf(TransportType.Tcp.toInt()));

        if (!getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
            entries.add(getString(R.string.pref_transport_tcp));
            values.add(String.valueOf(TransportType.Tls.toInt()));
        }

        mTransport.setItems(entries, values);
    }
}
