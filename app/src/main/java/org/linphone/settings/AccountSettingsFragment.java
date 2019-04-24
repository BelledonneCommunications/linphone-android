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
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.assistant.AssistantActivity;
import org.linphone.core.AVPFMode;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.NatPolicy;
import org.linphone.core.ProxyConfig;
import org.linphone.core.TransportType;
import org.linphone.core.tools.Log;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.settings.widget.BasicSetting;
import org.linphone.settings.widget.ListSetting;
import org.linphone.settings.widget.SettingListenerBase;
import org.linphone.settings.widget.SwitchSetting;
import org.linphone.settings.widget.TextSetting;
import org.linphone.utils.PushNotificationUtils;

public class AccountSettingsFragment extends Fragment {
    protected View mRootView;
    protected LinphonePreferences mPrefs;
    private int mAccountIndex;
    private ProxyConfig mProxyConfig;
    private AuthInfo mAuthInfo;
    private boolean mIsNewlyCreatedAccount;

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
            mUseAsDefault,
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

        mIsNewlyCreatedAccount = true;
        mAccountIndex = getArguments().getInt("Account", -1);
        if (mAccountIndex == -1 && savedInstanceState != null) {
            mAccountIndex = savedInstanceState.getInt("Account", -1);
        }

        mProxyConfig = null;
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (mAccountIndex >= 0 && core != null) {
            ProxyConfig[] proxyConfigs = core.getProxyConfigList();
            if (proxyConfigs.length > mAccountIndex) {
                mProxyConfig = proxyConfigs[mAccountIndex];
                mIsNewlyCreatedAccount = false;
            } else {
                Log.e("[Account Settings] Proxy config not found !");
            }
        }

        return mRootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("Account", mAccountIndex);
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

    @Override
    public void onPause() {
        super.onPause();
        if (mIsNewlyCreatedAccount) {
            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (core != null && mProxyConfig != null && mAuthInfo != null) {
                core.addAuthInfo(mAuthInfo);
                core.addProxyConfig(mProxyConfig);
                if (mUseAsDefault.isChecked()) {
                    core.setDefaultProxyConfig(mProxyConfig);
                }
            }
        }
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

        mAvpfInterval = mRootView.findViewById(R.id.pref_avpf_rr_interval);
        mAvpfInterval.setInputType(InputType.TYPE_CLASS_NUMBER);

        mDisable = mRootView.findViewById(R.id.pref_disable_account);

        mUseAsDefault = mRootView.findViewById(R.id.pref_default_account);

        mOutboundProxy = mRootView.findViewById(R.id.pref_enable_outbound_proxy);

        mIce = mRootView.findViewById(R.id.pref_ice_enable);

        mAvpf = mRootView.findViewById(R.id.pref_avpf);

        mReplacePlusBy00 = mRootView.findViewById(R.id.pref_escape_plus);

        mPush = mRootView.findViewById(R.id.pref_push_notification);
        mPush.setVisibility(
                PushNotificationUtils.isAvailable(getActivity()) ? View.VISIBLE : View.GONE);

        mChangePassword = mRootView.findViewById(R.id.pref_change_password);
        mChangePassword.setVisibility(View.GONE); // TODO

        mDeleteAccount = mRootView.findViewById(R.id.pref_delete_account);

        mLinkAccount = mRootView.findViewById(R.id.pref_link_account);

        mTransport = mRootView.findViewById(R.id.pref_transport);
        initTransportList();
    }

