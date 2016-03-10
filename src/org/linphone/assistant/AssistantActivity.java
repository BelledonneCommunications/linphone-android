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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;
/**
 * @author Sylvain Berfini
 */
public class AssistantActivity extends Activity implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
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
	private ProgressDialog progress;
	private Dialog dialog;
	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 201;
	
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
		//if(mPrefs.isFirstLaunch()) {
			status.enableSideMenu(false);
		//}
        
        mListener = new LinphoneCoreListenerBase(){
        	@Override
        	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
				if(accountCreated && !newAccount){
					if(address != null && address.asString().equals(cfg.getAddress().asString()) ) {
						if (state == RegistrationState.RegistrationOk) {
							if(progress != null)
								progress.dismiss();
							if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
								launchEchoCancellerCalibration(true);
							}
						} else if (state == RegistrationState.RegistrationFailed) {
							if(progress != null)
								progress.dismiss();
							if(dialog == null || !dialog.isShowing()) {
								dialog = createErrorDialog(cfg, smessage);
								dialog.show();
							}
						} else if(!(state == RegistrationState.RegistrationProgress)) {
							if(progress != null)
								progress.dismiss();
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
		cancel = (ImageView) findViewById(R.id.assistant_cancel);
		cancel.setOnClickListener(this);
	}
	
	private void changeFragment(Fragment newFragment) {
		hideKeyboard();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.fragment_container, newFragment);
		transaction.commitAllowingStateLoss();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();
		
		if (id == R.id.assistant_cancel) {
			hideKeyboard();
			LinphonePreferences.instance().firstLaunchSuccessful();
			if (getResources().getBoolean(R.bool.setup_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		} else if (id == R.id.back) {
			hideKeyboard();
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

	public void hideKeyboard(){
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		View view = this.getCurrentFocus();
		if (imm != null && view != null) {
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	public void checkAndRequestAudioPermission() {
		if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
			if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED) {
				launchEchoCancellerCalibration(true);
			} else {
				success();
			}
		} else {
			success();
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		if (getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName()) == PackageManager.PERMISSION_GRANTED) {
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
		} else {
			checkAndRequestAudioPermission();
		}
	}

	private void logIn(String username, String password, String displayName, String domain, TransportType transport, boolean sendEcCalibrationResult) {
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

		if(address != null && displayName != null && !displayName.equals("")){
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

			if(transport != null) {
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
			if(!newAccount) {
				displayRegistrationInProgressDialog();
			}
			accountCreated = true;
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

 	public void displayRegistrationInProgressDialog(){
		if(LinphoneManager.getLc().isNetworkReachable()) {
			progress = ProgressDialog.show(this,null,null);
			Drawable d = new ColorDrawable(getResources().getColor(R.color.colorE));
			d.setAlpha(200);
			progress.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			progress.getWindow().setBackgroundDrawable(d);
			progress.setContentView(R.layout.progress_dialog);
			progress.show();
		}
	}

	public void displayAssistantConfirm(String username, String password) {
		CreateAccountActivationFragment fragment = new CreateAccountActivationFragment();
		newAccount = true;
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		extras.putString("Password", password);
		fragment.setArguments(extras);
		changeFragment(fragment);
		
		currentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_ACTIVATION;
		back.setVisibility(View.INVISIBLE);
	}
	
	public void isAccountVerified(String username) {
		Toast.makeText(this, getString(R.string.assistant_account_validated), Toast.LENGTH_LONG).show();
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
		if(LinphoneActivity.instance() != null) {
			LinphoneActivity.instance().isNewProxyConfig();
			setResult(Activity.RESULT_OK);
		}
		finish();
	}
}
