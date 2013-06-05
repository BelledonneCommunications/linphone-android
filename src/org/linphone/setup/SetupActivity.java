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
import org.linphone.mediastream.Log;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.RelativeLayout;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class SetupActivity extends FragmentActivity implements OnClickListener {
	private static SetupActivity instance;
	private RelativeLayout back, next, cancel;
	private SetupFragment currentFragment;
	private SharedPreferences mPref;
	private SetupFragment firstFragment;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getResources().getBoolean(R.bool.isTablet) && getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
		
		setContentView(R.layout.setup);
		firstFragment = getResources().getBoolean(R.bool.setup_use_linphone_as_first_fragment) ?
				SetupFragment.LINPHONE_LOGIN : SetupFragment.WELCOME;
        if (findViewById(R.id.fragmentContainer) != null) {
            if (savedInstanceState == null) {
            	display(firstFragment);
            } else {
            	currentFragment = (SetupFragment) savedInstanceState.getSerializable("CurrentFragment");
            }
        }
        
        mPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        
        initUI();
        instance = this;
	};
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("CurrentFragment", currentFragment);
		super.onSaveInstanceState(outState);
	}
	
	public static SetupActivity instance() {
		return instance;
	}
	
	private void initUI() {
		back = (RelativeLayout) findViewById(R.id.setup_back);
		back.setOnClickListener(this);
		next = (RelativeLayout) findViewById(R.id.setup_next);
		next.setOnClickListener(this);
		cancel = (RelativeLayout) findViewById(R.id.setup_cancel);
		cancel.setOnClickListener(this);
	}
	
	private void changeFragment(Fragment newFragment) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		