    protected void setListeners() {
        mUsername.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (newValue.isEmpty()) {
                            return;
                        }

                        if (mAuthInfo != null) {
                            mAuthInfo.setUsername(newValue);
                        } else {
                            Log.e("[Account Settings] No auth info !");
                        }

                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            Address identity = mProxyConfig.getIdentityAddress();
                            if (identity != null) {
                                identity.setUsername(newValue);
                            }
                            mProxyConfig.setIdentityAddress(identity);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mUserId.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mAuthInfo != null) {
                            mAuthInfo.setUserid(newValue);

                            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (core != null) {
                                core.refreshRegisters();
                            }
                        } else {
                            Log.e("[Account Settings] No auth info !");
                        }
                    }
                });

        mPassword.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mAuthInfo != null) {
                            mAuthInfo.setHa1(null);
                            mAuthInfo.setPassword(newValue);

                            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (core != null) {
                                core.addAuthInfo(mAuthInfo);
                                core.refreshRegisters();
                            }
                        } else {
                            Log.e("[Account Settings] No auth info !");
                        }
                    }
                });

        mDomain.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (newValue.isEmpty()) {
                            return;
                        }

                        if (mAuthInfo != null) {
                            mAuthInfo.setDomain(newValue);
                        } else {
                            Log.e("[Account Settings] No auth info !");
                        }

                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            Address identity = mProxyConfig.getIdentityAddress();
                            if (identity != null) {
                                identity.setDomain(newValue);
                            }
                            mProxyConfig.setIdentityAddress(identity);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mDisplayName.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            Address identity = mProxyConfig.getIdentityAddress();
                            if (identity != null) {
                                identity.setDisplayName(newValue);
                            }
                            mProxyConfig.setIdentityAddress(identity);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mProxy.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            Address proxy = Factory.instance().createAddress(newValue);
                            if (proxy != null) {
                                mProxyConfig.setServerAddr(proxy.asString());
                                if (mOutboundProxy.isChecked()) {
                                    mProxyConfig.setRoute(proxy.asString());
                                }
                                mTransport.setValue(proxy.getTransport().toInt());
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mStun.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            NatPolicy natPolicy = mProxyConfig.getNatPolicy();
                            if (natPolicy == null) {
                                Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                                if (core != null) {
                                    natPolicy = core.createNatPolicy();
                                    mProxyConfig.setNatPolicy(natPolicy);
                                }
                            }
                            if (natPolicy != null) {
                                natPolicy.setStunServer(newValue);
                            }
                            if (newValue == null || newValue.isEmpty()) {
                                mIce.setChecked(false);
                            }
                            mIce.setEnabled(newValue != null && !newValue.isEmpty());
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mExpire.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            try {
                                mProxyConfig.setExpires(Integer.parseInt(newValue));
                            } catch (NumberFormatException nfe) {
                                Log.e(nfe);
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mPrefix.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            mProxyConfig.setDialPrefix(newValue);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mAvpfInterval.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onTextValueChanged(String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            try {
                                mProxyConfig.setAvpfRrInterval(Integer.parseInt(newValue));
                            } catch (NumberFormatException nfe) {
                                Log.e(nfe);
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mDisable.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            mProxyConfig.enableRegister(!newValue);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mUseAsDefault.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (core != null && newValue) {
                                core.setDefaultProxyConfig(mProxyConfig);
                                mUseAsDefault.setEnabled(false);
                            }
                            LinphoneActivity.instance().refreshAccounts();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mOutboundProxy.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            if (newValue) {
                                mProxyConfig.setRoute(mProxy.getValue());
                            } else {
                                mProxyConfig.setRoute(null);
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mIce.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();

                            NatPolicy natPolicy = mProxyConfig.getNatPolicy();
                            if (natPolicy == null) {
                                Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                                if (core != null) {
                                    natPolicy = core.createNatPolicy();
                                    mProxyConfig.setNatPolicy(natPolicy);
                                }
                            }

                            if (natPolicy != null) {
                                natPolicy.enableIce(newValue);
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mAvpf.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            mProxyConfig.setAvpfMode(
                                    newValue ? AVPFMode.Enabled : AVPFMode.Disabled);
                            mAvpfInterval.setEnabled(mProxyConfig.avpfEnabled());
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mReplacePlusBy00.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            mProxyConfig.setDialEscapePlus(newValue);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mPush.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onBoolValueChanged(boolean newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            mProxyConfig.setPushNotificationAllowed(newValue);
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });

        mChangePassword.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        // TODO
                    }
                });

        mDeleteAccount.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                        if (core != null) {
                            if (mProxyConfig != null) {
                                core.removeProxyConfig(mProxyConfig);
                            }
                            if (mAuthInfo != null) {
                                core.removeAuthInfo(mAuthInfo);
                            }
                        }
                        LinphoneActivity.instance().displaySettings();
                        LinphoneActivity.instance().refreshAccounts();
                    }
                });

        mLinkAccount.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onClicked() {
                        Intent assistant = new Intent();
                        assistant.setClass(LinphoneActivity.instance(), AssistantActivity.class);
                        assistant.putExtra("LinkPhoneNumber", true);
                        assistant.putExtra("FromPref", true);
                        assistant.putExtra("AccountNumber", mAccountIndex);
                        startActivity(assistant);
                    }
                });

        mTransport.setListener(
                new SettingListenerBase() {
                    @Override
                    public void onListValueChanged(int position, String newLabel, String newValue) {
                        if (mProxyConfig != null) {
                            mProxyConfig.edit();
                            String server = mProxyConfig.getServerAddr();
                            Address serverAddr = Factory.instance().createAddress(server);
                            if (serverAddr != null) {
                                try {
                                    serverAddr.setTransport(
                                            TransportType.fromInt(Integer.parseInt(newValue)));
                                    server = serverAddr.asString();
                                    mProxyConfig.setServerAddr(server);
                                    if (mOutboundProxy.isChecked()) {
                                        mProxyConfig.setRoute(server);
                                    }
                                    mProxy.setValue(server);
                                } catch (NumberFormatException nfe) {
                                    Log.e(nfe);
                                }
                            }
                            mProxyConfig.done();
                        } else {
                            Log.e("[Account Settings] No proxy config !");
                        }
                    }
                });
    }

    protected void updateValues() {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core == null) return;

        // Create a proxy config if there is none
        if (mProxyConfig == null) {
            // Ensure the default configuration is loaded first
            String defaultConfig = LinphoneManager.getInstance().getDefaultDynamicConfigFile();
            core.loadConfigFromXml(defaultConfig);
            mProxyConfig = core.createProxyConfig();
            mAuthInfo = Factory.instance().createAuthInfo(null, null, null, null, null, null);
            mIsNewlyCreatedAccount = true;
        }

        if (mProxyConfig != null) {
            Address identityAddress = mProxyConfig.getIdentityAddress();
            mAuthInfo = mProxyConfig.findAuthInfo();

            NatPolicy natPolicy = mProxyConfig.getNatPolicy();
            if (natPolicy == null) {
                natPolicy = core.createNatPolicy();
                core.setNatPolicy(natPolicy);
            }

            if (mAuthInfo != null) {
                mUserId.setValue(mAuthInfo.getUserid());
                // If password is hashed we can't display it
                mPassword.setValue(mAuthInfo.getPassword());
            }

            mUsername.setValue(identityAddress.getUsername());

            mDomain.setValue(identityAddress.getDomain());

            mDisplayName.setValue(identityAddress.getDisplayName());

            mProxy.setValue(mProxyConfig.getServerAddr());

            mStun.setValue(natPolicy.getStunServer());

            mExpire.setValue(mProxyConfig.getExpires());

            mPrefix.setValue(mProxyConfig.getDialPrefix());

            mAvpfInterval.setValue(mProxyConfig.getAvpfRrInterval());
            mAvpfInterval.setEnabled(mProxyConfig.avpfEnabled());

            mDisable.setChecked(!mProxyConfig.registerEnabled());

            mUseAsDefault.setChecked(
                    core != null && mProxyConfig.equals(core.getDefaultProxyConfig()));
            mUseAsDefault.setEnabled(!mUseAsDefault.isChecked());

            mOutboundProxy.setChecked(mProxyConfig.getRoute() != null);

            mIce.setChecked(natPolicy.iceEnabled());
            mIce.setEnabled(
                    natPolicy.getStunServer() != null && !natPolicy.getStunServer().isEmpty());

            mAvpf.setChecked(mProxyConfig.avpfEnabled());

            mReplacePlusBy00.setChecked(mProxyConfig.getDialEscapePlus());

            mPush.setChecked(mProxyConfig.isPushNotificationAllowed());

            Address proxy = Factory.instance().createAddress(mProxyConfig.getServerAddr());
            if (proxy != null) {
                mTransport.setValue(proxy.getTransport().toInt());
            }
        }

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
            entries.add(getString(R.string.pref_transport_tls));
            values.add(String.valueOf(TransportType.Tls.toInt()));
        }

        mTransport.setItems(entries, values);
    }
}
