package org.linphone.assistant;
/*
AssistantActivity.java
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

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneLauncherActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.ConfiguringState;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;
import org.linphone.core.tools.Log;
import org.linphone.core.tools.OpenH264DownloadHelper;
import org.linphone.fragments.StatusFragment;
import org.linphone.mediastream.Version;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.ThemableActivity;

public class AssistantActivity extends ThemableActivity
        implements OnClickListener,
                ActivityCompat.OnRequestPermissionsResultCallback,
                AccountCreatorListener {
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 201;
    private static final int PERMISSIONS_REQUEST_CAMERA = 202;

    private static AssistantActivity sInstance;

    public DialPlan country;

    private ImageView mBack /*, mCancel*/;
    private AssistantFragmentsEnum mCurrentFragment;
    private AssistantFragmentsEnum mLastFragment;
    private AssistantFragmentsEnum mFirstFragment;
    private Fragment mFragment;
    private LinphonePreferences mPrefs;
    private boolean mAccountCreated = false,
            mNewAccount = false,
            mIsLink = false,
            mFromPref = false;
    private CoreListenerStub mListener;
    private Address mAddress;
    private StatusFragment mStatus;
    private ProgressDialog mProgress;
    private Dialog mDialog;
    private boolean mRemoteProvisioningInProgress;
    private boolean mEchoCancellerAlreadyDone;
    private AccountCreator mAccountCreator;
    private CountryListAdapter mCountryListAdapter;
    private LinearLayout mTopBar;

    public static AssistantActivity instance() {
        return sInstance;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.assistant);
        initUI();

        if (getIntent().getBooleanExtra("LinkPhoneNumber", false)) {
            mIsLink = true;
            if (getIntent().getBooleanExtra("FromPref", false)) mFromPref = true;
            displayCreateAccount();
        } else {
            mFirstFragment =
                    getResources().getBoolean(R.bool.assistant_use_linphone_login_as_first_fragment)
                            ? AssistantFragmentsEnum.LINPHONE_LOGIN
                            : AssistantFragmentsEnum.WELCOME;
            if (mFirstFragment == AssistantFragmentsEnum.WELCOME) {
                mFirstFragment =
                        getResources()
                                        .getBoolean(
                                                R.bool.assistant_use_create_linphone_account_as_first_fragment)
                                ? AssistantFragmentsEnum.CREATE_ACCOUNT
                                : AssistantFragmentsEnum.WELCOME;
            }

            if (findViewById(R.id.fragment_container) != null) {
                if (savedInstanceState == null) {
                    display(mFirstFragment);
                } else {
                    mCurrentFragment =
                            (AssistantFragmentsEnum)
                                    savedInstanceState.getSerializable("CurrentFragment");
                }
            }
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("echoCanceller")) {
            mEchoCancellerAlreadyDone = savedInstanceState.getBoolean("echoCanceller");
        } else {
            mEchoCancellerAlreadyDone = false;
        }
        mPrefs = LinphonePreferences.instance();
        mStatus.enableSideMenu(false);

        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            mAccountCreator =
                    LinphoneManager.getLc()
                            .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
            mAccountCreator.setListener(this);
        }

        mCountryListAdapter = new CountryListAdapter(getApplicationContext());
        mListener =
                new CoreListenerStub() {

                    @Override
                    public void onConfiguringStatus(
                            Core lc, final ConfiguringState state, String message) {
                        if (mProgress != null) mProgress.dismiss();
                        if (state == ConfiguringState.Successful) {
                            goToLinphoneActivity();
                        } else if (state == ConfiguringState.Failed) {
                            Toast.makeText(
                                            AssistantActivity.instance(),
                                            getString(R.string.remote_provisioning_failure),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            Core lc, ProxyConfig cfg, RegistrationState state, String smessage) {
                        if (mRemoteProvisioningInProgress) {
                            if (mProgress != null) mProgress.dismiss();
                            if (state == RegistrationState.Ok) {
                                mRemoteProvisioningInProgress = false;
                                success();
                            }
                        } else if (mAccountCreated && !mNewAccount) {
                            if (mAddress != null
                                    && mAddress.asString()
                                            .equals(cfg.getIdentityAddress().asString())) {
                                if (state == RegistrationState.Ok) {
                                    if (mProgress != null) mProgress.dismiss();
                                    if (getResources()
                                                    .getBoolean(R.bool.use_phone_number_validation)
                                            && cfg.getDomain()
                                                    .equals(getString(R.string.default_domain))
                                            && LinphoneManager.getLc().getDefaultProxyConfig()
                                                    != null) {
                                        loadAccountCreator(cfg).isAccountExist();
                                    } else {
                                        success();
                                    }
                                } else if (state == RegistrationState.Failed) {
                                    if (mProgress != null) mProgress.dismiss();
                                    if (mDialog == null || !mDialog.isShowing()) {
                                        mDialog = createErrorDialog(cfg, smessage);
                                        mDialog.setCancelable(false);
                                        mDialog.show();
                                    }
                                } else if (!(state == RegistrationState.Progress)) {
                                    if (mProgress != null) mProgress.dismiss();
                                }
                            }
                        }
                    }
                };
        sInstance = this;
    }

    @Override
    protected void onResume() {
        super.onResume();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("CurrentFragment", mCurrentFragment);
        outState.putBoolean("echoCanceller", mEchoCancellerAlreadyDone);
        super.onSaveInstanceState(outState);
    }

    public void updateStatusFragment(StatusFragment fragment) {
        mStatus = fragment;
    }

    private AccountCreator loadAccountCreator(ProxyConfig cfg) {
        ProxyConfig cfgTab[] = LinphoneManager.getLc().getProxyConfigList();
        int n = -1;
        for (int i = 0; i < cfgTab.length; i++) {
            if (cfgTab[i].equals(cfg)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            mAccountCreator.setDomain(mPrefs.getAccountDomain(n));
            mAccountCreator.setUsername(mPrefs.getAccountUsername(n));
        }
        return mAccountCreator;
    }

    private void initUI() {
        mBack = findViewById(R.id.back);
        mBack.setOnClickListener(this);
        // mCancel = findViewById(R.id.assistant_cancel);
        // mCancel.setOnClickListener(this);

        mTopBar = findViewById(R.id.topbar);
        if (getResources().getBoolean(R.bool.assistant_hide_top_bar)) {
            mTopBar.setVisibility(View.GONE);
        }
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
        boolean firstLaunch = LinphonePreferences.instance().isFirstLaunch();

        /*if (id == R.id.assistant_cancel) {
            hideKeyboard();
            LinphonePreferences.instance().firstLaunchSuccessful();
            if (getResources().getBoolean(R.bool.assistant_cancel_move_to_back)) {
                moveTaskToBack(true);
            } else {
                if (firstLaunch) startActivity(new Intent().setClass(this, LinphoneActivity.class));
                finish();
            }
        } else*/
        if (id == R.id.back) {
            hideKeyboard();
            if (mCurrentFragment == AssistantFragmentsEnum.WELCOME) {
                LinphonePreferences.instance().firstLaunchSuccessful();
                if (getResources().getBoolean(R.bool.assistant_cancel_move_to_back)) {
                    moveTaskToBack(true);
                } else {
                    if (firstLaunch)
                        startActivity(new Intent().setClass(this, LinphoneActivity.class));
                    finish();
                }
            } else {
                onBackPressed();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mIsLink) {
            return;
        }
        boolean firstLaunch = LinphonePreferences.instance().isFirstLaunch();
        if (mCurrentFragment == mFirstFragment) {
            LinphonePreferences.instance().firstLaunchSuccessful();
            if (getResources().getBoolean(R.bool.assistant_cancel_move_to_back)) {
                moveTaskToBack(true);
            } else {
                LinphonePreferences.instance().firstLaunchSuccessful();
                if (firstLaunch) startActivity(new Intent().setClass(this, LinphoneActivity.class));
                finish();
            }
        } else if (mCurrentFragment == AssistantFragmentsEnum.LOGIN
                || mCurrentFragment == AssistantFragmentsEnum.LINPHONE_LOGIN
                || mCurrentFragment == AssistantFragmentsEnum.CREATE_ACCOUNT
                || mCurrentFragment == AssistantFragmentsEnum.REMOTE_PROVISIONING) {
            displayMenu();
        } else if (mCurrentFragment == AssistantFragmentsEnum.WELCOME) {
            if (firstLaunch) startActivity(new Intent().setClass(this, LinphoneActivity.class));
            finish();
        } else if (mCurrentFragment == AssistantFragmentsEnum.COUNTRY_CHOOSER) {
            if (mLastFragment.equals(AssistantFragmentsEnum.LINPHONE_LOGIN)) {
                displayLoginLinphone(null, null);
            } else {
                displayCreateAccount();
            }
        } else if (mCurrentFragment == AssistantFragmentsEnum.QRCODE_READER) {
            displayRemoteProvisioning("");
        }
    }

    public void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = this.getCurrentFocus();
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void checkAndRequestAudioPermission() {
        checkAndRequestPermission(
                Manifest.permission.RECORD_AUDIO, PERMISSIONS_REQUEST_RECORD_AUDIO);
    }

    private void checkAndRequestVideoPermission() {
        checkAndRequestPermission(Manifest.permission.CAMERA, PERMISSIONS_REQUEST_CAMERA);
    }

    private void checkAndRequestPermission(String permission, int result) {
        int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " is "
                        + (permissionGranted == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));

        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[] {permission}, result);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, final int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_CAMERA:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    displayQRCodeReader();
                }
                break;
            case PERMISSIONS_REQUEST_RECORD_AUDIO:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    launchEchoCancellerCalibration();
                } else {
                    isEchoCalibrationFinished();
                }
                break;
        }
    }

    private void launchEchoCancellerCalibration() {
        int recordAudio =
                getPackageManager()
                        .checkPermission(Manifest.permission.RECORD_AUDIO, getPackageName());
        Log.i(
                "[Permission] Record audio permission is "
                        + (recordAudio == PackageManager.PERMISSION_GRANTED
                                ? "granted"
                                : "denied"));

        if (recordAudio == PackageManager.PERMISSION_GRANTED) {
            EchoCancellerCalibrationFragment fragment = new EchoCancellerCalibrationFragment();
            fragment.enableEcCalibrationResultSending(true);
            changeFragment(fragment);
            mCurrentFragment = AssistantFragmentsEnum.ECHO_CANCELLER_CALIBRATION;
            mBack.setVisibility(View.VISIBLE);
            mBack.setEnabled(false);
        } else {
            checkAndRequestAudioPermission();
        }
    }

    private void configureProxyConfig(AccountCreator accountCreator) {
        Core lc = LinphoneManager.getLc();
        ProxyConfig proxyConfig = lc.createProxyConfig();
        AuthInfo authInfo;

        String identity = proxyConfig.getIdentityAddress().asStringUriOnly();
        if (identity == null || accountCreator.getUsername() == null) {
            LinphoneUtils.displayErrorAlert(getString(R.string.error), this);
            return;
        }
        identity = identity.replace("?", accountCreator.getUsername());
        Address addr = Factory.instance().createAddress(identity);
        addr.setDisplayName(accountCreator.getUsername());
        mAddress = addr;
        proxyConfig.edit();

        proxyConfig.setIdentityAddress(addr);

        if (accountCreator.getPhoneNumber() != null && accountCreator.getPhoneNumber().length() > 0)
            proxyConfig.setDialPrefix(
                    org.linphone.core.Utils.getPrefixFromE164(accountCreator.getPhoneNumber()));

        proxyConfig.done();

        authInfo =
                Factory.instance()
                        .createAuthInfo(
                                accountCreator.getUsername(),
                                null,
                                accountCreator.getPassword(),
                                accountCreator.getHa1(),
                                proxyConfig.getRealm(),
                                proxyConfig.getDomain());

        lc.addProxyConfig(proxyConfig);

        lc.addAuthInfo(authInfo);

        lc.setDefaultProxyConfig(proxyConfig);

        if (LinphonePreferences.instance() != null)
            LinphonePreferences.instance().setPushNotificationEnabled(true);

        if (!mNewAccount) {
            displayRegistrationInProgressDialog();
        }
        mAccountCreated = true;
    }

    public void linphoneLogIn(AccountCreator accountCreator) {
        LinphoneManager.getLc()
                .loadConfigFromXml(LinphoneManager.getInstance().getLinphoneDynamicConfigFile());
        configureProxyConfig(accountCreator);
        // Restore default values for proxy config
        LinphoneManager.getLc()
                .loadConfigFromXml(LinphoneManager.getInstance().getDefaultDynamicConfigFile());
    }

    public void genericLogIn(
            String username,
            String userid,
            String password,
            String displayname,
            String prefix,
            String domain,
            TransportType transport) {
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (core == null) return;

        AuthInfo authInfo =
                Factory.instance().createAuthInfo(username, userid, password, null, null, domain);
        core.addAuthInfo(authInfo);

        ProxyConfig proxyConfig = core.createProxyConfig();

        String identity = "sip:" + username + "@" + domain;
        Address identityAddr = Factory.instance().createAddress(identity);
        if (identityAddr != null) {
            identityAddr.setDisplayName(displayname);
            proxyConfig.setIdentityAddress(identityAddr);
        }
        String proxy = "<sip:" + domain + ";transport=" + transport.name().toLowerCase() + ">";
        proxyConfig.setServerAddr(proxy);

        proxyConfig.setDialPrefix(prefix);

        core.addProxyConfig(proxyConfig);
        core.setDefaultProxyConfig(proxyConfig);

        mAccountCreated = true;
        success();
    }

    private void display(AssistantFragmentsEnum fragment) {
        switch (fragment) {
            case WELCOME:
                displayMenu();
                break;
            case LINPHONE_LOGIN:
                displayLoginLinphone(null, null);
                break;
            case CREATE_ACCOUNT:
                displayCreateAccount();
                break;
            default:
                throw new IllegalStateException("Can't handle " + fragment);
        }
    }

    private void displayMenu() {
        mFragment = new WelcomeFragment();
        changeFragment(mFragment);
        country = null;
        mCurrentFragment = AssistantFragmentsEnum.WELCOME;
    }

    public void displayLoginGeneric() {
        mFragment = new LoginFragment();
        changeFragment(mFragment);
        mCurrentFragment = AssistantFragmentsEnum.LOGIN;
    }

    public void displayLoginLinphone(String username, String password) {
        mFragment = new LinphoneLoginFragment();
        Bundle extras = new Bundle();
        extras.putString("Phone", null);
        extras.putString("Dialcode", null);
        extras.putString("Username", username);
        extras.putString("Password", password);
        mFragment.setArguments(extras);
        changeFragment(mFragment);
        mCurrentFragment = AssistantFragmentsEnum.LINPHONE_LOGIN;
    }

    public void displayCreateAccount() {
        mFragment = new CreateAccountFragment();
        Bundle extra = new Bundle();
        extra.putBoolean("LinkPhoneNumber", mIsLink);
        extra.putBoolean("LinkFromPref", mFromPref);
        mFragment.setArguments(extra);
        changeFragment(mFragment);
        mCurrentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT;
    }

    public void displayRemoteProvisioning(String url) {
        mFragment = new RemoteProvisioningFragment();
        Bundle extra = new Bundle();
        extra.putString("RemoteUrl", url);
        mFragment.setArguments(extra);
        changeFragment(mFragment);
        mCurrentFragment = AssistantFragmentsEnum.REMOTE_PROVISIONING;
    }

    public void displayQRCodeReader() {
        if (getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName())
                != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestVideoPermission();
        } else {
            mFragment = new QrCodeFragment();
            changeFragment(mFragment);
            mCurrentFragment = AssistantFragmentsEnum.QRCODE_READER;
        }
    }

    public void displayCountryChooser() {
        mFragment = new CountryListFragment();
        changeFragment(mFragment);
        mLastFragment = mCurrentFragment;
        mCurrentFragment = AssistantFragmentsEnum.COUNTRY_CHOOSER;
    }

    private void launchDownloadCodec() {
        if (OpenH264DownloadHelper.isOpenH264DownloadEnabled()) {
            OpenH264DownloadHelper downloadHelper =
                    Factory.instance().createOpenH264DownloadHelper(this);
            if (Version.getCpuAbis().contains("armeabi-v7a")
                    && !Version.getCpuAbis().contains("x86")
                    && !downloadHelper.isCodecFound()) {
                CodecDownloaderFragment codecFragment = new CodecDownloaderFragment();
                changeFragment(codecFragment);
                mCurrentFragment = AssistantFragmentsEnum.DOWNLOAD_CODEC;
                mBack.setEnabled(false);
            } else goToLinphoneActivity();
        } else {
            goToLinphoneActivity();
        }
    }

    public void endDownloadCodec() {
        goToLinphoneActivity();
    }

    private void displayRegistrationInProgressDialog() {
        if (LinphoneManager.getLc().isNetworkReachable()) {
            mProgress = ProgressDialog.show(this, null, null);
            Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.light_grey_color));
            d.setAlpha(200);
            mProgress
                    .getWindow()
                    .setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT);
            mProgress.getWindow().setBackgroundDrawable(d);
            mProgress.setContentView(R.layout.wait_layout);
            mProgress.show();
        }
    }

    public void displayRemoteProvisioningInProgressDialog() {
        mRemoteProvisioningInProgress = true;

        mProgress = ProgressDialog.show(this, null, null);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.light_grey_color));
        d.setAlpha(200);
        mProgress
                .getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mProgress.getWindow().setBackgroundDrawable(d);
        mProgress.setContentView(R.layout.wait_layout);
        mProgress.show();
    }

    public void displayAssistantConfirm(String username, String password, String email) {
        CreateAccountActivationFragment fragment = new CreateAccountActivationFragment();
        mNewAccount = true;
        Bundle extras = new Bundle();
        extras.putString("Username", username);
        extras.putString("Password", password);
        extras.putString("Email", email);
        fragment.setArguments(extras);
        changeFragment(fragment);

        mCurrentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_ACTIVATION;
    }

    public void displayAssistantCodeConfirm(
            String username, String phone, String dialcode, boolean recoverAccount) {
        CreateAccountCodeActivationFragment fragment = new CreateAccountCodeActivationFragment();
        mNewAccount = true;
        Bundle extras = new Bundle();
        extras.putString("Username", username);
        extras.putString("Phone", phone);
        extras.putString("Dialcode", dialcode);
        extras.putBoolean("RecoverAccount", recoverAccount);
        extras.putBoolean("LinkAccount", mIsLink);
        fragment.setArguments(extras);
        changeFragment(fragment);

        mCurrentFragment = AssistantFragmentsEnum.CREATE_ACCOUNT_CODE_ACTIVATION;
    }

    public void displayAssistantLinphoneLogin(String phone, String dialcode) {
        LinphoneLoginFragment fragment = new LinphoneLoginFragment();
        mNewAccount = true;
        Bundle extras = new Bundle();
        extras.putString("Phone", phone);
        extras.putString("Dialcode", dialcode);
        fragment.setArguments(extras);
        changeFragment(fragment);

        mCurrentFragment = AssistantFragmentsEnum.LINPHONE_LOGIN;
    }

    public void isAccountVerified() {
        Toast.makeText(this, getString(R.string.assistant_account_validated), Toast.LENGTH_LONG)
                .show();
        hideKeyboard();
        success();
    }

    public void isEchoCalibrationFinished() {
        launchDownloadCodec();
    }

    private Dialog createErrorDialog(ProxyConfig proxy, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (message.equals("Forbidden")) {
            message = getString(R.string.assistant_error_bad_credentials);
        }
        builder.setMessage(message)
                .setTitle(proxy.getState().toString())
                .setPositiveButton(
                        getString(R.string.continue_text),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                success();
                            }
                        })
                .setNegativeButton(
                        getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                LinphoneManager.getLc()
                                        .removeProxyConfig(
                                                LinphoneManager.getLc().getDefaultProxyConfig());
                                LinphonePreferences.instance().resetDefaultProxyConfig();
                                LinphoneManager.getLc().refreshRegisters();
                                dialog.dismiss();
                            }
                        });
        return builder.show();
    }

    public void success() {
        boolean needsEchoCalibration = LinphoneManager.getLc().isEchoCancellerCalibrationRequired();
        if (needsEchoCalibration && mPrefs.isFirstLaunch()) {
            launchEchoCancellerCalibration();
        } else {
            launchDownloadCodec();
        }
    }

    private void goToLinphoneActivity() {
        mPrefs.firstLaunchSuccessful();
        startActivity(
                new Intent()
                        .setClass(this, LinphoneActivity.class)
                        .putExtra("isNewProxyConfig", true));
        finish();
    }

    public void setCoreListener() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
        if (mStatus != null) {
            mStatus.setCoreListener();
        }
    }

    public void restartApplication() {
        mPrefs.firstLaunchSuccessful();

        Intent mStartActivity = new Intent(this, LinphoneLauncherActivity.class);
        PendingIntent mPendingIntent =
                PendingIntent.getActivity(
                        this,
                        (int) System.currentTimeMillis(),
                        mStartActivity,
                        PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent);

        finish();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (status.equals(AccountCreator.Status.AccountExistWithAlias)) {
            success();
        } else {
            mIsLink = true;
            displayCreateAccount();
        }
        if (mAccountCreator != null) mAccountCreator.setListener(null);
    }

    @Override
    public void onCreateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onLinkAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAlias(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountActivated(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onRecoverAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountLinked(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAliasUsed(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onUpdateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    public CountryListAdapter getCountryListAdapter() {
        return mCountryListAdapter;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mCurrentFragment == AssistantFragmentsEnum.QRCODE_READER) {
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    /**
     * This class reads a JSON file containing Country-specific phone number description, and allows
     * to present them into a ListView
     */
    public class CountryListAdapter extends BaseAdapter implements Filterable {

        private LayoutInflater mInflater;
        private final DialPlan[] allCountries;
        private List<DialPlan> filteredCountries;
        private final Context context;

        CountryListAdapter(Context ctx) {
            context = ctx;
            allCountries = Factory.instance().getDialPlans();
            filteredCountries = new ArrayList<>(Arrays.asList(allCountries));
        }

        public void setInflater(LayoutInflater inf) {
            mInflater = inf;
        }

        public DialPlan getCountryFromCountryCode(String countryCode) {
            countryCode = (countryCode.startsWith("+")) ? countryCode.substring(1) : countryCode;
            for (DialPlan c : allCountries) {
                if (c.getCountryCallingCode().compareTo(countryCode) == 0) return c;
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
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView != null) {
                view = convertView;
            } else {
                view = mInflater.inflate(R.layout.country_cell, parent, false);
            }

            DialPlan c = filteredCountries.get(position);

            TextView name = view.findViewById(R.id.country_name);
            name.setText(c.getCountry());

            TextView dial_code = view.findViewById(R.id.country_prefix);
            if (context != null)
                dial_code.setText(
                        String.format(
                                context.getString(R.string.country_code),
                                c.getCountryCallingCode()));

            view.setTag(c);
            return view;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    ArrayList<DialPlan> filteredCountries = new ArrayList<>();
                    for (DialPlan c : allCountries) {
                        if (c.getCountry().toLowerCase().contains(constraint)
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
