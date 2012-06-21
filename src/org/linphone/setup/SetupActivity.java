package org.linphone.setup;
/*
SetupActivity.java
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
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class SetupActivity extends FragmentActivity implements OnClickListener {
	private static SetupActivity instance;
	private ImageView back, next, cancel;
	private SetupFragments currentFragment;
	private SharedPreferences mPref;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.setup);
        
        if (findViewById(R.id.fragmentContainer) != null) {
            if (savedInstanceState != null) {
                return;
            }

            WelcomeFragment welcomeFragment = new WelcomeFragment();
            getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, welcomeFragment).commit();
            
            currentFragment = SetupFragments.WELCOME;
        }
        
        mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        
        initUI();
        instance = this;
	};
	
	public static SetupActivity instance() {
		return instance;
	}
	
	private void initUI() {
		back = (ImageView) findViewById(R.id.setup_back);
		back.setOnClickListener(this);
		next = (ImageView) findViewById(R.id.setup_next);
		next.setOnClickListener(this);
		cancel = (ImageView) findViewById(R.id.setup_cancel);
		cancel.setOnClickListener(this);
	}
	
	private void changeFragment(Fragment newFragment) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		
		transaction.addToBackStack("Add to back stack");
		getSupportFragmentManager().popBackStack("Add to back stack", FragmentManager.POP_BACK_STACK_INCLUSIVE);
		transaction.replace(R.id.fragmentContainer, newFragment);
		
		transaction.commitAllowingStateLoss();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.setup_cancel) {
			finish();
		} else if (id == R.id.setup_next) {
			MenuFragment fragment = new MenuFragment();
			changeFragment(fragment);
			currentFragment = SetupFragments.MENU;
			
			next.setVisibility(View.GONE);
			back.setVisibility(View.VISIBLE);
		} else if (id == R.id.setup_back) {
			if (currentFragment == SetupFragments.MENU) {
				WelcomeFragment fragment = new WelcomeFragment();
				changeFragment(fragment);
				currentFragment = SetupFragments.WELCOME;
				
				next.setVisibility(View.VISIBLE);
				back.setVisibility(View.GONE);
			} else if (currentFragment == SetupFragments.GENERIC_LOGIN || currentFragment == SetupFragments.LINPHONE_LOGIN || currentFragment == SetupFragments.WIZARD) {
				MenuFragment fragment = new MenuFragment();
				changeFragment(fragment);
				currentFragment = SetupFragments.MENU;
			}
		}
	}
	
	public void logIn(String username, String password, String domain) {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);

		writePreference(R.string.pref_username_key, username);
		writePreference(R.string.pref_passwd_key, password);
		writePreference(R.string.pref_domain_key, domain);
		writePreference(R.string.pref_extra_accounts, 1);

		LinphoneManager.getInstance().initializePayloads();

		try {
			LinphoneManager.getInstance().initFromConf();
		} catch (Throwable e) {
			Log.e(e, "Error while initializing from config in first login activity");
			Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
		}
		
		if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
			writePreference(R.string.first_launch_suceeded_once_key, true);
			finish();
		}
	}
	
	private void writePreference(int key, String value) {
		mPref.edit().putString(getString(key), value).commit();
	}
	
	private void writePreference(int key, int value) {
		mPref.edit().putInt(getString(key), value).commit();
	}
	
	private void writePreference(int key, boolean value) {
		mPref.edit().putBoolean(getString(key), value).commit();
	}

	public void displayLoginGeneric() {
		GenericLoginFragment fragment = new GenericLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragments.GENERIC_LOGIN;
	}
	
	public void displayLoginLinphone() {
		LinphoneLoginFragment fragment = new LinphoneLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragments.LINPHONE_LOGIN;
	}

	public void displayWizard() {
		WizardFragment fragment = new WizardFragment();
		changeFragment(fragment);
		currentFragment = SetupFragments.WIZARD;
	}
}
