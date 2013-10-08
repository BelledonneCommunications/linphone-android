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
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneSimpleListener.LinphoneOnRegistrationStateChangedListener;
import org.linphone.R;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
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
	private SetupFragmentsEnum currentFragment;
	private SharedPreferences mPref;
	private SetupFragmentsEnum firstFragment;
	private Fragment fragment;
	private boolean accountCreated = false;
	private Handler mHandler = new Handler();
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getResources().getBoolean(R.bool.isTablet) && getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
		
		setContentView(R.layout.setup);
		firstFragment = getResources().getBoolean(R.bool.setup_use_linphone_as_first_fragment) ?
				SetupFragmentsEnum.LINPHONE_LOGIN : SetupFragmentsEnum.WELCOME;
        if (findViewById(R.id.fragmentContainer) != null) {
            if (savedInstanceState == null) {
            	display(firstFragment);
            } else {
            	currentFragment = (SetupFragmentsEnum) savedInstanceState.getSerializable("CurrentFragment");
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
			LinphonePreferences.instance().firstLaunchSuccessful();
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		} else if (id == R.id.setup_next) {
			if (firstFragment == SetupFragmentsEnum.LINPHONE_LOGIN) {
				LinphoneLoginFragment linphoneFragment = (LinphoneLoginFragment) fragment;
				linphoneFragment.linphoneLogIn();
			} else {
				if (currentFragment == SetupFragmentsEnum.WELCOME) {
					MenuFragment fragment = new MenuFragment();
					changeFragment(fragment);
					currentFragment = SetupFragmentsEnum.MENU;
					
					next.setVisibility(View.GONE);
					back.setVisibility(View.VISIBLE);
				} else if (currentFragment == SetupFragmentsEnum.WIZARD_CONFIRM) {
					finish();
				}
			}
		} else if (id == R.id.setup_back) {
			onBackPressed();
		}
	}

	@Override
	public void onBackPressed() {
		if (currentFragment == firstFragment) {
			LinphonePreferences.instance().firstLaunchSuccessful();
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		}
		if (currentFragment == SetupFragmentsEnum.MENU) {
			WelcomeFragment fragment = new WelcomeFragment();
			changeFragment(fragment);
			currentFragment = SetupFragmentsEnum.WELCOME;
			
			next.setVisibility(View.VISIBLE);
			back.setVisibility(View.GONE);
		} else if (currentFragment == SetupFragmentsEnum.GENERIC_LOGIN || currentFragment == SetupFragmentsEnum.LINPHONE_LOGIN || currentFragment == SetupFragmentsEnum.WIZARD) {
			MenuFragment fragment = new MenuFragment();
			changeFragment(fragment);
			currentFragment = SetupFragmentsEnum.MENU;
		} else if (currentFragment == SetupFragmentsEnum.WELCOME) {
			finish();
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		if (LinphoneManager.getLc().needsEchoCalibration() && !mPref.getBoolean(getString(R.string.first_launch_suceeded_once_key), false)) {
			EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
			fragment.enableEcCalibrationResultSending(sendEcCalibrationResult);
			changeFragment(fragment);
			currentFragment = SetupFragmentsEnum.ECHO_CANCELLER_CALIBRATION;
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

		if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
			launchEchoCancellerCalibration(sendEcCalibrationResult);
		}
	}
	
	
	private LinphoneOnRegistrationStateChangedListener registrationListener = new LinphoneOnRegistrationStateChangedListener() {
		public void onRegistrationStateChanged(RegistrationState state) {
			if (state == RegistrationState.RegistrationOk) {
				LinphoneManager.removeListener(registrationListener);
				
				if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
					mHandler .post(new Runnable () {
						public void run() {
							launchEchoCancellerCalibration(true);
						}
					});
				}
			} else if (state == RegistrationState.RegistrationFailed) {
				LinphoneManager.removeListener(registrationListener);
				deleteCreatedAccount();
				mHandler.post(new Runnable () {
					public void run() {
						Toast.makeText(SetupActivity.this, getString(R.string.first_launch_bad_login_password), Toast.LENGTH_LONG).show();
					}
				});
			}
		}
	};
	public void checkAccount(String username, String password, String domain) {
		LinphoneManager.removeListener(registrationListener);
		LinphoneManager.addListener(registrationListener);
		
		saveCreatedAccount(username, password, domain);
	}

	public void linphoneLogIn(String username, String password, boolean validate) {
		if (validate) {
			checkAccount(username, password, getString(R.string.default_domain));
		} else {
			logIn(username, password, getString(R.string.default_domain), true);
		}
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

	private void display(SetupFragmentsEnum fragment) {
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
		fragment = new WelcomeFragment();
		changeFragment(fragment);
		currentFragment = SetupFragmentsEnum.WELCOME;
	}

	public void displayLoginGeneric() {
		fragment = new GenericLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragmentsEnum.GENERIC_LOGIN;
	}
	
	public void displayLoginLinphone() {
		fragment = new LinphoneLoginFragment();
		changeFragment(fragment);
		currentFragment = SetupFragmentsEnum.LINPHONE_LOGIN;
	}

	public void displayWizard() {
		fragment = new WizardFragment();
		changeFragment(fragment);
		currentFragment = SetupFragmentsEnum.WIZARD;
	}
	
	public void deleteCreatedAccount() {
		if (!accountCreated)
			return;
		
		writePreference(R.string.pref_extra_accounts, 0);
		accountCreated = false;
	}
	
	public void saveCreatedAccount(String username, String password, String domain) {
		if (accountCreated)
			return;
		
		int newAccountId = mPref.getInt(getString(R.string.pref_extra_accounts), 0);
		if (newAccountId == -1)
			newAccountId = 0;
		writePreference(R.string.pref_extra_accounts, newAccountId+1);
		
		if (newAccountId == 0) {
			writePreference(R.string.pref_username_key, username);
			writePreference(R.string.pref_passwd_key, password);
			writePreference(R.string.pref_domain_key, domain);
			
			boolean isMainAccountLinphoneDotOrg = domain.equals(getString(R.string.default_domain));
			boolean useLinphoneDotOrgCustomPorts = getResources().getBoolean(R.bool.use_linphone_server_ports);
			if (isMainAccountLinphoneDotOrg && useLinphoneDotOrgCustomPorts) {
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
		accountCreated = true;
	}

	public void displayWizardConfirm(String username) {
		WizardConfirmFragment fragment = new WizardConfirmFragment();
		
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		fragment.setArguments(extras);
		changeFragment(fragment);
		
		currentFragment = SetupFragmentsEnum.WIZARD_CONFIRM;

		next.setVisibility(View.VISIBLE);
		next.setEnabled(false);
		back.setVisibility(View.GONE);
	}
	
	public void isAccountVerified() {
		Toast.makeText(this, getString(R.string.setup_account_validated), Toast.LENGTH_LONG).show();
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
