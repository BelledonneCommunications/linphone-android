package org.linphone.fragments;
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

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.view.WindowManager;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphonePreferences.AccountBuilder;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.assistant.AssistantActivity;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.CoreException;
import org.linphone.core.NatPolicy;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.ui.PreferencesListFragment;

import java.util.ArrayList;
import java.util.List;

public class AccountPreferencesFragment extends PreferencesListFragment implements AccountCreatorListener {
    private int n;
    private boolean isNewAccount = false;
    private LinphonePreferences mPrefs;
    private EditTextPreference mProxyPreference;
    private ListPreference mTransportPreference;
    private AccountBuilder builder;
    private AccountCreator accountCreator;
    private ProgressDialog progress;

    public AccountPreferencesFragment() {
        super(R.xml.account_preferences);
        mPrefs = LinphonePreferences.instance();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceScreen screen = getPreferenceScreen();
        n = getArguments().getInt("Account", 0);
        if (n == mPrefs.getAccountCount()) {
            isNewAccount = true;
            builder = new AccountBuilder(LinphoneManager.getLc());
        }
        initAccountPreferencesFields(screen);

        // Force hide keyboard
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    public static boolean isEditTextEmpty(String s) {
        return s.equals("");  // really empty.
    }

    OnPreferenceChangeListener usernameChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isEditTextEmpty(newValue.toString())) return false;
            if (isNewAccount) {
                builder.setUsername(newValue.toString());
            } else {
                mPrefs.setAccountUsername(n, newValue.toString());
            }
            preference.setSummary(newValue.toString());
            return true;
        }
    };
    OnPreferenceChangeListener useridChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isNewAccount) {
                builder.setUserid(newValue.toString());
            } else {
                mPrefs.setAccountUserId(n, newValue.toString());
            }
            preference.setSummary(newValue.toString());
            return true;
        }
    };
    OnPreferenceChangeListener passwordChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isEditTextEmpty(newValue.toString())) return false;
            if (isNewAccount) {
                builder.setPassword(newValue.toString());
            } else {
                mPrefs.setAccountPassword(n, newValue.toString());
            }
            return true;
        }
    };
    OnPreferenceChangeListener domainChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isEditTextEmpty(newValue.toString())) return false;
            if (isNewAccount) {
                builder.setDomain(newValue.toString());
            } else {
                mPrefs.setAccountDomain(n, newValue.toString());
            }
            preference.setSummary(newValue.toString());
            return true;
        }
    };
    OnPreferenceChangeListener displayNameChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isNewAccount) {
                builder.setDisplayName(newValue.toString());
            } else {
                mPrefs.setAccountDisplayName(n, newValue.toString());
            }
            preference.setSummary(newValue.toString());
            return true;
        }
    };
    OnPreferenceChangeListener proxyChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            if (isNewAccount) {
                builder.setServerAddr(newValue.toString());
                preference.setSummary(newValue.toString());
            } else {
                mPrefs.setAccountProxy(n, value);
                preference.setSummary(mPrefs.getAccountProxy(n));

                if (mTransportPreference != null) {
                    mTransportPreference.setSummary(mPrefs.getAccountTransportString(n));
                    mTransportPreference.setValue(mPrefs.getAccountTransportKey(n));
                }
            }
            return true;
        }
    };
    OnPreferenceChangeListener outboundProxyChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isNewAccount) {
                builder.setOutboundProxyEnabled((Boolean) newValue);
            } else {
                mPrefs.setAccountOutboundProxyEnabled(n, (Boolean) newValue);
            }
            return true;
        }
    };
    OnPreferenceChangeListener expiresChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (isNewAccount) {
                builder.setExpires(newValue.toString());
            } else {
                mPrefs.setExpires(n, newValue.toString());
            }
            preference.setSummary(newValue.toString());
            return true;
        }
    };
    OnPreferenceChangeListener prefixChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            preference.setSummary(value);
            if (isNewAccount) {
                builder.setPrefix(value);
            } else {
                mPrefs.setPrefix(n, value);
            }
            return true;
        }
    };
    OnPreferenceChangeListener avpfChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean value = (Boolean) newValue;
            if (isNewAccount) {
                builder.setAvpfEnabled(value);
            } else {
                mPrefs.setAvpfMode(n, value);
            }
            return true;
        }
    };
    OnPreferenceChangeListener avpfRRIntervalChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            try {
                int intValue = Integer.parseInt(value);
                if ((intValue < 1) || (intValue > 5)) {
                    return false;
                }
            } catch (NumberFormatException nfe) {
            }
            if (isNewAccount) {
                //TODO
            } else {
                mPrefs.setAvpfRrInterval(n, value);
            }
            preference.setSummary(value);
            return true;
        }
    };
    OnPreferenceChangeListener escapeChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean value = (Boolean) newValue;
            if (isNewAccount) {
                //TODO
            } else {
                mPrefs.setReplacePlusByZeroZero(n, value);
            }
            return true;
        }
    };
    OnPreferenceClickListener linkAccountListener = new OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(Preference preference) {
            Intent assistant = new Intent();
            assistant.setClass(LinphoneActivity.instance(), AssistantActivity.class);
            assistant.putExtra("LinkPhoneNumber", true);
            assistant.putExtra("FromPref", true);
            assistant.putExtra("AccountNumber", n);
            startActivity(assistant);
            return true;
        }
    };
    OnPreferenceChangeListener pushNotificationListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean value = (Boolean) newValue;
            if (isNewAccount) {
                //TODO
            } else {
                mPrefs.enablePushNotifForProxy(n, value);
            }
            return true;
        }
    };
    OnPreferenceChangeListener disableChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean value = (Boolean) newValue;
            if (isNewAccount) {
                builder.setEnabled(!value);
            } else {
                mPrefs.setAccountEnabled(n, !value);
            }
            return true;
        }
    };
    OnPreferenceChangeListener transportChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = newValue.toString();
            if (isNewAccount) {
                //TODO
                //builder.setTransport(transport);
            } else {
                mPrefs.setAccountTransport(n, key);
                preference.setSummary(mPrefs.getAccountTransportString(n));
                preference.setDefaultValue(mPrefs.getAccountTransportKey(n));
                if (mProxyPreference != null) {
                    String newProxy = mPrefs.getAccountProxy(n);
                    mProxyPreference.setSummary(newProxy);
                    mProxyPreference.setText(newProxy);
                }
            }
            return true;
        }
    };

    OnPreferenceChangeListener iceChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            boolean value = (Boolean) newValue;
            if (isNewAccount) {
            } else {
                mPrefs.setAccountIce(n, value);
                ((CheckBoxPreference) preference).setChecked(mPrefs.getAccountIce(n));
            }
            return true;
        }
    };

    OnPreferenceChangeListener stunTurnChangedListener = new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String value = newValue.toString();
            if (isNewAccount) {
            } else {
                mPrefs.setAccountStunServer(n, value);
                preference.setSummary(value);
            }
            return true;
        }
    };

    private void initAccountPreferencesFields(PreferenceScreen parent) {
        boolean isDefaultAccount = mPrefs.getDefaultAccountIndex() == n;
        NatPolicy natPolicy = null;
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null &&
                LinphoneManager.getLc().getProxyConfigList() != null &&
                LinphoneManager.getLc().getProxyConfigList().length > n) {
            ProxyConfig proxy = LinphoneManager.getLc().getProxyConfigList()[n];
            natPolicy = proxy.getNatPolicy();
            if (natPolicy == null) {
                natPolicy = LinphoneManager.getLc().createNatPolicy();
                proxy.edit();
                proxy.setNatPolicy(natPolicy);
                proxy.done();
            }
        }

        accountCreator = LinphoneManager.getLc().createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        accountCreator.setListener(this);

        final PreferenceCategory account = (PreferenceCategory) getPreferenceScreen().findPreference(getString(R.string.pref_sipaccount_key));
        EditTextPreference username = (EditTextPreference) account.getPreference(0);
        username.setOnPreferenceChangeListener(usernameChangedListener);
        if (!isNewAccount) {
            username.setText(mPrefs.getAccountUsername(n));
            username.setSummary(username.getText());
        }

        EditTextPreference userid = (EditTextPreference) account.getPreference(1);
        userid.setOnPreferenceChangeListener(useridChangedListener);
        if (!isNewAccount) {
            userid.setText(mPrefs.getAccountUserId(n));
            userid.setSummary(userid.getText());
        }

        EditTextPreference password = (EditTextPreference) account.getPreference(2);
        password.setOnPreferenceChangeListener(passwordChangedListener);
        if (!isNewAccount) {
            password.setText(mPrefs.getAccountPassword(n));
        }

        EditTextPreference domain = (EditTextPreference) account.getPreference(3);
        domain.setOnPreferenceChangeListener(domainChangedListener);
        if (!isNewAccount) {
            domain.setText(mPrefs.getAccountDomain(n));
            domain.setSummary(domain.getText());
        }

        EditTextPreference displayName = (EditTextPreference) account.getPreference(4);
        displayName.setOnPreferenceChangeListener(displayNameChangedListener);
        if (!isNewAccount) {
            displayName.setText(mPrefs.getAccountDisplayName(n));
            displayName.setSummary(displayName.getText());
        }

        PreferenceCategory advanced = (PreferenceCategory) getPreferenceScreen().findPreference(getString(R.string.pref_advanced_key));
        mTransportPreference = (ListPreference) advanced.getPreference(0);
        initializeTransportPreference(mTransportPreference);
        mTransportPreference.setOnPreferenceChangeListener(transportChangedListener);
        if (!isNewAccount) {
            mTransportPreference.setSummary(mPrefs.getAccountTransportString(n));
        }

        CheckBoxPreference ice = (CheckBoxPreference) advanced.getPreference(1);
        ice.setOnPreferenceChangeListener(iceChangedListener);
        if (natPolicy != null)
            ice.setChecked(natPolicy.iceEnabled());

        EditTextPreference stunTurn = (EditTextPreference) advanced.getPreference(2);
        stunTurn.setOnPreferenceChangeListener(stunTurnChangedListener);
        if (natPolicy != null) {
            stunTurn.setText(natPolicy.getStunServer());
            stunTurn.setSummary(natPolicy.getStunServer());
        }

        mProxyPreference = (EditTextPreference) advanced.getPreference(3);
        mProxyPreference.setOnPreferenceChangeListener(proxyChangedListener);
        if (!isNewAccount) {
            mProxyPreference.setText(mPrefs.getAccountProxy(n));
            mProxyPreference.setSummary("".equals(mProxyPreference.getText()) || (mProxyPreference.getText() == null) ? getString(R.string.pref_help_proxy) : mProxyPreference.getText());
        }

        CheckBoxPreference outboundProxy = (CheckBoxPreference) advanced.getPreference(4);
        outboundProxy.setOnPreferenceChangeListener(outboundProxyChangedListener);
        if (!isNewAccount) {
            outboundProxy.setChecked(mPrefs.isAccountOutboundProxySet(n));
        }

        EditTextPreference expires = (EditTextPreference) advanced.getPreference(5);
        expires.setOnPreferenceChangeListener(expiresChangedListener);
        if (!isNewAccount) {
            expires.setText(mPrefs.getExpires(n));
            expires.setSummary(mPrefs.getExpires(n));
        }

        EditTextPreference prefix = (EditTextPreference) advanced.getPreference(6);
        prefix.setOnPreferenceChangeListener(prefixChangedListener);
        if (!isNewAccount) {
            String prefixValue = mPrefs.getPrefix(n);
            prefix.setText(prefixValue);
            prefix.setSummary(prefixValue);
        }

        CheckBoxPreference avpf = (CheckBoxPreference) advanced.getPreference(7);
        avpf.setOnPreferenceChangeListener(avpfChangedListener);
        if (!isNewAccount) {
            avpf.setChecked(mPrefs.avpfEnabled(n));
        }

        EditTextPreference avpfRRInterval = (EditTextPreference) advanced.getPreference(8);
        avpfRRInterval.setOnPreferenceChangeListener(avpfRRIntervalChangedListener);
        if (!isNewAccount) {
            avpfRRInterval.setText(mPrefs.getAvpfRrInterval(n));
            avpfRRInterval.setSummary(mPrefs.getAvpfRrInterval(n));
        }

        CheckBoxPreference escape = (CheckBoxPreference) advanced.getPreference(9);
        escape.setOnPreferenceChangeListener(escapeChangedListener);
        if (!isNewAccount) {
            escape.setChecked(mPrefs.getReplacePlusByZeroZero(n));
        }

        Preference linkAccount = advanced.getPreference(10);
        linkAccount.setOnPreferenceClickListener(linkAccountListener);

        CheckBoxPreference pushNotif = (CheckBoxPreference) advanced.getPreference(11);
        pushNotif.setOnPreferenceChangeListener(pushNotificationListener);
        if (!isNewAccount) {
            pushNotif.setChecked(mPrefs.isPushNotifEnabledForProxy(n));
        }

        PreferenceCategory manage = (PreferenceCategory) getPreferenceScreen().findPreference(getString(R.string.pref_manage_key));
        final CheckBoxPreference disable = (CheckBoxPreference) manage.getPreference(0);
        disable.setEnabled(true);
        disable.setOnPreferenceChangeListener(disableChangedListener);
        if (!isNewAccount) {
            disable.setChecked(!mPrefs.isAccountEnabled(n));
        }

        CheckBoxPreference mainAccount = (CheckBoxPreference) manage.getPreference(1);
        mainAccount.setChecked(isDefaultAccount);
        mainAccount.setEnabled(!mainAccount.isChecked());
        mainAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                mPrefs.setDefaultAccount(n);
                disable.setEnabled(false);
                disable.setChecked(false);
                preference.setEnabled(false);
                return true;
            }
        });
        if (!isNewAccount) {
            mainAccount.setEnabled(!mainAccount.isChecked());
        }

        final Preference changePassword = manage.getPreference(2);
        changePassword.setEnabled(false);

        final Preference delete = manage.getPreference(3);
        delete.setEnabled(!isNewAccount);
        delete.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                mPrefs.deleteAccount(n);
                LinphoneActivity.instance().displaySettings();
                LinphoneActivity.instance().refreshAccounts();
                return true;
            }
        });
    }

    private void initializeTransportPreference(ListPreference pref) {
        List<CharSequence> entries = new ArrayList<CharSequence>();
        List<CharSequence> values = new ArrayList<CharSequence>();
        entries.add(getString(R.string.pref_transport_udp));
        values.add(getString(R.string.pref_transport_udp_key));
        entries.add(getString(R.string.pref_transport_tcp));
        values.add(getString(R.string.pref_transport_tcp_key));

        if (!getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
            entries.add(getString(R.string.pref_transport_tls));
            values.add(getString(R.string.pref_transport_tls_key));
        }
        setListPreferenceValues(pref, entries, values);

        if (!isNewAccount) {
            pref.setSummary(mPrefs.getAccountTransportString(n));
            pref.setDefaultValue(mPrefs.getAccountTransportKey(n));
            pref.setValueIndex(entries.indexOf(mPrefs.getAccountTransportString(n)));
        } else {

            pref.setSummary(getString(R.string.pref_transport_udp));
            pref.setDefaultValue(getString(R.string.pref_transport_udp));
            pref.setValueIndex(entries.indexOf(getString(R.string.pref_transport_udp)));
        }
    }

    private static void setListPreferenceValues(ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
        CharSequence[] contents = new CharSequence[entries.size()];
        entries.toArray(contents);
        pref.setEntries(contents);
        contents = new CharSequence[values.size()];
        values.toArray(contents);
        pref.setEntryValues(contents);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (LinphoneActivity.isInstanciated()) {
            try {
                if (isNewAccount) {
                    builder.saveNewAccount();
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
    public void onUpdateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (progress != null) progress.dismiss();
        if (status.equals(AccountCreator.Status.RequestOk)) {
            mPrefs.setAccountPassword(n, accountCreator.getPassword());
            PreferenceCategory account = (PreferenceCategory) getPreferenceScreen().findPreference(getString(R.string.pref_sipaccount_key));
            ((EditTextPreference) account.getPreference(2)).setText(mPrefs.getAccountPassword(n));
            LinphoneUtils.displayErrorAlert(getString(R.string.pref_password_changed), LinphoneActivity.instance());
        } else {
            LinphoneUtils.displayErrorAlert(LinphoneUtils.errorForStatus(status), LinphoneActivity.instance());
        }
    }

    @Override
    public void onIsAccountExist(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
    }

    @Override
    public void onCreateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onActivateAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onLinkAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onActivateAlias(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onIsAccountActivated(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onRecoverAccount(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onIsAccountLinked(AccountCreator accountCreator, AccountCreator.Status status, String resp) {

    }

    @Override
    public void onIsAliasUsed(AccountCreator accountCreator, AccountCreator.Status status, String resp) {
    }
}
