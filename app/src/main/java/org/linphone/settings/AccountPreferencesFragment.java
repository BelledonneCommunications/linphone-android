package org.linphone.settings;
/*
AccountPreferencesFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.assistant.AssistantActivity;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.CoreException;
import org.linphone.core.NatPolicy;
import org.linphone.core.ProxyConfig;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.mediastream.Log;
import org.linphone.settings.LinphonePreferences.AccountBuilder;
import org.linphone.utils.LinphoneUtils;

public class AccountPreferencesFragment extends PreferencesListFragment
        implements AccountCreatorListener {
    private int mN;
    private final OnPreferenceClickListener linkAccountListener =
            new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent assistant = new Intent();
                    assistant.setClass(LinphoneActivity.instance(), AssistantActivity.class);
                    assistant.putExtra("LinkPhoneNumber", true);
                    assistant.putExtra("FromPref", true);
                    assistant.putExtra("AccountNumber", mN);
                    startActivity(assistant);
                    return true;
                }
            };
    private boolean mIsNewAccount = false;
    private final LinphonePreferences mPrefs;
    private final OnPreferenceChangeListener mAvpfRRIntervalChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = newValue.toString();
                    try {
                        int intValue = Integer.parseInt(value);
                        if ((intValue < 1) || (intValue > 5)) {
                            return false;
                        }
                    } catch (NumberFormatException nfe) {
                        Log.e(nfe);
                    }
                    if (mIsNewAccount) {
                        // TODO
                    } else {
                        mPrefs.setAvpfRrInterval(mN, value);
                    }
                    preference.setSummary(value);
                    return true;
                }
            };
    private final OnPreferenceChangeListener mEscapeChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean value = (Boolean) newValue;
                    if (mIsNewAccount) {
                        // TODO
                    } else {
                        mPrefs.setReplacePlusByZeroZero(mN, value);
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mPushNotificationListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean value = (Boolean) newValue;
                    if (mIsNewAccount) {
                        // TODO
                    } else {
                        mPrefs.enablePushNotifForProxy(mN, value);
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mIceChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean value = (Boolean) newValue;
                    if (mIsNewAccount) {
                    } else {
                        mPrefs.setAccountIce(mN, value);
                        ((CheckBoxPreference) preference).setChecked(mPrefs.getAccountIce(mN));
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mStunTurnChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = newValue.toString();
                    if (mIsNewAccount) {
                    } else {
                        mPrefs.setAccountStunServer(mN, value);
                        preference.setSummary(value);
                    }
                    return true;
                }
            };
    private EditTextPreference mProxyPreference;
    private final OnPreferenceChangeListener mTransportChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String key = newValue.toString();
                    if (mIsNewAccount) {
                        // TODO
                        // mBuilder.setTransport(transport);
                    } else {
                        mPrefs.setAccountTransport(mN, key);
                        preference.setSummary(mPrefs.getAccountTransportString(mN));
                        preference.setDefaultValue(mPrefs.getAccountTransportKey(mN));
                        if (mProxyPreference != null) {
                            String newProxy = mPrefs.getAccountProxy(mN);
                            mProxyPreference.setSummary(newProxy);
                            mProxyPreference.setText(newProxy);
                        }
                    }
                    return true;
                }
            };
    private ListPreference mTransportPreference;
    private AccountBuilder mBuilder;
    private final OnPreferenceChangeListener mUsernameChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (isEditTextEmpty(newValue.toString())) return false;
                    if (mIsNewAccount) {
                        mBuilder.setUsername(newValue.toString());
                    } else {
                        mPrefs.setAccountUsername(mN, newValue.toString());
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            };
    private final OnPreferenceChangeListener mUseridChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (mIsNewAccount) {
                        mBuilder.setUserid(newValue.toString());
                    } else {
                        mPrefs.setAccountUserId(mN, newValue.toString());
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            };
    private final OnPreferenceChangeListener mPasswordChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (isEditTextEmpty(newValue.toString())) return false;
                    if (mIsNewAccount) {
                        mBuilder.setPassword(newValue.toString());
                    } else {
                        mPrefs.setAccountPassword(mN, newValue.toString());
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mDomainChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (isEditTextEmpty(newValue.toString())) return false;
                    if (mIsNewAccount) {
                        mBuilder.setDomain(newValue.toString());
                    } else {
                        mPrefs.setAccountDomain(mN, newValue.toString());
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            };
    private final OnPreferenceChangeListener mDisplayNameChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (mIsNewAccount) {
                        mBuilder.setDisplayName(newValue.toString());
                    } else {
                        mPrefs.setAccountDisplayName(mN, newValue.toString());
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            };
    private final OnPreferenceChangeListener mProxyChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = newValue.toString();
                    if (mIsNewAccount) {
                        mBuilder.setServerAddr(newValue.toString());
                        preference.setSummary(newValue.toString());
                    } else {
                        mPrefs.setAccountProxy(mN, value);
                        preference.setSummary(mPrefs.getAccountProxy(mN));

                        if (mTransportPreference != null) {
                            mTransportPreference.setSummary(mPrefs.getAccountTransportString(mN));
                            mTransportPreference.setValue(mPrefs.getAccountTransportKey(mN));
                        }
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mOutboundProxyChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (mIsNewAccount) {
                        mBuilder.setOutboundProxyEnabled((Boolean) newValue);
                    } else {
                        mPrefs.setAccountOutboundProxyEnabled(mN, (Boolean) newValue);
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mExpiresChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (mIsNewAccount) {
                        mBuilder.setExpires(newValue.toString());
                    } else {
                        mPrefs.setExpires(mN, newValue.toString());
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            };
    private final OnPreferenceChangeListener mPrefixChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String value = newValue.toString();
                    preference.setSummary(value);
                    if (mIsNewAccount) {
                        mBuilder.setPrefix(value);
                    } else {
                        mPrefs.setPrefix(mN, value);
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mAvpfChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean value = (Boolean) newValue;
                    if (!mIsNewAccount) {
                        mPrefs.setAvpfMode(mN, value);
                    }
                    return true;
                }
            };
    private final OnPreferenceChangeListener mDisableChangedListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean value = (Boolean) newValue;
                    if (mIsNewAccount) {
                        mBuilder.setEnabled(!value);
                    } else {
                        mPrefs.setAccountEnabled(mN, !value);
                    }
                    return true;
                }
            };
    private AccountCreator mAccountCreator;

    public AccountPreferencesFragment() {
        super(R.xml.account_preferences);
        mPrefs = LinphonePreferences.instance();
    }

    private static boolean isEditTextEmpty(String s) {
        return s.equals(""); // really empty.
    }

    private static void setListPreferenceValues(
            ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
        CharSequence[] contents = new CharSequence[entries.size()];
        entries.toArray(contents);
        pref.setEntries(contents);
        contents = new CharSequence[values.size()];
        values.toArray(contents);
        pref.setEntryValues(contents);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mN = getArguments().getInt("Account", 0);
        if (mN == mPrefs.getAccountCount()) {
            mIsNewAccount = true;
            mBuilder = new AccountBuilder(LinphoneManager.getLc());
        }
        initAccountPreferencesFields();

        // Force hide keyboard
        getActivity()
                .getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void initAccountPreferencesFields() {
        boolean isDefaultAccount = mPrefs.getDefaultAccountIndex() == mN;
        NatPolicy natPolicy = null;
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null
                && LinphoneManager.getLc().getProxyConfigList() != null
                && LinphoneManager.getLc().getProxyConfigList().length > mN) {
            ProxyConfig proxy = LinphoneManager.getLc().getProxyConfigList()[mN];
            natPolicy = proxy.getNatPolicy();
            if (natPolicy == null) {
                natPolicy = LinphoneManager.getLc().createNatPolicy();
                proxy.edit();
                proxy.setNatPolicy(natPolicy);
                proxy.done();
            }
        }

        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);

        final PreferenceCategory account =
                (PreferenceCategory)
                        getPreferenceScreen()
                                .findPreference(getString(R.string.pref_sipaccount_key));
        EditTextPreference username = (EditTextPreference) account.getPreference(0);
        username.setOnPreferenceChangeListener(mUsernameChangedListener);
        if (!mIsNewAccount) {
            username.setText(mPrefs.getAccountUsername(mN));
            username.setSummary(username.getText());
        }

        EditTextPreference userid = (EditTextPreference) account.getPreference(1);
        userid.setOnPreferenceChangeListener(mUseridChangedListener);
        if (!mIsNewAccount) {
            userid.setText(mPrefs.getAccountUserId(mN));
            userid.setSummary(userid.getText());
        }

        EditTextPreference password = (EditTextPreference) account.getPreference(2);
        password.setOnPreferenceChangeListener(mPasswordChangedListener);
        if (!mIsNewAccount) {
            password.setText(mPrefs.getAccountPassword(mN));
        }

        EditTextPreference domain = (EditTextPreference) account.getPreference(3);
        domain.setOnPreferenceChangeListener(mDomainChangedListener);
        if (!mIsNewAccount) {
            domain.setText(mPrefs.getAccountDomain(mN));
            domain.setSummary(domain.getText());
        }

        EditTextPreference displayName = (EditTextPreference) account.getPreference(4);
        displayName.setOnPreferenceChangeListener(mDisplayNameChangedListener);
        if (!mIsNewAccount) {
            displayName.setText(mPrefs.getAccountDisplayName(mN));
            displayName.setSummary(displayName.getText());
        }

        PreferenceCategory advanced =
                (PreferenceCategory)
                        getPreferenceScreen().findPreference(getString(R.string.pref_advanced_key));
        mTransportPreference = (ListPreference) advanced.getPreference(0);
        initializeTransportPreference(mTransportPreference);
        mTransportPreference.setOnPreferenceChangeListener(mTransportChangedListener);
        if (!mIsNewAccount) {
            mTransportPreference.setSummary(mPrefs.getAccountTransportString(mN));
        }

        CheckBoxPreference ice = (CheckBoxPreference) advanced.getPreference(1);
        ice.setOnPreferenceChangeListener(mIceChangedListener);
        if (natPolicy != null) ice.setChecked(natPolicy.iceEnabled());

        EditTextPreference stunTurn = (EditTextPreference) advanced.getPreference(2);
        stunTurn.setOnPreferenceChangeListener(mStunTurnChangedListener);
        if (natPolicy != null) {
            stunTurn.setText(natPolicy.getStunServer());
            stunTurn.setSummary(natPolicy.getStunServer());
        }

        mProxyPreference = (EditTextPreference) advanced.getPreference(3);
        mProxyPreference.setOnPreferenceChangeListener(mProxyChangedListener);
        if (!mIsNewAccount) {
            mProxyPreference.setText(mPrefs.getAccountProxy(mN));
            mProxyPreference.setSummary(
                    "".equals(mProxyPreference.getText()) || (mProxyPreference.getText() == null)
                            ? getString(R.string.pref_help_proxy)
                            : mProxyPreference.getText());
        }

        CheckBoxPreference outboundProxy = (CheckBoxPreference) advanced.getPreference(4);
        outboundProxy.setOnPreferenceChangeListener(mOutboundProxyChangedListener);
        if (!mIsNewAccount) {
            outboundProxy.setChecked(mPrefs.isAccountOutboundProxySet(mN));
        }

        EditTextPreference expires = (EditTextPreference) advanced.getPreference(5);
        expires.setOnPreferenceChangeListener(mExpiresChangedListener);
        if (!mIsNewAccount) {
            expires.setText(mPrefs.getExpires(mN));
            expires.setSummary(mPrefs.getExpires(mN));
        }

        EditTextPreference prefix = (EditTextPreference) advanced.getPreference(6);
        prefix.setOnPreferenceChangeListener(mPrefixChangedListener);
        if (!mIsNewAccount) {
            String prefixValue = mPrefs.getPrefix(mN);
            prefix.setText(prefixValue);
            prefix.setSummary(prefixValue);
        }

        CheckBoxPreference avpf = (CheckBoxPreference) advanced.getPreference(7);
        avpf.setOnPreferenceChangeListener(mAvpfChangedListener);
        if (!mIsNewAccount) {
            avpf.setChecked(mPrefs.avpfEnabled(mN));
        }

        EditTextPreference avpfRRInterval = (EditTextPreference) advanced.getPreference(8);
        avpfRRInterval.setOnPreferenceChangeListener(mAvpfRRIntervalChangedListener);
        if (!mIsNewAccount) {
            avpfRRInterval.setText(mPrefs.getAvpfRrInterval(mN));
            avpfRRInterval.setSummary(mPrefs.getAvpfRrInterval(mN));
        }

        CheckBoxPreference escape = (CheckBoxPreference) advanced.getPreference(9);
        escape.setOnPreferenceChangeListener(mEscapeChangedListener);
        if (!mIsNewAccount) {
            escape.setChecked(mPrefs.getReplacePlusByZeroZero(mN));
        }

        Preference linkAccount = advanced.getPreference(10);
        linkAccount.setOnPreferenceClickListener(linkAccountListener);

        CheckBoxPreference pushNotif = (CheckBoxPreference) advanced.getPreference(11);
        pushNotif.setOnPreferenceChangeListener(mPushNotificationListener);
        if (!mIsNewAccount) {
            pushNotif.setChecked(mPrefs.isPushNotifEnabledForProxy(mN));
        }

        PreferenceCategory manage =
                (PreferenceCategory)
                        getPreferenceScreen().findPreference(getString(R.string.pref_manage_key));
        final CheckBoxPreference disable = (CheckBoxPreference) manage.getPreference(0);
        disable.setEnabled(true);
        disable.setOnPreferenceChangeListener(mDisableChangedListener);
        if (!mIsNewAccount) {
            disable.setChecked(!mPrefs.isAccountEnabled(mN));
        }

        CheckBoxPreference mainAccount = (CheckBoxPreference) manage.getPreference(1);
        mainAccount.setChecked(isDefaultAccount);
        mainAccount.setEnabled(!mainAccount.isChecked());
        mainAccount.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        mPrefs.setDefaultAccount(mN);
                        disable.setEnabled(false);
                        disable.setChecked(false);
                        preference.setEnabled(false);
                        return true;
                    }
                });
        if (!mIsNewAccount) {
            mainAccount.setEnabled(!mainAccount.isChecked());
        }

        final Preference changePassword = manage.getPreference(2);
        changePassword.setEnabled(false);

        final Preference delete = manage.getPreference(3);
        delete.setEnabled(!mIsNewAccount);
        delete.setOnPreferenceClickListener(
                new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        mPrefs.deleteAccount(mN);
                        LinphoneActivity.instance().displaySettings();
                        LinphoneActivity.instance().refreshAccounts();
                        return true;
                    }
                });
    }

    private void initializeTransportPreference(ListPreference pref) {
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add(getString(R.string.pref_transport_udp));
        values.add(getString(R.string.pref_transport_udp_key));
        entries.add(getString(R.string.pref_transport_tcp));
        values.add(getString(R.string.pref_transport_tcp_key));

        if (!getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
            entries.add(getString(R.string.pref_transport_tls));
            values.add(getString(R.string.pref_transport_tls_key));
        }
        setListPreferenceValues(pref, entries, values);

        if (!mIsNewAccount) {
            pref.setSummary(mPrefs.getAccountTransportString(mN));
            pref.setDefaultValue(mPrefs.getAccountTransportKey(mN));
            pref.setValueIndex(entries.indexOf(mPrefs.getAccountTransportString(mN)));
        } else {

            pref.setSummary(getString(R.string.pref_transport_udp));
            pref.setDefaultValue(getString(R.string.pref_transport_udp));
            pref.setValueIndex(entries.indexOf(getString(R.string.pref_transport_udp)));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.ACCOUNT_SETTINGS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (LinphoneActivity.isInstanciated()) {
            try {
                if (mIsNewAccount) {
                    mBuilder.saveNewAccount();
                }
            } catch (CoreException e) {
                Log.e(e);
            }
            LinphoneActivity.instance().isNewProxyConfig();
            LinphoneManager.getLc().refreshRegisters();
            LinphoneActivity.instance().hideTopBar();
        }
    }

    @Override
    public void onUpdateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (status.equals(AccountCreator.Status.RequestOk)) {
            mPrefs.setAccountPassword(mN, accountCreator.getPassword());
            PreferenceCategory account =
                    (PreferenceCategory)
                            getPreferenceScreen()
                                    .findPreference(getString(R.string.pref_sipaccount_key));
            ((EditTextPreference) account.getPreference(2)).setText(mPrefs.getAccountPassword(mN));
            LinphoneUtils.displayErrorAlert(
                    getString(R.string.pref_password_changed), LinphoneActivity.instance());
        } else {
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(status), LinphoneActivity.instance());
        }
    }

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onCreateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onLinkAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAlias(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountActivated(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onRecoverAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountLinked(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAliasUsed(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}
}
