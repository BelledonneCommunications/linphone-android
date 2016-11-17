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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneUtils;
import org.linphone.LinphonePreferences.AccountBuilder;
import org.linphone.R;
import org.linphone.StatusFragment;
import org.linphone.core.DialPlan;
import org.linphone.core.LinphoneAccountCreator;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.tools.OpenH264DownloadHelper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sylvain Berfini
 */
public class AssistantActivity extends Activity implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback, LinphoneAccountCreator.LinphoneAccountCreatorListener {
private static AssistantActivity instance;
	private ImageView back, cancel;
	private AssistantFragmentsEnum currentFragment;
	private AssistantFragmentsEnum lastFragment;
	private AssistantFragmentsEnum firstFragment;
	private Fragment fragment;
	private LinphonePreferences mPrefs;
	private boolean accountCreated = false, newAccount = false, isLink = false, fromPref = false;
	private LinphoneCoreListenerBase mListener;
	private LinphoneAddress address;
	private StatusFragment status;
	private ProgressDialog progress;
	private Dialog dialog;
	private boolean remoteProvisioningInProgress;
	private boolean echoCancellerAlreadyDone;
	private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 201;
	private LinphoneAccountCreator accountCreator;
	private CountryListAdapter countryListAdapter;

	public DialPlan country;
	public String phone_number;
	public String email;
	public String activation_code;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		setContentView(R.layout.assistant);
		initUI();

		if (getIntent().getBooleanExtra("LinkPhoneNumber",false)) {
			isLink = true;
			if (getIntent().getBooleanExtra("FromPref",false))
				fromPref = true;
			displayCreateAccount();
		} else {
			firstFragment = getResources().getBoolean(R.bool.assistant_use_linphone_login_as_first_fragment) ? AssistantFragmentsEnum.LINPHONE_LOGIN : AssistantFragmentsEnum.WELCOME;
			if (findViewById(R.id.fragment_container) != null) {
				if (savedInstanceState == null) {
					display(firstFragment);
				} else {
					currentFragment = (AssistantFragmentsEnum) savedInstanceState.getSerializable("CurrentFragment");
				}
			}
		}
		if (savedInstanceState != null && savedInstanceState.containsKey("echoCanceller")) {
			echoCancellerAlreadyDone = savedInstanceState.getBoolean("echoCanceller");
		} else {
			echoCancellerAlreadyDone = false;
		}
        mPrefs = LinphonePreferences.instance();
		status.enableSideMenu(false);

		accountCreator = LinphoneCoreFactory.instance().createAccountCreator(LinphoneManager.getLc(), LinphonePreferences.instance().getXmlrpcUrl());
		accountCreator.setDomain(getResources().getString(R.string.default_domain));
		accountCreator.setListener(this);

