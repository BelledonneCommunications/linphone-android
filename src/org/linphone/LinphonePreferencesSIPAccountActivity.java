package org.linphone;
/*
LinphonePreferencesSIPAccountActivity.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import org.linphone.core.Log;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.InputType;

public class LinphonePreferencesSIPAccountActivity extends PreferenceActivity {
	
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.account_preferences);
		
		PreferenceScreen screen = getPreferenceScreen();
		int n = getIntent().getExtras().getInt("Account", 1);
		addExtraAccountPreferencesFields(screen, n);
	}
	
	private void addExtraAccountPreferencesFields(PreferenceScreen parent, final int n) {
    	final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
    	
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle(getString(R.string.pref_sipaccount));
		
    	EditTextPreference username = new EditTextPreference(this);
    	username.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	username.setTitle(getString(R.string.pref_username));
    	username.setPersistent(true);
    	username.setKey(getString(R.string.pref_username_key) + getAccountNumber(n));
    	
    	EditTextPreference password = new EditTextPreference(this);
    	password.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    	password.setTitle(getString(R.string.pref_passwd));
    	password.setPersistent(true);
    	password.setKey(getString(R.string.pref_passwd_key) + getAccountNumber(n));
    	
    	EditTextPreference domain = new EditTextPreference(this);
    	domain.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	domain.setTitle(getString(R.string.pref_domain));
    	domain.setPersistent(true);
    	domain.setKey(getString(R.string.pref_domain_key) + getAccountNumber(n));
    	
    	EditTextPreference proxy = new EditTextPreference(this);
    	proxy.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    	proxy.setTitle(getString(R.string.pref_proxy));
    	proxy.setPersistent(true);
    	proxy.setKey(getString(R.string.pref_proxy_key) + getAccountNumber(n));
    	
    	CheckBoxPreference outboundProxy = new CheckBoxPreference(this);
    	outboundProxy.setTitle(getString(R.string.pref_enable_outbound_proxy));
    	outboundProxy.setPersistent(true);
    	outboundProxy.setKey(getString(R.string.pref_enable_outbound_proxy_key) + getAccountNumber(n));
   
    	CheckBoxPreference disable = new CheckBoxPreference(this);
    	disable.setTitle(getString(R.string.pref_disable_account));
    	disable.setPersistent(true);
    	disable.setKey(getString(R.string.pref_disable_account_key) + getAccountNumber(n));

    	final Preference delete = new Preference(this);
    	delete.setTitle("Delete this account");
    	delete.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	        public boolean onPreferenceClick(Preference preference) {
	        	int nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
        		SharedPreferences.Editor editor = prefs.edit();
        		
	        	for (int i = n; i < nbAccounts - 1; i++) {
	        		editor.putString(getString(R.string.pref_username_key) + i, prefs.getString(getString(R.string.pref_username_key) + (i+1), null));
	        		editor.putString(getString(R.string.pref_passwd_key) + i, prefs.getString(getString(R.string.pref_passwd_key) + (i+1), null));
	        		editor.putString(getString(R.string.pref_domain_key) + i, prefs.getString(getString(R.string.pref_domain_key) + (i+1), null));
	        		editor.putString(getString(R.string.pref_proxy_key) + i, prefs.getString(getString(R.string.pref_proxy_key) + (i+1), null));
	        		editor.putBoolean(getString(R.string.pref_enable_outbound_proxy_key) + i, prefs.getBoolean(getString(R.string.pref_enable_outbound_proxy_key) + (i+1), false));
	        		editor.putBoolean(getString(R.string.pref_disable_account_key) + i, prefs.getBoolean(getString(R.string.pref_disable_account_key) + (i+1), false));
	        	}
	        	
	        	int lastAccount = nbAccounts - 1;
	        	editor.putString(getString(R.string.pref_username_key) + lastAccount, null);
        		editor.putString(getString(R.string.pref_passwd_key) + lastAccount, null);
        		editor.putString(getString(R.string.pref_domain_key) + lastAccount, null);
        		editor.putString(getString(R.string.pref_proxy_key) + lastAccount, null);
        		editor.putBoolean(getString(R.string.pref_enable_outbound_proxy_key) + lastAccount, false);
        		editor.putBoolean(getString(R.string.pref_disable_account_key) + lastAccount, false);
        		
        		int defaultAccount = prefs.getInt(getString(R.string.pref_default_account), 0);
        		if (defaultAccount > n) {
        			Log.e("Default Account : ", defaultAccount + " => " + (defaultAccount - 1));
        			editor.putInt(getString(R.string.pref_default_account), defaultAccount - 1);
        		}
        		
        		editor.putInt(getString(R.string.pref_extra_accounts), nbAccounts - 1);
	        	editor.commit();
	        	LinphonePreferencesSIPAccountActivity.this.finish();
	        	return true;
	        }
        });
    	
    	CheckBoxPreference mainAccount = new CheckBoxPreference(this);
    	mainAccount.setTitle("Use as default");
    	mainAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() 
    	{
			public boolean onPreferenceClick(Preference preference) {
				
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt(getString(R.string.pref_default_account), n);
				editor.commit();
				delete.setEnabled(false);
				preference.setEnabled(false);
				return true;
			}
		});
    	
    	mainAccount.setChecked(prefs.getInt(getString(R.string.pref_default_account), 0) == n);
    	mainAccount.setEnabled(!mainAccount.isChecked());
    	delete.setEnabled(prefs.getInt(getString(R.string.pref_default_account), 0) != n);
    	
    	parent.addPreference(category);
    	category.addPreference(username);
    	category.addPreference(password);
    	category.addPreference(domain);
    	category.addPreference(proxy);
    	category.addPreference(outboundProxy);
    	category.addPreference(disable);
    	category.addPreference(mainAccount);
    	category.addPreference(delete);
	}
	
	private String getAccountNumber(int n) {
		if (n > 0)
			return Integer.toString(n);
		else
			return "";
	}
}