//		transaction.addToBackStack("");
		transaction.replace(R.id.fragmentContainer, newFragment);
		
		transaction.commitAllowingStateLoss();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.setup_cancel) {
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				finish();
			}
		} else if (id == R.id.setup_next) {
			if (currentFragment == SetupFragment.WELCOME) {
				MenuFragment fragment = new MenuFragment();
				changeFragment(fragment);
				currentFragment = SetupFragment.MENU;
				
				next.setVisibility(View.GONE);
				back.setVisibility(View.VISIBLE);
			} else if (currentFragment == SetupFragment.WIZARD_CONFIRM) {
				finish();
			}
		} else if (id == R.id.setup_back) {
			onBackPressed();
		}
	}

	@Override
	public void onBackPressed() {
		if (currentFragment == firstFragment) {
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				finish();
			}
		}
		if (currentFragment == SetupFragment.MENU) {
			WelcomeFragment fragment = new WelcomeFragment();
			changeFragment(fragment);
			currentFragment = SetupFragment.WELCOME;
			
			next.setVisibility(View.VISIBLE);
			back.setVisibility(View.GONE);
		} else if (currentFragment == SetupFragment.GENERIC_LOGIN || currentFragment == SetupFragment.LINPHONE_LOGIN || currentFragment == SetupFragment.WIZARD) {
			MenuFragment fragment = new MenuFragment();
			changeFragment(fragment);
			currentFragment = SetupFragment.MENU;
		} else if (currentFragment == SetupFragment.WELCOME) {
			finish();
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		if (LinphoneManager.getLc().needsEchoCalibration() && !mPref.getBoolean(getString(R.string.first_launch_suceeded_once_key), false)) {
			EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
			fragment.enableEcCalibrationResultSending(sendEcCalibrationResult);
			changeFragment(fragment);
			currentFragment = SetupFragment.ECHO_CANCELLER_CALIBRATION;
			back.setVisibility(View.VISIBLE);
			next.setVisibility(View.GONE);
			next.setEnabled(false);
			cancel.setEnabled(false);
		} else {
			success();
		}		
	}

	private void logIn(String username, String password, String domain, boolean sendEcCalibrationResult) {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null && getCurrentFocus() != null) {
			imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
		}

        saveCreatedAccount(username, password, domain);
		LinphoneManager.getInstance().initializePayloads();

		try {
			LinphoneManager.getInstance().initFromConf();
		} catch (Throwable e) {
			Log.e(e, "Error while initializing from config in first login activity");
			Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
		}

		if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
			launchEchoCancellerCalibration(sendEcCalibrationResult);
		}
	}

	public void linphoneLogIn(String username, String password) {
		logIn(username, password, getString(R.string.default_domain), true);
	}

	public void genericLogIn(String username, String password, String domain) {
		logIn(username, password, domain, false);
	}

	private void writePreference(int key, String value) {
		mPref.edit().putString(getString(key), value).commit();
	}
	
	private void writePreference(String key, String value) {
		mPref.edit().putString(key, value).commit();
	}
	
	private void writePreference(int key, int value) {
		mPref.edit().putInt(getString(key), value).commit();
	}
	
	private void writePreference(int key, boolean value) {
		mPref.edit().putBoolean(getString(key), value).commit();
	}

	private void display(SetupFragment fragment) {
		switch (fragment) {
		case WELCOME:
			displayWelcome();
			break;
		case LINPHONE_LOGIN:
			displayLoginLinphone();
			break;
		default:
			throw new IllegalStateException("Can't handle " + fragment);
		}
	}

	public void displayWelcome() {
		changeFragment(new WelcomeFragment());
		currentFragment = SetupFragment.WELCOME;
	}

	public void displayLoginGeneric() {
		GenericLoginFragment fragment = new GenericLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragment.GENERIC_LOGIN;
	}
	
	public void displayLoginLinphone() {
		LinphoneLoginFragment fragment = new LinphoneLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragment.LINPHONE_LOGIN;
	}

	public void displayWizard() {
		WizardFragment fragment = new WizardFragment();
		changeFragment(fragment);
		currentFragment = SetupFragment.WIZARD;
	}
	
	public void saveCreatedAccount(String username, String password, String domain) {
		int newAccountId = mPref.getInt(getString(R.string.pref_extra_accounts), 0);
		if (newAccountId == -1)
			newAccountId = 0;
		writePreference(R.string.pref_extra_accounts, newAccountId+1);
		
		if (newAccountId == 0) {
			writePreference(R.string.pref_username_key, username);
			writePreference(R.string.pref_passwd_key, password);
			writePreference(R.string.pref_domain_key, domain);
			
			boolean isMainAccountLinphoneDotOrg = domain.equals(getString(R.string.default_domain));
			if (isMainAccountLinphoneDotOrg) {
				if (getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
					writePreference(R.string.pref_proxy_key, domain + ":5228");
					writePreference(R.string.pref_transport_key, getString(R.string.pref_transport_tcp_key));
				}
				else {
					writePreference(R.string.pref_proxy_key, domain + ":5223");
					writePreference(R.string.pref_transport_key, getString(R.string.pref_transport_tls_key));
				}
				
				writePreference(R.string.pref_expire_key, "604800"); // 3600*24*7
				writePreference(R.string.pref_enable_outbound_proxy_key, true);
				writePreference(R.string.pref_stun_server_key, getString(R.string.default_stun));
				writePreference(R.string.pref_ice_enable_key, true);
				writePreference(R.string.pref_push_notification_key, true);
			}
		} else {
			writePreference(getString(R.string.pref_username_key) + newAccountId, username);
			writePreference(getString(R.string.pref_passwd_key) + newAccountId, password);
			writePreference(getString(R.string.pref_domain_key) + newAccountId, domain);
		}
		String forcedProxy=getResources().getString(R.string.setup_forced_proxy);
		if (!TextUtils.isEmpty(forcedProxy)) {
			writePreference(R.string.pref_enable_outbound_proxy_key, true);
			writePreference(R.string.pref_proxy_key, forcedProxy);
		}
	}

	public void displayWizardConfirm(String username) {
		WizardConfirmFragment fragment = new WizardConfirmFragment();
		
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		fragment.setArguments(extras);
		changeFragment(fragment);
		
		currentFragment = SetupFragment.WIZARD_CONFIRM;

		next.setVisibility(View.VISIBLE);
		next.setEnabled(false);
		back.setVisibility(View.GONE);
	}
	
	public void isAccountVerified() {
		Toast.makeText(this, getString(R.string.setup_account_validated), Toast.LENGTH_LONG).show();
		
		LinphoneManager.getInstance().initializePayloads();

		try {
			LinphoneManager.getInstance().initFromConf();
		} catch (Throwable e) {
			Log.e(e, "Error while initializing from config in first login activity");
			Toast.makeText(this, getString(R.string.error), Toast.LENGTH_LONG).show();
		}

		launchEchoCancellerCalibration(true);
	}

	public void isEchoCalibrationFinished() {
		success();
	}
	
	public void success() {
		writePreference(R.string.first_launch_suceeded_once_key, true);
		setResult(Activity.RESULT_OK);
		finish();
	}
}