		countryListAdapter = new CountryListAdapter(getApplicationContext());
        mListener = new LinphoneCoreListenerBase() {

			@Override
			public void configuringStatus(LinphoneCore lc, final LinphoneCore.RemoteProvisioningState state, String message) {
				if (progress != null) progress.dismiss();
				if (state == LinphoneCore.RemoteProvisioningState.ConfiguringSuccessful) {
					goToLinphoneActivity();
				} else if (state == LinphoneCore.RemoteProvisioningState.ConfiguringFailed) {
					Toast.makeText(AssistantActivity.instance(), getString(R.string.remote_provisioning_failure), Toast.LENGTH_LONG).show();
				}
			}

        	@Override
        	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, RegistrationState state, String smessage) {
        		if (remoteProvisioningInProgress) {
        			if (progress != null) progress.dismiss();
        			if (state == RegistrationState.RegistrationOk) {
            			remoteProvisioningInProgress = false;
        				success();
        			}
        		} else if (accountCreated && !newAccount){
					if (address != null && address.asString().equals(cfg.getAddress().asString()) ) {
						if (state == RegistrationState.RegistrationOk) {
							if (progress != null) progress.dismiss();
							if (LinphoneManager.getLc().getDefaultProxyConfig() != null) {
								accountCreator.isAccountUsed();
							}
						} else if (state == RegistrationState.RegistrationFailed) {
							if (progress != null) progress.dismiss();
							if (dialog == null || !dialog.isShowing()) {
								dialog = createErrorDialog(cfg, smessage);
								dialog.show();
							}
						} else if(!(state == RegistrationState.RegistrationProgress)) {
							if (progress != null) progress.dismiss();
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
		outState.putBoolean("echoCanceller", echoCancellerAlreadyDone);
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
			if (getResources().getBoolean(R.bool.assistant_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				LinphonePreferences.instance().firstLaunchSuccessful();
				startActivity(new Intent().setClass(this, LinphoneActivity.class));
				finish();
			}
		} else if (id == R.id.back) {
			hideKeyboard();
			onBackPressed();
		}
	}

	@Override
	public void onBackPressed() {
		if(isLink){
			return;
		}
		if (currentFragment == firstFragment) {
			LinphonePreferences.instance().firstLaunchSuccessful();
			if (getResources().getBoolean(R.bool.assistant_cancel_move_to_back)) {
				moveTaskToBack(true);
			} else {
				LinphonePreferences.instance().firstLaunchSuccessful();
				startActivity(new Intent().setClass(this, LinphoneActivity.class));
				finish();
			}
		} else if (currentFragment == AssistantFragmentsEnum.LOGIN
				|| currentFragment == AssistantFragmentsEnum.LINPHONE_LOGIN
				|| currentFragment == AssistantFragmentsEnum.CREATE_ACCOUNT
				|| currentFragment == AssistantFragmentsEnum.REMOTE_PROVISIONING) {
			displayMenu();
		} else if (currentFragment == AssistantFragmentsEnum.WELCOME) {
			finish();
		} else if (currentFragment == AssistantFragmentsEnum.COUNTRY_CHOOSER){
			if(lastFragment.equals(AssistantFragmentsEnum.LINPHONE_LOGIN)){
				displayLoginLinphone();
			} else {
				displayCreateAccount();
			}
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
		checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, 0);
	}

	public void checkAndRequestPermission(String permission, int result) {
		int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
		Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
			if (LinphonePreferences.instance().firstTimeAskingForPermission(permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
				Log.i("[Permission] Asking for " + permission);
				ActivityCompat.requestPermissions(this, new String[]{permission}, result);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
		}

		if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				launchEchoCancellerCalibration(true);
			} else {
				isEchoCalibrationFinished();
			}
		}
	}

	private void launchEchoCancellerCalibration(boolean sendEcCalibrationResult) {
		int recordAudio = getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
		Log.i("[Permission] Record audio permission is " + (recordAudio == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

		if (recordAudio == PackageManager.PERMISSION_GRANTED) {
			EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
			fragment.enableEcCalibrationResultSending(sendEcCalibrationResult);
			changeFragment(fragment);
			currentFragment = AssistantFragmentsEnum.ECHO_CANCELLER_CALIBRATION;
			back.setVisibility(View.VISIBLE);
			cancel.setEnabled(false);
		} else {
			checkAndRequestAudioPermission();
		}
	}

	private void logIn(String username, String password, String ha1, String prefix, String domain, TransportType transport, boolean sendEcCalibrationResult) {
		saveCreatedAccount(username, password, ha1, prefix, domain, transport);
	}

	public void checkAccount(String username, String password, String prefix, String domain) {
		saveCreatedAccount(username, password, null, prefix, domain, null);
	}

	public void linphoneLogIn(String username, String password, String ha1,  String prefix, boolean validate) {
		if (validate) {
			checkAccount(username, password, prefix, getString(R.string.default_domain));
		} else {
			if(accountCreated) {
				retryLogin(username, password, prefix, getString(R.string.default_domain), null);
			} else {
				logIn(username, password, ha1,  prefix, getString(R.string.default_domain), null, true);
			}
		}
	}

	public void genericLogIn(String username, String password, String prefix, String domain, TransportType transport) {
		if (accountCreated) {
			retryLogin(username, password, prefix, domain, transport);
		} else {
			logIn(username, password, null, prefix, domain, transport, false);
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
		country = null;
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
		Bundle extras = new Bundle();
		extras.putString("Phone", null);
		extras.putString("Dialcode", null);
		fragment.setArguments(extras);
		changeFragment(fragment);
		currentFragment = AssistantFragmentsEnum.LINPHONE_LOGIN;
		back.setVisibility(View.VISIBLE);
	}

	public void displayCreateAccount() {
		fragment = new CreateAccountFragment();
		Bundle extra = new Bundle();
		extra.putBoolean("LinkPhoneNumber", isLink);
		extra.putBoolean("LinkFromPref", fromPref);
		fragment.setArguments(extra);
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

	public void displayCountryChooser() {
		fragment = new CountryListFragment();
		changeFragment(fragment);
		lastFragment = currentFragment;
		currentFragment = AssistantFragmentsEnum.COUNTRY_CHOOSER;
		back.setVisibility(View.VISIBLE);
	}

	public void retryLogin(String username, String password, String prefix, String domain, TransportType transport) {
		accountCreated = false;
		saveCreatedAccount(username, password, null, prefix, domain, transport);
	}

	private void launchDownloadCodec() {
		if (LinphoneManager.getLc().openH264Enabled()) {
			OpenH264DownloadHelper downloadHelper = LinphoneCoreFactory.instance().createOpenH264DownloadHelper();
			if (Version.getCpuAbis().contains("armeabi-v7a") && !Version.getCpuAbis().contains("x86") && !downloadHelper.isCodecFound()) {
				CodecDownloaderFragment codecFragment = new CodecDownloaderFragment();
				changeFragment(codecFragment);
				currentFragment = AssistantFragmentsEnum.DOWNLOAD_CODEC;
				back.setVisibility(View.VISIBLE);
				cancel.setEnabled(false);
			} else
				goToLinphoneActivity();
		} else {
			goToLinphoneActivity();
		}
	}

	public void endDownloadCodec() {
		goToLinphoneActivity();
	}

	public String getPhoneWithCountry() {
		if(country == null || phone_number == null) return "";
		String phoneNumberWithCountry = country.getCountryCode() + phone_number.replace("\\D", "");
		return phoneNumberWithCountry;
	}

	public void saveCreatedAccount(String username, String password, String prefix, String ha1, String domain, TransportType transport) {
		if (accountCreated)
			return;

		username = LinphoneUtils.getDisplayableUsernameFromAddress(username);
		domain = LinphoneUtils.getDisplayableUsernameFromAddress(domain);

		String identity = "sip:" + username + "@" + domain;
		try {
			address = LinphoneCoreFactory.instance().createLinphoneAddress(identity);
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}

		boolean isMainAccountLinphoneDotOrg = domain.equals(getString(R.string.default_domain));
		AccountBuilder builder = new AccountBuilder(LinphoneManager.getLc())
		.setUsername(username)
		.setDomain(domain)
		.setHa1(ha1)
		.setPassword(password);

		if(prefix != null){
			builder.setPrefix(prefix);
		}

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

			mPrefs.enabledFriendlistSubscription(getResources().getBoolean(R.bool.use_friendlist_subscription));

			mPrefs.setStunServer(getString(R.string.default_stun));
			mPrefs.setIceEnabled(true);

			accountCreator.setPassword(password);
			accountCreator.setHa1(ha1);
			accountCreator.setUsername(username);
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
			Log.e(e);
		}
	}

 	public void displayRegistrationInProgressDialog() {
		if(LinphoneManager.getLc().isNetworkReachable()) {
			progress = ProgressDialog.show(this, null, null);
			Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorE));
			d.setAlpha(200);
			progress.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			progress.getWindow().setBackgroundDrawable(d);
			progress.setContentView(R.layout.progress_dialog);
			progress.show();
		}
	}

 	public void displayRemoteProvisioningInProgressDialog() {
		remoteProvisioningInProgress = true;

		progress = ProgressDialog.show(this, null, null);
		Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorE));
		d.setAlpha(200);
		progress.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
		progress.getWindow().setBackgroundDrawable(d);
		progress.setContentView(R.layout.progress_dialog);
		progress.show();
	}

	public void displayAssistantConfirm(String username, String password, String email) {
		CreateAccountActivationFragment fragment = new CreateAccountActivationFragment();
		newAccount = true;
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		extras.putString("Password", password);
		extras.putString("Email", email);
		fragment.setArguments(extras);
		changeFragment(fragment);

		currentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_ACTIVATION;
		back.setVisibility(View.INVISIBLE);
	}

	public void displayAssistantCodeConfirm(String username, String phone, String dialcode, boolean recoverAccount) {
		CreateAccountCodeActivationFragment fragment = new CreateAccountCodeActivationFragment();
		newAccount = true;
		Bundle extras = new Bundle();
		extras.putString("Username", username);
		extras.putString("Phone", phone);
		extras.putString("Dialcode", dialcode);
		extras.putBoolean("RecoverAccount", recoverAccount);
		extras.putBoolean("LinkAccount", isLink);
		fragment.setArguments(extras);
		changeFragment(fragment);

		currentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_CODE_ACTIVATION;
		back.setVisibility(View.INVISIBLE);
	}

	public void displayAssistantLinphoneLogin( String phone, String dialcode) {
		LinphoneLoginFragment fragment = new LinphoneLoginFragment();
		newAccount = true;
		Bundle extras = new Bundle();
		extras.putString("Phone", phone);
		extras.putString("Dialcode", dialcode);
		fragment.setArguments(extras);
		changeFragment(fragment);

		currentFragment = AssistantFragmentsEnum.LINPHONE_LOGIN;
		back.setVisibility(View.VISIBLE);
	}

	public void isAccountVerified(String username) {
		Toast.makeText(this, getString(R.string.assistant_account_validated), Toast.LENGTH_LONG).show();
		hideKeyboard();
		success();
	}

	public void isEchoCalibrationFinished() {
		launchDownloadCodec();
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
		boolean needsEchoCalibration = LinphoneManager.getLc().needsEchoCalibration();
		if (needsEchoCalibration && mPrefs.isFirstLaunch()) {
			launchEchoCancellerCalibration(true);
		} else {
			launchDownloadCodec();
		}
	}

	private void goToLinphoneActivity() {
		mPrefs.firstLaunchSuccessful();
		startActivity(new Intent().setClass(this, LinphoneActivity.class).putExtra("isNewProxyConfig", true));
		finish();
	}

	public void setLinphoneCoreListener() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}
		if (status != null) {
			status.setLinphoneCoreListener();
		}
	}

	@Override
	public void onAccountCreatorIsAccountUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {
		if(status.equals(LinphoneAccountCreator.Status.AccountExistWithAlias)){
			success();
		} else {
			isLink = true;
			displayCreateAccount();
		}

	}

	@Override
	public void onAccountCreatorAccountCreated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorAccountActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorAccountLinkedWithPhoneNumber(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorPhoneNumberLinkActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorIsAccountActivated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorPhoneAccountRecovered(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorIsAccountLinked(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorIsPhoneNumberUsed(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	@Override
	public void onAccountCreatorPasswordUpdated(LinphoneAccountCreator accountCreator, LinphoneAccountCreator.Status status) {

	}

	public CountryListAdapter getCountryListAdapter() {
		return countryListAdapter;
	}

	/**
	 * This class reads a JSON file containing Country-specific phone number description,
	 * and allows to present them into a ListView
	 */
	public class CountryListAdapter extends BaseAdapter implements Filterable {

		private LayoutInflater mInflater;
		private DialPlan[] allCountries;
		private List<DialPlan> filteredCountries;
		private Context context;

		public CountryListAdapter(Context ctx) {
			context = ctx;
			allCountries = LinphoneCoreFactory.instance().getAllDialPlan();
			filteredCountries = new ArrayList<DialPlan>(Arrays.asList(allCountries));
		}

		public void setInflater(LayoutInflater inf) {
			mInflater = inf;
		}


		public DialPlan getCountryFromCountryCode(String countryCode) {
			countryCode = (countryCode.startsWith("+")) ? countryCode.substring(1) : countryCode;
			for (DialPlan c : allCountries) {
				if (c.getCountryCallingCode().compareTo(countryCode) == 0)
					return c;
			}
			return null;
		}

		@Override
		public int getCount() {
			return filteredCountries.size();
		}

		@Override
		public DialPlan getItem(int position) {
			return filteredCountries.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent){
			View view;

			if (convertView != null) {
				view = convertView;
			} else {
				view = mInflater.inflate(R.layout.country_cell, parent, false);
			}

			DialPlan c = filteredCountries.get(position);

			TextView name = (TextView) view.findViewById(R.id.country_name);
			name.setText(c.getCountryName());

			TextView dial_code = (TextView) view.findViewById(R.id.country_prefix);
			if (context != null)
				dial_code.setText(String.format(context.getString(R.string.country_code),c.getCountryCallingCode()));

			view.setTag(c);
			return view;
		}

		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence constraint) {
					ArrayList<DialPlan> filteredCountries = new ArrayList<DialPlan>();
					for (DialPlan c : allCountries) {
						if (c.getCountryName().toLowerCase().contains(constraint)
								|| c.getCountryCallingCode().contains(constraint)) {
							filteredCountries.add(c);
						}
					}
					FilterResults filterResults = new FilterResults();
					filterResults.values = filteredCountries;
					return filterResults;
				}

				@Override
				@SuppressWarnings("unchecked")
				protected void publishResults(CharSequence constraint, FilterResults results) {
					filteredCountries = (List<DialPlan>) results.values;
					CountryListAdapter.this.notifyDataSetChanged();
				}
			};
		}
	}
}
