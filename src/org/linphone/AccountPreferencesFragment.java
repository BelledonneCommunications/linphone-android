package org.linphone;
/*
AccountPreferencesFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import org.linphone.compatibility.Compatibility;
import org.linphone.ui.PreferencesListFragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputType;

/**
 * @author Sylvain Berfini
 */
public class AccountPreferencesFragment extends PreferencesListFragment {
	private int n;
	private String key;
	
	public AccountPreferencesFragment() {
		super(R.xml.account_preferences);
	}
	
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		PreferenceScreen screen = getPreferenceScreen();
		n = getArguments().getInt("Account", 0);
		key = getAccountNumber(n);
		manageAccountPreferencesFields(screen);
	}
	
	OnPreferenceChangeListener preferenceChangedListener = new OnPreferenceChangeListener() {
		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
			preference.setSummary(newValue.toString());
			return true;
		}		
	};
	
	private void manageAccountPreferencesFields(PreferenceScreen parent) {
    	final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		
    	PreferenceCategory account = (PreferenceCategory) getPreferenceScreen().getPreference(0);
    	EditTextPreference username = (EditTextPreference) account.getPreference(0);
    	username.setText(prefs.getString(getString(R.string.pref_username_key) + key, ""));
    	username.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	username.setKey(getString(R.string.pref_username_key) + key);
    	username.setOnPreferenceChangeListener(preferenceChangedListener);
    	username.setSummary(username.getText());
    	
    	EditTextPreference password = (EditTextPreference) account.getPreference(1);
    	password.setText(prefs.getString(getString(R.string.pref_passwd_key) + key, ""));
    	password.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    	password.setKey(getString(R.string.pref_passwd_key) + key);
    	
    	EditTextPreference domain = (EditTextPreference) account.getPreference(2);
    	domain.setText(prefs.getString(getString(R.string.pref_domain_key) + key, ""));
    	domain.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	domain.setKey(getString(R.string.pref_domain_key) + key);
    	domain.setOnPreferenceChangeListener(preferenceChangedListener);
    	domain.setSummary(domain.getText());

    	PreferenceCategory advanced = (PreferenceCategory) getPreferenceScreen().getPreference(1);
    	EditTextPreference proxy = (EditTextPreference) advanced.getPreference(0);
    	proxy.setText(prefs.getString(getString(R.string.pref_proxy_key) + key, ""));
    	proxy.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	proxy.setKey(getString(R.string.pref_proxy_key) + key);
    	proxy.setOnPreferenceChangeListener(preferenceChangedListener);
    	proxy.setSummary("".equals(proxy.getText()) || (proxy.getText() == null) ? getString(R.string.pref_help_proxy) : proxy.getText());
    	
    	Preference outboundProxy = advanced.getPreference(1);
    	Compatibility.setPreferenceChecked(outboundProxy, prefs.getBoolean(getString(R.string.pref_enable_outbound_proxy_key) + key, false));
    	outboundProxy.setKey(getString(R.string.pref_enable_outbound_proxy_key) + key);
   
    	final Preference disable = advanced.getPreference(2);
    	disable.setEnabled(prefs.getInt(getString(R.string.pref_default_account_key), 0) != n);
    	Compatibility.setPreferenceChecked(disable, prefs.getBoolean(getString(R.string.pref_disable_account_key) + key, false));
    	disable.setKey(getString(R.string.pref_disable_account_key) + key);

    	final Preference delete = advanced.getPreference(4);
    	delete.setEnabled(prefs.getInt(getString(R.string.pref_default_account_key), 0) != n);
    	delete.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	        public boolean onPreferenceClick(Preference preference) {
	        	int nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 1);
        		SharedPreferences.Editor editor = prefs.edit();
        		
	        	for (int i = n; i < nbAccounts - 1; i++) {
	        		editor.putString(getString(R.string.pref_username_key) + getAccountNumber(i), prefs.getString(getString(R.string.pref_username_key) + getAccountNumber(i+1), null));
	        		editor.putString(getString(R.string.pref_passwd_key) + getAccountNumber(i), prefs.getString(getString(R.string.pref_passwd_key) + getAccountNumber(i+1), null));
	        		editor.putString(getString(R.string.pref_domain_key) + getAccountNumber(i), prefs.getString(getString(R.string.pref_domain_key) + getAccountNumber(i+1), null));
	        		editor.putString(getString(R.string.pref_proxy_key) + getAccountNumber(i), prefs.getString(getString(R.string.pref_proxy_key) + getAccountNumber(i+1), null));
	        		editor.putBoolean(getString(R.string.pref_enable_outbound_proxy_key) + getAccountNumber(i), prefs.getBoolean(getString(R.string.pref_enable_outbound_proxy_key) + getAccountNumber(i+1), false));
	        		editor.putBoolean(getString(R.string.pref_disable_account_key) + getAccountNumber(i), prefs.getBoolean(getString(R.string.pref_disable_account_key) + getAccountNumber(i+1), false));
	        	}
	        	
	        	int lastAccount = nbAccounts - 1;
	        	editor.putString(getString(R.string.pref_username_key) + getAccountNumber(lastAccount), null);
        		editor.putString(getString(R.string.pref_passwd_key) + getAccountNumber(lastAccount), null);
        		editor.putString(getString(R.string.pref_domain_key) + getAccountNumber(lastAccount), null);
        		editor.putString(getString(R.string.pref_proxy_key) + getAccountNumber(lastAccount), null);
        		editor.putBoolean(getString(R.string.pref_enable_outbound_proxy_key) + getAccountNumber(lastAccount), false);
        		editor.putBoolean(getString(R.string.pref_disable_account_key) + getAccountNumber(lastAccount), false);
        		
        		int defaultAccount = prefs.getInt(getString(R.string.pref_default_account_key), 0);
        		if (defaultAccount > n) {
        			editor.putInt(getString(R.string.pref_default_account_key), defaultAccount - 1);
        		}
        		
        		editor.putInt(getString(R.string.pref_extra_accounts), nbAccounts - 1);
	        	editor.commit();
	        	
	        	LinphoneActivity.instance().displaySettings();
	        	
	        	return true;
	        }
        });
    	
    	Preference mainAccount = advanced.getPreference(3);
    	Compatibility.setPreferenceChecked(mainAccount, prefs.getInt(getString(R.string.pref_default_account_key), 0) == n);
    	mainAccount.setEnabled(!Compatibility.isPreferenceChecked(mainAccount));
    	mainAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() 
    	{
			public boolean onPreferenceClick(Preference preference) {
				
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt(getString(R.string.pref_default_account_key), n);
				editor.commit();
				delete.setEnabled(false);
				disable.setEnabled(false);
				Compatibility.setPreferenceChecked(disable, false);
				preference.setEnabled(false);
				return true;
			}
		});
	}
	
	private String getAccountNumber(int n) {
		if (n > 0)
			return Integer.toString(n);
		else
			return "";
	}
	
	@Override
	public void onPause() {
		super.onPause();

		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		int n = prefs.getInt(getString(R.string.pref_extra_accounts), 1);
		String keyUsername = getString(R.string.pref_username_key) + (n-1 == 0 ? "" : Integer.toString(n-1));
		
		if (prefs.getString(keyUsername, "").equals("")) {
			//If not, we suppress it to not display a blank field
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(getString(R.string.pref_extra_accounts), n-1);
			editor.commit();
		}
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().applyConfigChangesIfNeeded();
		}
	}
}
