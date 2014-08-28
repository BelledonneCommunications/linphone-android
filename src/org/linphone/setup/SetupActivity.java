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
import org.linphone.LinphonePreferences.AccountBuilder;
import org.linphone.LinphoneSimpleListener.LinphoneOnRegistrationStateChangedListener;
import org.linphone.R;
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneProxyConfig;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
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
	private SetupFragmentsEnum firstFragment;
	private Fragment fragment;
	private LinphonePreferences mPrefs;
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
        mPrefs = LinphonePreferences.instance();
        
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
		} else if (currentFragment == SetupFragmentsEnum.GENERIC_LOGIN 
				|| currentFragment == SetupFragmentsEnum.LINPHONE_LOGIN 
				|| currentFragment == SetupFragmentsEnum.WIZARD 
				|| currentFragment == SetupFragmentsEnum.REMOTE_PROVISIONING) {
			MenuFragment fragment = new MenuFragment();
			changeFragment(fragment);
			currentFragment = SetupFragmentsEnum.MENU;
		} else if (currentFragment == SetupFragmentsEnum.WELCOME) {
			finish();
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		boolean needsEchoCalibration = LinphoneManager.getLc().needsEchoCalibration();
		if (needsEchoCalibration && mPrefs.isFirstLaunch()) {
			EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
			fragment.enableEcCalibrationResultSending(sendEcCalibrationResult);
			changeFragment(fragment);
			currentFragment = SetupFragmentsEnum.ECHO_CANCELLER_CALIBRATION;
			back.setVisibility(View.VISIBLE);
			next.setVisibility(View.GONE);
			next.setEnabled(false);
			cancel.setEnabled(false);
		} else {
			if (mPrefs.isFirstLaunch()) {
				mPrefs.setEchoCancellation(false);
			}
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
		public void onRegistrationStateChanged(LinphoneProxyConfig proxy, RegistrationState state, String message) {
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

	public void displayRemoteProvisioning() {
		fragment = new RemoteProvisioningFragment();
		changeFragment(fragment);
		currentFragment = SetupFragmentsEnum.REMOTE_PROVISIONING;
	}
	
	public void saveCreatedAccount(String username, String password, String domain) {
		if (accountCreated)
			return;
		
		boolean isMainAccountLinphoneDotOrg = domain.equals(getString(R.string.default_domain));
		boolean useLinphoneDotOrgCustomPorts = getResources().getBoolean(R.bool.use_linphone_server_ports);
		AccountBuilder builder = new AccountBuilder(LinphoneManager.getLc())
		.setUsername(username)
		.setDomain(domain)
		.setPassword(password);
		
		if (isMainAccountLinphoneDotOrg && useLinphoneDotOrgCustomPorts) {
			if (getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
				builder.setProxy(domain + ":5228")
				.setTransport(TransportType.LinphoneTransportTcp);
			}
			else {
				builder.setProxy(domain + ":5223")
				.setTransport(TransportType.LinphoneTransportTls);
			}
			
			builder.setExpires("604800")
			.setOutboundProxyEnabled(true)
			.setAvpfEnabled(true)
			.setAvpfRRInterval(3)
			.setQualityReportingCollector("sip:voip-metrics@sip.linphone.org")
			.setQualityReportingEnabled(true)
			.setQualityReportingInterval(180)
			.setRealm("sip.linphone.org");
			
			
			mPrefs.setStunServer(getString(R.string.default_stun));
			mPrefs.setIceEnabled(true);
			mPrefs.setPushNotificationEnabled(true);
		} else {
			String forcedProxy = getResources().getString(R.string.setup_forced_proxy);
			if (!TextUtils.isEmpty(forcedProxy)) {
				builder.setProxy(forcedProxy)
				.setOutboundProxyEnabled(true)
				.setAvpfRRInterval(5);
			}
		}
		
		if (getResources().getBoolean(R.bool.enable_push_id)) {
			String regId = mPrefs.getPushNotificationRegistrationID();
			String appId = getString(R.string.push_sender_id);
			if (regId != null && mPrefs.isPushNotificationEnabled()) {
				String contactInfos = "app-id=" + appId + ";pn-type=google;pn-tok=" + regId;
				builder.setContactParameters(contactInfos);
			}
		}
		
		try {
			builder.saveNewAccount();
			accountCreated = true;
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
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
		mPrefs.firstLaunchSuccessful();
		setResult(Activity.RESULT_OK);
		finish();
	}
}
