package org.linphone.assistant;
/*
AssistantActivity.java
Copyright (C) 2015  Belledonne Communications, Grenoble, France

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
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphonePreferences.AccountBuilder;
import org.linphone.R;
import org.linphone.StatusFragment;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.TextUtils;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class AssistantActivity extends Activity implements OnClickListener {
	private static AssistantActivity instance;
	private ImageView back, cancel;
	private AssistantFragmentsEnum currentFragment;
	private AssistantFragmentsEnum firstFragment;
	private Fragment fragment;
	private LinphonePreferences mPrefs;
	private boolean accountCreated = false, newAccount = false;
	private LinphoneCoreListenerBase mListener;
	private LinphoneAddress address;
	private StatusFragment status;
	private Dialog dialog;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		setContentView(R.layout.assistant);
		initUI();

		firstFragment = getResources().getBoolean(R.bool.assistant_use_linphone_login_as_first_fragment) ? AssistantFragmentsEnum.LINPHONE_LOGIN : AssistantFragmentsEnum.WELCOME;
        if (findViewById(R.id.fragment_container) != null) {
            if (savedInstanceState == null) {
            	display(firstFragment);
            } else {
            	currentFragment = (AssistantFragmentsEnum) savedInstanceState.getSerializable("CurrentFragment");
            }
        }
        mPrefs = LinphonePreferences.instance();
		if(mPrefs.isFirstLaunch()) {
			status.enableSideMenu(false);
		}
        
        mListener = new LinphoneCoreListenerBase(){
        	@Override
        	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
				if(accountCreated && !newAccount){
					if(address != null && address.asString().equals(cfg.getIdentity()) ) {
						if (state == RegistrationState.RegistrationOk) {
							if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
								launchEchoCancellerCalibration(true);
							}
						} else if (state == RegistrationState.RegistrationFailed) {
							if(dialog == null || !dialog.isShowing()) {
								dialog = createErrorDialog(cfg, smessage);
								dialog.show();
							}
						}
					}
				}
        	}
        };
        instance = this;
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
	}
	
	@Override
	protected void onPause() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		super.onPause();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putSerializable("CurrentFragment", currentFragment);
		super.onSaveInstanceState(outState);
	}
	
	public static AssistantActivity instance() {
		return instance;
	}

	public void updateStatusFragment(StatusFragment fragment) {
		status = fragment;
	}

	private void initUI() {
		back = (ImageView) findViewById(R.id.back);
		back.setOnClickListener(this);
		cancel = (ImageView) findViewById(R.id.cancel);
		cancel.setOnClickListener(this);
	}
	
	private void changeFragment(Fragment newFragment) {
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragment_container, newFragment);
		transaction.commitAllowingStateLoss();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.cancel) {
			LinphonePreferences.instance().firstLaunchSuccessful();
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		} else if (id == R.id.back) {
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
		} else if (currentFragment == AssistantFragmentsEnum.LOGIN
				|| currentFragment == AssistantFragmentsEnum.LINPHONE_LOGIN
				|| currentFragment == AssistantFragmentsEnum.CREATE_ACCOUNT
				|| currentFragment == AssistantFragmentsEnum.REMOTE_PROVISIONING) {
			WelcomeFragment fragment = new WelcomeFragment();
			changeFragment(fragment);
			currentFragment = AssistantFragmentsEnum.WELCOME;
			back.setVisibility(View.INVISIBLE);
		} else if (currentFragment == AssistantFragmentsEnum.WELCOME) {
			finish();
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		boolean needsEchoCalibration = LinphoneManager.getLc().needsEchoCalibration();
		if (needsEchoCalibration && mPrefs.isFirstLaunch()) {
			EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
			fragment.enableEcCalibrationResultSending(sendEcCalibrationResult);
			changeFragment(fragment);
			currentFragment = AssistantFragmentsEnum.ECHO_CANCELLER_CALIBRATION;
			back.setVisibility(View.VISIBLE);
			cancel.setEnabled(false);
		} else {
			success();
		}		
	}

	private void logIn(String username, String password, String displayName, String domain, TransportType transport, boolean sendEcCalibrationResult) {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null && getCurrentFocus() != null) {
			imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
		}

        saveCreatedAccount(username, password, displayName, domain, transport);
	}
	
	public void checkAccount(String username, String password, String displayName, String domain) {
		saveCreatedAccount(username, password, displayName, domain, null);
	}

	public void linphoneLogIn(String username, String password, String displayName, boolean validate) {
		if (validate) {
			checkAccount(username, password, displayName, getString(R.string.default_domain));
		} else {
			if(accountCreated) {
				retryLogin(username, password, displayName, getString(R.string.default_domain), null);
			} else {
				logIn(username, password, displayName, getString(R.string.default_domain), null, true);
			}
		}
	}

	public void genericLogIn(String username, String password, String displayName, String domain, TransportType transport) {
		if(accountCreated) {
			retryLogin(username, password, displayName, domain, transport);
		} else {
			logIn(username, password, displayName, domain, transport, false);
		}
	}

	private void display(AssistantFragmentsEnum fragment) {
		switch (fragment) {
			case WELCOME:
				displayMenu();
				break;
			case LINPHONE_LOGIN:
				displayLoginLinphone();
				break;
		default:
			throw new IllegalStateException("Can't handle " + fragment);
		}
	}

	public void displayMenu() {
		fragment = new WelcomeFragment();
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.WELCOME;
		back.setVisibility(View.INVISIBLE);
	}

	public void displayLoginGeneric() {
		fragment = new LoginFragment();
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.LOGIN;
		back.setVisibility(View.VISIBLE);
	}
	
	public void displayLoginLinphone() {
		fragment = new LinphoneLoginFragment();
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.LINPHONE_LOGIN;
		back.setVisibility(View.VISIBLE);
	}

	public void displayCreateAccount() {
		fragment = new CreateAccountFragment();
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT;
		back.setVisibility(View.VISIBLE);
	}

	public void displayRemoteProvisioning() {
		fragment = new RemoteProvisioningFragment();
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.REMOTE_PROVISIONING;
		back.setVisibility(View.VISIBLE);
	}

	public void retryLogin(String username, String password, String displayName, String domain, TransportType transport) {
		accountCreated = false;
		saveCreatedAccount(username, password, displayName, domain, transport);
	}

	public void loadLinphoneConfig(){
		//LinphoneManager.getInstance().loadConfig();
		//LinphoneManager.getInstance().restartLinphoneCore();
	}

	public void saveCreatedAccount(String username, String password, String displayName, String domain, TransportType transport) {
		if (accountCreated)
			return;

		if(username.startsWith("sip:")) {
			username = username.substring(4);
		}

		if (username.contains("@"))
			username = username.split("@")[0];

		if(domain.startsWith("sip:")) {
			domain = domain.substring(4);
		}

		String identity = "sip:" + username + "@" + domain;
		try {
			address = LinphoneCoreFactory.instance().createLinphoneAddress(identity);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}

		if(displayName != null && !displayName.equals("")){
			address.setDisplayName(displayName);
		}

		boolean isMainAccountLinphoneDotOrg = domain.equals(getString(R.string.default_domain));
		AccountBuilder builder = new AccountBuilder(LinphoneManager.getLc())
		.setUsername(username)
		.setDomain(domain)
		.setDisplayName(displayName)
		.setPassword(password);
		
		if (isMainAccountLinphoneDotOrg) {
			if (getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
				builder.setProxy(domain)
				.setTransport(TransportType.LinphoneTransportTcp);
			}
			else {
				builder.setProxy(domain)
				.setTransport(TransportType.LinphoneTransportTls);
			}

			builder.setExpires("604800")
			.setAvpfEnabled(true)
			.setAvpfRRInterval(3)
			.setQualityReportingCollector("sip:voip-metrics@sip.linphone.org")
			.setQualityReportingEnabled(true)
			.setQualityReportingInterval(180)
			.setRealm("sip.linphone.org")
			.setNoDefault(false);
			
			
			mPrefs.setStunServer(getString(R.string.default_stun));
			mPrefs.setIceEnabled(true);
		} else {
			String forcedProxy = "";
			if (!TextUtils.isEmpty(forcedProxy)) {
				builder.setProxy(forcedProxy)
				.setOutboundProxyEnabled(true)
				.setAvpfRRInterval(5);
			}

			if(transport != null){
				builder.setTransport(transport);
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
		CreateAccountActivationFragment fragment = new CreateAccountActivationFragment();

		newAccount = true;
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		fragment.setArguments(extras);
		changeFragment(fragment);
		
		currentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_ACTIVATION;
		back.setVisibility(View.INVISIBLE);
	}
	
	public void isAccountVerified(String username) {
		Toast.makeText(this, getString(R.string.assistant_account_validated), Toast.LENGTH_LONG).show();
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().refreshRegisters();
		launchEchoCancellerCalibration(true);
	}

	public void isEchoCalibrationFinished() {
		success();
	}

	public Dialog createErrorDialog(LinphoneProxyConfig proxy, String message){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if(message.equals("Forbidden")) {
			message = getString(R.string.assistant_error_bad_credentials);
		}
		builder.setMessage(message)
				.setTitle(proxy.getState().toString())
				.setPositiveButton(getString(R.string.continue_text), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						success();
					}
				})
				.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						LinphoneManager.getLc().removeProxyConfig(LinphoneManager.getLc().getDefaultProxyConfig());
						LinphonePreferences.instance().resetDefaultProxyConfig();
						LinphoneManager.getLc().refreshRegisters();
						dialog.dismiss();
					}
				});
		return builder.show();
	}
	
	public void success() {
		mPrefs.firstLaunchSuccessful();
		LinphoneActivity.instance().isNewProxyConfig();
		setResult(Activity.RESULT_OK);
		finish();
	}
}
