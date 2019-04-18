package org.linphone;

/*
LinphoneActivity.java
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
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.linphone.LinphoneManager.AddressType;
import org.linphone.assistant.AssistantActivity;
import org.linphone.assistant.RemoteProvisioningLoginActivity;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.chat.ChatMessagesFragment;
import org.linphone.chat.ChatRoomCreationFragment;
import org.linphone.chat.ChatRoomsFragment;
import org.linphone.chat.DevicesFragment;
import org.linphone.chat.GroupInfoFragment;
import org.linphone.chat.ImdnFragment;
import org.linphone.chat.ImdnOldFragment;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactDetailsFragment;
import org.linphone.contacts.ContactEditorFragment;
import org.linphone.contacts.ContactsFragment;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.Call.State;
import org.linphone.core.CallLog;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.Reason;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.fragments.AboutFragment;
import org.linphone.fragments.DialerFragment;
import org.linphone.fragments.EmptyFragment;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.fragments.StatusFragment;
import org.linphone.history.HistoryDetailFragment;
import org.linphone.history.HistoryFragment;
import org.linphone.purchase.InAppPurchaseActivity;
import org.linphone.recording.RecordingsFragment;
import org.linphone.settings.AccountSettingsFragment;
import org.linphone.settings.AudioSettingsFragment;
import org.linphone.settings.LinphonePreferences;
import org.linphone.settings.SettingsFragment;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.IntentUtils;
import org.linphone.utils.LinphoneGenericActivity;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.PushNotificationUtils;
import org.linphone.views.AddressText;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

public class LinphoneActivity extends LinphoneGenericActivity
        implements OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final int ANDROID_APP_SETTINGS_ACTIVITY = 300;

    private static final int SETTINGS_ACTIVITY = 123;
    private static final int CALL_ACTIVITY = 19;
    private static final int PERMISSIONS_REQUEST_OVERLAY = 206;
    private static final int PERMISSIONS_REQUEST_SYNC = 207;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER = 209;
    private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_TESTER = 211;

    private static LinphoneActivity sInstance;

    public String addressWaitingToBeCalled;

    private StatusFragment mStatusFragment;
    private TextView mMissedCalls, mMissedChats;
    private RelativeLayout mContacts, mHistory, mDialer, mChat;
    private View mContactsSelected, mHistorySelected, mDialerSelected, mChatSelected;
    private LinearLayout mTopBar;
    private TextView mTopBarTitle;
    private ImageView mCancel;
    private FragmentsAvailable mPendingFragmentTransaction, mCurrentFragment, mLeftFragment;
    private Fragment mFragment;
    private Fragment.SavedState mDialerSavedState;
    private boolean mNewProxyConfig;
    private boolean mEmptyFragment = false;
    private boolean mIsTrialAccount = false;
    private OrientationEventListener mOrientationHelper;
    private CoreListenerStub mListener;
    private LinearLayout mTabBar;
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent, mQuitLayout, mDefaultAccount;
    private ListView mAccountsList, mSideMenuItemList;
    private ImageView mMenu;
    private List<MenuItem> mSideMenuItems;
    private boolean mCallTransfer = false;
    private boolean mIsOnBackground = false;
    private int mAlwaysChangingPhoneAngle = -1;

    public static boolean isInstanciated() {
        return sInstance != null;
    }

    public static LinphoneActivity instance() {
        if (sInstance != null) return sInstance;
        throw new RuntimeException("LinphoneActivity not instantiated yet");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // This must be done before calling super.onCreate().
        super.onCreate(savedInstanceState);

        LinphoneService.instance().removeForegroundServiceNotificationIfPossible();

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        boolean useFirstLoginActivity =
                getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
        if (LinphonePreferences.instance().isProvisioningLoginViewEnabled()) {
            Intent wizard = new Intent();
            wizard.setClass(this, RemoteProvisioningLoginActivity.class);
            wizard.putExtra("Domain", LinphoneManager.getInstance().wizardLoginViewDomain);
            startActivity(wizard);
            finish();
            return;
        } else if (savedInstanceState == null
                && (useFirstLoginActivity
                        && LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null
                        && LinphonePreferences.instance().isFirstLaunch())) {
            if (LinphonePreferences.instance().getAccountCount() > 0) {
                LinphonePreferences.instance().firstLaunchSuccessful();
            } else {
                startActivity(new Intent().setClass(this, AssistantActivity.class));
                finish();
                return;
            }
        }

        if (getResources().getBoolean(R.bool.use_linphone_tag)) {
            if (getPackageManager()
                            .checkPermission(
                                    Manifest.permission.WRITE_SYNC_SETTINGS, getPackageName())
                    != PackageManager.PERMISSION_GRANTED) {
                checkSyncPermission();
            }
        }

        setContentView(R.layout.main);
        sInstance = this;
        mPendingFragmentTransaction = FragmentsAvailable.UNKNOW;

        initButtons();
        initSideMenu();

        mCurrentFragment = FragmentsAvailable.EMPTY;
        if (savedInstanceState == null) {
            changeCurrentFragment(FragmentsAvailable.DIALER, getIntent().getExtras());
        } else {
            mCurrentFragment =
                    (FragmentsAvailable) savedInstanceState.getSerializable("mCurrentFragment");
        }

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
                        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            Core lc, ProxyConfig proxy, RegistrationState state, String smessage) {
                        AuthInfo authInfo =
                                lc.findAuthInfo(
                                        proxy.getRealm(),
                                        proxy.getIdentityAddress().getUsername(),
                                        proxy.getDomain());

                        refreshAccounts();

                        if (getResources().getBoolean(R.bool.use_phone_number_validation)
                                && authInfo != null
                                && authInfo.getDomain()
                                        .equals(getString(R.string.default_domain))) {
                            if (state.equals(RegistrationState.Ok)) {
                                LinphoneManager.getInstance().isAccountWithAlias();
                            }
                        }

                        if (state.equals(RegistrationState.Failed) && mNewProxyConfig) {
                            mNewProxyConfig = false;
                            if (proxy.getError() == Reason.Unauthorized) {
                                displayCustomToast(
                                        getString(R.string.error_unauthorized), Toast.LENGTH_LONG);
                            }
                            if (proxy.getError() == Reason.IOError) {
                                displayCustomToast(
                                        getString(R.string.error_io_error), Toast.LENGTH_LONG);
                            }
                        }

                        if (state == RegistrationState.Ok) {
                            // For push notifications to work on Huawei device,
                            // app must be in "protected mode" in battery settings...
                            // https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
                            DeviceUtils
                                    .displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(
                                            LinphoneActivity.this);
                        }
                    }

                    @Override
                    public void onCallStateChanged(
                            Core lc, Call call, Call.State state, String message) {
                        if (state == State.IncomingReceived) {
                            // This case will be handled by the service listener
                        } else if (state == State.OutgoingInit || state == State.OutgoingProgress) {
                            startActivity(
                                    new Intent(
                                            LinphoneActivity.instance(),
                                            CallOutgoingActivity.class));
                        } else if (state == State.End
                                || state == State.Error
                                || state == State.Released) {
                            resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                        }

                        int missedCalls = LinphoneManager.getLc().getMissedCallsCount();
                        displayMissedCalls(missedCalls);
                    }
                };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            int missedCalls = lc.getMissedCallsCount();
            displayMissedCalls(missedCalls);
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                rotation = 0;
                break;
            case Surface.ROTATION_90:
                rotation = 90;
                break;
            case Surface.ROTATION_180:
                rotation = 180;
                break;
            case Surface.ROTATION_270:
                rotation = 270;
                break;
        }

        mAlwaysChangingPhoneAngle = rotation;
        if (LinphoneManager.isInstanciated()) {
            LinphoneManager.getLc().setDeviceRotation(rotation);
            onNewIntent(getIntent());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        String[] permissionsToHave = {
            // This one is to allow floating notifications
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            // Required starting Android 9 to be able to start a foreground service
            "android.permission.FOREGROUND_SERVICE",
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CONTACTS
        };

        for (String permissionToHave : permissionsToHave) {
            if (!checkPermission(permissionToHave)) {
                permissionsToAskFor.add(permissionToHave);
            }
        }

        if (permissionsToAskFor.size() > 0) {
            for (String permission : permissionsToAskFor) {
                Log.i("[Permission] Asking for " + permission + " permission");
            }
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                // This is to workaround an infinite loop of pause/start in LinphoneActivity issue
                // if incoming call ends while screen if off and locked
                ActivityCompat.requestPermissions(
                        this, permissions, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
            }
        } else {
            if (getResources().getBoolean(R.bool.check_for_update_when_app_starts)) {
                checkForUpdate();
            }
        }

        if (checkPermission(Manifest.permission.READ_CONTACTS)) {
            ContactsManager.getInstance().enableContactsAccess();
        }
        ContactsManager.getInstance().initializeContactManager(this);

        if (DeviceUtils.isAppUserRestricted(this)) {
            Log.w(
                    "[Linphone Activity] Device has been restricted by user (Android 9+), push notifications won't work !");
        }

        int bucket = DeviceUtils.getAppStandbyBucket(this);
        if (bucket > 0) {
            Log.w(
                    "[Linphone Activity] Device is in bucket "
                            + Compatibility.getAppStandbyBucketNameFromValue(bucket));
        }

        if (!PushNotificationUtils.isAvailable(this)) {
            Log.w("[Linphone Activity] Push notifications won't work !");
        }

        IntentUtils.handleIntent(this, getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mCurrentFragment", mCurrentFragment);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        mIsOnBackground = true;

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!LinphoneService.isReady()) {
            startService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        }

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        if (isTablet()) {
            // Prevent fragmentContainer2 to be visible when rotating the device
            LinearLayout ll = findViewById(R.id.fragmentContainer2);
            if (mCurrentFragment == FragmentsAvailable.DIALER
                    || mCurrentFragment == FragmentsAvailable.ABOUT
                    || mCurrentFragment == FragmentsAvailable.SETTINGS
                    || mCurrentFragment == FragmentsAvailable.SETTINGS_SUBLEVEL
                    || mCurrentFragment == FragmentsAvailable.ACCOUNT_SETTINGS) {
                ll.setVisibility(View.GONE);
            }
        }

        refreshAccounts();

        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            isTrialAccount();
        }

        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
        displayMissedCalls(LinphoneManager.getLc().getMissedCallsCount());

        if (!getIntent().getBooleanExtra("DoNotGoToCallActivity", false)) {
            if (LinphoneManager.getLc().getCalls().length > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];
                Call.State onCallStateChanged = call.getState();

                if (onCallStateChanged == State.IncomingReceived
                        || onCallStateChanged == State.IncomingEarlyMedia) {
                    startActivity(new Intent(this, CallIncomingActivity.class));
                } else if (onCallStateChanged == State.OutgoingInit
                        || onCallStateChanged == State.OutgoingProgress
                        || onCallStateChanged == State.OutgoingRinging) {
                    startActivity(new Intent(this, CallOutgoingActivity.class));
                } else {
                    startIncallActivity();
                }
            }
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (mPendingFragmentTransaction != FragmentsAvailable.UNKNOW) {
            changeCurrentFragment(mPendingFragmentTransaction, null);
            selectMenu(mPendingFragmentTransaction);
            mPendingFragmentTransaction = FragmentsAvailable.UNKNOW;
        }
    }

    @Override
    protected void onDestroy() {
        if (mOrientationHelper != null) {
            mOrientationHelper.disable();
            mOrientationHelper = null;
        }

        sInstance = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (getIntent() != null && getIntent().getExtras() != null) {
            mNewProxyConfig = getIntent().getExtras().getBoolean("isNewProxyConfig");
        }

        if (requestCode == ANDROID_APP_SETTINGS_ACTIVITY) {
            LinphoneActivity.instance().goToDialerFragment();
        } else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
            if (data.getExtras().getBoolean("Exit", false)) {
                quit();
            } else {
                mPendingFragmentTransaction =
                        (FragmentsAvailable) data.getExtras().getSerializable("FragmentToDisplay");
            }
        } else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == CALL_ACTIVITY) {
            getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
            mCallTransfer = data != null && data.getBooleanExtra("Transfer", false);
            if (LinphoneManager.getLc().getCallsNb() > 0) {
                initInCallMenuLayout();
            } else {
                resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
            }
        } else if (requestCode == PERMISSIONS_REQUEST_OVERLAY) {
            if (Compatibility.canDrawOverlays(this)) {
                LinphonePreferences.instance().enableOverlay(true);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        /*if (getCurrentFragment() == FragmentsAvailable.SETTINGS) {
            if (mFragment instanceof SettingsFragment) {
                ((SettingsFragment) mFragment).closePreferenceScreen();
            }
        }*/

        Bundle extras = intent.getExtras();
        mCallTransfer = false;
        if (extras != null) {
            if (extras.getBoolean("GoToChat", false)) {
                String localSipUri = extras.getString("LocalSipUri");
                String remoteSipUri = extras.getString("ChatContactSipUri");
                Log.i(
                        "[Linphone Activity] Intent asked to go to chat, local URI "
                                + localSipUri
                                + ", remote URI "
                                + remoteSipUri);
                intent.putExtra("DoNotGoToCallActivity", true);
                if (remoteSipUri == null) {
                    goToChatList();
                } else {
                    goToChat(localSipUri, remoteSipUri, extras);
                }
            } else if (extras.getBoolean("GoToHistory", false)) {
                Log.i("[Linphone Activity] Intent asked to go to call history");
                intent.putExtra("DoNotGoToCallActivity", true);
                changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
            } else if (extras.getBoolean("GoToInapp", false)) {
                Log.i("[Linphone Activity] Intent asked to go to inapp");
                intent.putExtra("DoNotGoToCallActivity", true);
                displayInapp();
            } else if (extras.getBoolean("Notification", false)) {
                if (LinphoneManager.getLc().getCallsNb() > 0) {
                    startIncallActivity();
                }
            } else if (extras.getBoolean("StartCall", false)) {
                addressWaitingToBeCalled = extras.getString("NumberToCall");
                goToDialerFragment();
            } else if (extras.getBoolean("Transfer", false)) {
                intent.putExtra("DoNotGoToCallActivity", true);
                mCallTransfer = true;
                if (LinphoneManager.getLc().getCallsNb() > 0) {
                    initInCallMenuLayout();
                } else {
                    resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
                }
            } else if (extras.getBoolean("AddCall", false)) {
                intent.putExtra("DoNotGoToCallActivity", true);
            } else if (intent.getStringExtra("msgShared") != null) {
                String message = intent.getStringExtra("msgShared");
                Log.i(
                        "[Linphone Activity] Intent asked to go to chat list to share message "
                                + message);
                extras.putString("messageDraft", message);
                changeCurrentFragment(FragmentsAvailable.CHAT_LIST, extras);
                intent.removeExtra("msgShared");
            } else if (intent.getStringExtra("fileShared") != null
                    && !intent.getStringExtra("fileShared").equals("")) {
                String file = intent.getStringExtra("fileShared");
                Log.i(
                        "[Linphone Activity] Intent asked to go to chat list to share file(s) "
                                + file);
                extras.putString("fileSharedUri", file);
                changeCurrentFragment(FragmentsAvailable.CHAT_LIST, extras);
                intent.removeExtra("fileShared");
            } else {
                DialerFragment dialerFragment = DialerFragment.instance();
                if (dialerFragment != null) {
                    if (extras.containsKey("SipUriOrNumber")) {
                        if (getResources()
                                .getBoolean(
                                        R.bool.automatically_start_intercepted_outgoing_gsm_call)) {
                            dialerFragment.newOutgoingCall(extras.getString("SipUriOrNumber"));
                        } else {
                            dialerFragment.displayTextInAddressBar(
                                    extras.getString("SipUriOrNumber"));
                        }
                    }
                } else {
                    if (extras.containsKey("SipUriOrNumber")) {
                        addressWaitingToBeCalled = extras.getString("SipUriOrNumber");
                        goToDialerFragment();
                    }
                }
            }
        }
        setIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {

        // If permission was asked we wait here for the results so dialogs won't conflict
        if (getResources().getBoolean(R.bool.check_for_update_when_app_starts)) {
            checkForUpdate();
        }

        if (permissions.length <= 0) return;

        int readContactsI = -1;
        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
            if (permissions[i].compareTo(Manifest.permission.READ_CONTACTS) == 0
                    || permissions[i].compareTo(Manifest.permission.WRITE_CONTACTS) == 0)
                readContactsI = i;
        }

        if (readContactsI >= 0
                && grantResults[readContactsI] == PackageManager.PERMISSION_GRANTED) {
            ContactsManager.getInstance().enableContactsAccess();
        }
        switch (requestCode) {
            case PERMISSIONS_REQUEST_SYNC:
                ContactsManager.getInstance().initializeContactManager(this);
                break;
            case PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ((AudioSettingsFragment) mFragment).startEchoCancellerCalibration();
                }
                break;
            case PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE:
                if (permissions[0].compareTo(Manifest.permission.READ_EXTERNAL_STORAGE) != 0) break;
                boolean enableRingtone = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
                break;
            case PERMISSIONS_RECORD_AUDIO_ECHO_TESTER:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    ((AudioSettingsFragment) mFragment).startEchoTester();
                break;
        }
    }

    private void checkForUpdate() {
        String url = LinphonePreferences.instance().getCheckReleaseUrl();
        if (url != null && !url.isEmpty()) {
            int lastTimestamp = LinphonePreferences.instance().getLastCheckReleaseTimestamp();
            int currentTimeStamp = (int) System.currentTimeMillis();
            int interval = getResources().getInteger(R.integer.time_between_update_check); // 24h
            if (lastTimestamp == 0 || currentTimeStamp - lastTimestamp >= interval) {
                LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                        .checkForUpdate(BuildConfig.VERSION_NAME);
                LinphonePreferences.instance().setLastCheckReleaseTimestamp(currentTimeStamp);
            }
        }
    }

    private void initButtons() {
        mTabBar = findViewById(R.id.footer);
        mTopBar = findViewById(R.id.top_bar);
        mTopBarTitle = findViewById(R.id.top_bar_title);

        mCancel = findViewById(R.id.cancel);
        mCancel.setOnClickListener(this);

        mHistory = findViewById(R.id.history);
        mHistory.setOnClickListener(this);
        mContacts = findViewById(R.id.contacts);
        mContacts.setOnClickListener(this);
        mDialer = findViewById(R.id.dialer);
        mDialer.setOnClickListener(this);
        mChat = findViewById(R.id.chat);
        mChat.setOnClickListener(this);
        if (getResources().getBoolean(R.bool.disable_chat)) {
            mChat.setVisibility(View.GONE);
        }

        mHistorySelected = findViewById(R.id.history_select);
        mContactsSelected = findViewById(R.id.contacts_select);
        mDialerSelected = findViewById(R.id.dialer_select);
        mChatSelected = findViewById(R.id.chat_select);

        mMissedCalls = findViewById(R.id.missed_calls);
        mMissedChats = findViewById(R.id.missed_chats);
    }

    public boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    public void hideStatusBar() {
        if (isTablet()) {
            return;
        }

        findViewById(R.id.status).setVisibility(View.GONE);
    }

    public void showStatusBar() {
        if (isTablet()) {
            return;
        }

        if (mStatusFragment != null && !mStatusFragment.isVisible()) {
            mStatusFragment.getView().setVisibility(View.VISIBLE);
        }
        findViewById(R.id.status).setVisibility(View.VISIBLE);
    }

    public void popBackStack() {
        getFragmentManager().popBackStackImmediate();
        mCurrentFragment = FragmentsAvailable.EMPTY;
    }

    private void changeCurrentFragment(FragmentsAvailable newFragmentType, Bundle extras) {
        if (newFragmentType == mCurrentFragment
                && newFragmentType != FragmentsAvailable.CHAT_LIST
                && newFragmentType != FragmentsAvailable.CHAT
                && newFragmentType != FragmentsAvailable.GROUP_CHAT) {
            return;
        }

        if (mCurrentFragment == FragmentsAvailable.DIALER) {
            try {
                DialerFragment dialerFragment = DialerFragment.instance();
                mDialerSavedState = getFragmentManager().saveFragmentInstanceState(dialerFragment);
            } catch (Exception e) {
                Log.e(e);
            }
        }

        mFragment = null;
        switch (newFragmentType) {
            case HISTORY_LIST:
                mFragment = new HistoryFragment();
                break;
            case HISTORY_DETAIL:
                mFragment = new HistoryDetailFragment();
                break;
            case CONTACTS_LIST:
                mFragment = new ContactsFragment();
                break;
            case CONTACT_DETAIL:
                mFragment = new ContactDetailsFragment();
                break;
            case CONTACT_EDITOR:
                mFragment = new ContactEditorFragment();
                break;
            case DIALER:
                mFragment = new DialerFragment();
                if (extras == null) {
                    mFragment.setInitialSavedState(mDialerSavedState);
                }
                break;
            case SETTINGS:
                mFragment = new SettingsFragment();
                break;
            case ACCOUNT_SETTINGS:
                mFragment = new AccountSettingsFragment();
                break;
            case ABOUT:
                mFragment = new AboutFragment();
                break;
            case EMPTY:
                mFragment = new EmptyFragment();
                break;
            case CHAT_LIST:
                mFragment = new ChatRoomsFragment();
                break;
            case CREATE_CHAT:
                mFragment = new ChatRoomCreationFragment();
                break;
            case INFO_GROUP_CHAT:
                mFragment = new GroupInfoFragment();
                break;
            case GROUP_CHAT:
                mFragment = new ChatMessagesFragment();
                break;
            case MESSAGE_IMDN:
                if (getResources().getBoolean(R.bool.use_new_chat_bubbles_layout)) {
                    mFragment = new ImdnFragment();
                } else {
                    mFragment = new ImdnOldFragment();
                }
                break;
            case CONTACT_DEVICES:
                mFragment = new DevicesFragment();
                break;
            case RECORDING_LIST:
                mFragment = new RecordingsFragment();
                break;
            default:
                break;
        }

        applyFragmentChanges(newFragmentType, extras);
    }

    private void changeSettingsFragment(Fragment fragment) {
        mFragment = fragment;
        applyFragmentChanges(FragmentsAvailable.SETTINGS_SUBLEVEL, null);
    }

    private void applyFragmentChanges(FragmentsAvailable newFragmentType, Bundle extras) {
        if (mFragment != null) {
            mFragment.setArguments(extras);
            if (isTablet()) {
                changeFragmentForTablets(mFragment, newFragmentType);
                switch (newFragmentType) {
                    case HISTORY_LIST:
                        ((HistoryFragment) mFragment).displayFirstLog();
                        break;
                    case CONTACTS_LIST:
                        ((ContactsFragment) mFragment).displayFirstContact();
                        break;
                    case CHAT_LIST:
                        ((ChatRoomsFragment) mFragment).displayFirstChat();
                        break;
                }
            } else {
                changeFragment(mFragment, newFragmentType);
            }
            LinphoneUtils.hideKeyboard(this);
        }
    }

    private void changeFragment(Fragment newFragment, FragmentsAvailable newFragmentType) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        if (newFragmentType != FragmentsAvailable.DIALER
                && newFragmentType != FragmentsAvailable.CONTACTS_LIST
                && newFragmentType != FragmentsAvailable.CHAT_LIST
                && newFragmentType != FragmentsAvailable.HISTORY_LIST) {
            transaction.addToBackStack(newFragmentType.toString());
        } else {
            while (fm.getBackStackEntryCount() > 0) {
                fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        transaction.replace(R.id.fragmentContainer, newFragment, newFragmentType.toString());
        transaction.commitAllowingStateLoss();
        fm.executePendingTransactions();

        mCurrentFragment = newFragmentType;
    }

    private void changeFragmentForTablets(
            Fragment newFragment, FragmentsAvailable newFragmentType) {
        if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
            if (newFragmentType == FragmentsAvailable.DIALER) {
                showStatusBar();
            } else {
                hideStatusBar();
            }
        }
        mEmptyFragment = false;
        LinearLayout ll = findViewById(R.id.fragmentContainer2);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        if (newFragmentType == FragmentsAvailable.EMPTY) {
            ll.setVisibility(View.VISIBLE);
            mEmptyFragment = true;
            transaction.replace(R.id.fragmentContainer2, newFragment);
            transaction.commitAllowingStateLoss();
            getFragmentManager().executePendingTransactions();
        } else {
            if (newFragmentType.shouldAddItselfToTheRightOf(mCurrentFragment)
                    || newFragmentType.shouldAddItselfToTheRightOf(mLeftFragment)) {
                ll.setVisibility(View.VISIBLE);

                if (newFragmentType == FragmentsAvailable.CONTACT_EDITOR) {
                    transaction.addToBackStack(newFragmentType.toString());
                }
                transaction.replace(R.id.fragmentContainer2, newFragment);
                mLeftFragment = mCurrentFragment;

                if (newFragmentType == FragmentsAvailable.GROUP_CHAT
                        && mLeftFragment != FragmentsAvailable.CHAT_LIST) {
                    mLeftFragment = FragmentsAvailable.CHAT_LIST;
                    transaction.replace(R.id.fragmentContainer, new ChatRoomsFragment());
                }
            } else {
                if (newFragmentType == FragmentsAvailable.EMPTY) {
                    ll.setVisibility(View.VISIBLE);
                    transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
                    mEmptyFragment = true;
                }

                if (newFragmentType == FragmentsAvailable.DIALER
                        || newFragmentType == FragmentsAvailable.ABOUT
                        || newFragmentType == FragmentsAvailable.SETTINGS
                        || newFragmentType == FragmentsAvailable.ACCOUNT_SETTINGS
                        || newFragmentType == FragmentsAvailable.CREATE_CHAT
                        || newFragmentType == FragmentsAvailable.INFO_GROUP_CHAT
                        || newFragmentType == FragmentsAvailable.RECORDING_LIST) {
                    ll.setVisibility(View.GONE);
                } else {
                    ll.setVisibility(View.VISIBLE);
                    transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
                }

                transaction.replace(R.id.fragmentContainer, newFragment);
            }
            transaction.commitAllowingStateLoss();
            getFragmentManager().executePendingTransactions();

            mCurrentFragment = newFragmentType;
            if (newFragmentType == FragmentsAvailable.DIALER
                    || newFragmentType == FragmentsAvailable.SETTINGS
                    || newFragmentType == FragmentsAvailable.CONTACTS_LIST
                    || newFragmentType == FragmentsAvailable.CHAT_LIST
                    || newFragmentType == FragmentsAvailable.HISTORY_LIST) {
                try {
                    getFragmentManager()
                            .popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                } catch (java.lang.IllegalStateException e) {
                    Log.e(e);
                }
            }
        }
    }

    public void displayHistoryDetail(String sipUri, CallLog log) {
        Address lAddress;
        LinphoneContact c = null;

        lAddress = Factory.instance().createAddress(sipUri);
        if (lAddress != null) {
            c = ContactsManager.getInstance().findContactFromAddress(lAddress);
        }

        String displayName =
                c != null ? c.getFullName() : LinphoneUtils.getAddressDisplayName(sipUri);
        String pictureUri =
                c != null && c.getPhotoUri() != null ? c.getPhotoUri().toString() : null;

        Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
        if (fragment2 != null
                && fragment2.isVisible()
                && mCurrentFragment == FragmentsAvailable.HISTORY_DETAIL) {
            HistoryDetailFragment historyDetailFragment = (HistoryDetailFragment) fragment2;
            historyDetailFragment.changeDisplayedHistory(sipUri, displayName);
        } else {
            Bundle extras = new Bundle();
            extras.putString("SipUri", sipUri);
            if (displayName != null) {
                extras.putString("DisplayName", displayName);
                extras.putString("PictureUri", pictureUri);
            }

            changeCurrentFragment(FragmentsAvailable.HISTORY_DETAIL, extras);
        }
    }

    public void displayEmptyFragment() {
        changeCurrentFragment(FragmentsAvailable.EMPTY, new Bundle());
    }

    public void displayContact(LinphoneContact contact, boolean chatOnly) {
        Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
        if (fragment2 != null
                && fragment2.isVisible()
                && mCurrentFragment == FragmentsAvailable.CONTACT_DETAIL) {
            ContactDetailsFragment contactFragment = (ContactDetailsFragment) fragment2;
            contactFragment.changeDisplayedContact(contact);
        } else {
            Bundle extras = new Bundle();
            extras.putSerializable("Contact", contact);
            extras.putBoolean("ChatAddressOnly", chatOnly);
            changeCurrentFragment(FragmentsAvailable.CONTACT_DETAIL, extras);
        }
    }

    public void displayContacts(boolean chatOnly) {
        Bundle extras = new Bundle();
        extras.putBoolean("ChatAddressOnly", chatOnly);
        changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
    }

    public void displayContactsForEdition(String sipAddress) {
        Bundle extras = new Bundle();
        extras.putBoolean("EditOnClick", true);
        extras.putString("SipAddress", sipAddress);
        changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
    }

    private void displayAbout() {
        changeCurrentFragment(FragmentsAvailable.ABOUT, null);
    }

    private void displayRecordings() {
        changeCurrentFragment(FragmentsAvailable.RECORDING_LIST, null);
    }

    public void displaySubSettings(Fragment fragment) {
        changeSettingsFragment(fragment);
    }

    public void displayContactsForEdition(String sipAddress, String displayName) {
        Bundle extras = new Bundle();
        extras.putBoolean("EditOnClick", true);
        extras.putString("SipAddress", sipAddress);
        extras.putString("DisplayName", displayName);
        changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
    }

    private void displayAssistant() {
        startActivity(new Intent(LinphoneActivity.this, AssistantActivity.class));
    }

    private void displayInapp() {
        startActivity(new Intent(LinphoneActivity.this, InAppPurchaseActivity.class));
    }

    public void goToChatCreator(
            String address,
            ArrayList<ContactAddress> selectedContacts,
            String subject,
            boolean isGoBack,
            Bundle shareInfos,
            boolean createGroupChat,
            boolean isChatRoomEncrypted) {
        if (mCurrentFragment == FragmentsAvailable.INFO_GROUP_CHAT && isGoBack) {
            getFragmentManager().popBackStackImmediate();
            getFragmentManager().popBackStackImmediate();
        }
        Bundle extras = new Bundle();
        extras.putSerializable("selectedContacts", selectedContacts);
        extras.putString("subject", subject);
        extras.putString("groupChatRoomAddress", address);
        extras.putBoolean("createGroupChatRoom", createGroupChat);
        extras.putBoolean("encrypted", isChatRoomEncrypted);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        changeCurrentFragment(FragmentsAvailable.CREATE_CHAT, extras);
    }

    public void goToChat(String localSipUri, String remoteSipUri, Bundle shareInfos) {
        Bundle extras = new Bundle();
        extras.putString("LocalSipUri", localSipUri);
        extras.putString("RemoteSipUri", remoteSipUri);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        if (isTablet()) {
            Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
            if (fragment2 != null
                    && fragment2.isVisible()
                    && mCurrentFragment == FragmentsAvailable.GROUP_CHAT
                    && !mEmptyFragment) {
                ChatMessagesFragment chatFragment = (ChatMessagesFragment) fragment2;
                chatFragment.changeDisplayedChat(localSipUri, remoteSipUri);
            } else {
                changeCurrentFragment(FragmentsAvailable.GROUP_CHAT, extras);
            }
        } else {
            changeCurrentFragment(FragmentsAvailable.GROUP_CHAT, extras);
        }

        LinphoneManager.getInstance().updateUnreadCountForChatRoom(localSipUri, remoteSipUri, 0);
        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
    }

    public void goToChatGroupInfos(
            String address,
            ArrayList<ContactAddress> contacts,
            String subject,
            boolean isEditionEnabled,
            boolean isGoBack,
            Bundle shareInfos,
            boolean enableEncryption) {
        if (mCurrentFragment == FragmentsAvailable.CREATE_CHAT && isGoBack) {
            getFragmentManager().popBackStackImmediate();
            getFragmentManager().popBackStackImmediate();
        }
        Bundle extras = new Bundle();
        extras.putString("groupChatRoomAddress", address);
        extras.putBoolean("isEditionEnabled", isEditionEnabled);
        extras.putSerializable("ContactAddress", contacts);
        extras.putString("subject", subject);
        extras.putBoolean("encryptionEnabled", enableEncryption);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        changeCurrentFragment(FragmentsAvailable.INFO_GROUP_CHAT, extras);
    }

    public void goToContactDevicesInfos(String localSipUri, String remoteSipUri) {
        Bundle extras = new Bundle();
        extras.putSerializable("LocalSipUri", localSipUri);
        extras.putSerializable("RemoteSipUri", remoteSipUri);
        changeCurrentFragment(FragmentsAvailable.CONTACT_DEVICES, extras);
    }

    public void goToChatMessageImdnInfos(
            String localSipUri, String remoteSipUri, String messageId) {
        Bundle extras = new Bundle();
        extras.putSerializable("LocalSipUri", localSipUri);
        extras.putSerializable("RemoteSipUri", remoteSipUri);
        extras.putString("MessageId", messageId);
        changeCurrentFragment(FragmentsAvailable.MESSAGE_IMDN, extras);
    }

    public void goToChatList() {
        changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        resetSelection();

        if (id == R.id.history) {
            changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
            mHistorySelected.setVisibility(View.VISIBLE);
            LinphoneManager.getLc().resetMissedCallsCount();
            displayMissedCalls(0);
        } else if (id == R.id.contacts) {
            changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, null);
            mContactsSelected.setVisibility(View.VISIBLE);
        } else if (id == R.id.dialer) {
            changeCurrentFragment(FragmentsAvailable.DIALER, null);
            mDialerSelected.setVisibility(View.VISIBLE);
        } else if (id == R.id.chat) {
            changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
            mChatSelected.setVisibility(View.VISIBLE);
        } else if (id == R.id.cancel) {
            if (mCurrentFragment == FragmentsAvailable.SETTINGS_SUBLEVEL && !isTablet()) {
                popBackStack();
            } else {
                hideTopBar();
                displayDialer();
            }
        }
    }

    private void resetSelection() {
        mHistorySelected.setVisibility(View.GONE);
        mContactsSelected.setVisibility(View.GONE);
        mDialerSelected.setVisibility(View.GONE);
        mChatSelected.setVisibility(View.GONE);
    }

    public void hideTabBar(Boolean hide) {
        if (hide && !isTablet()) { // do not hide if tablet, otherwise won't be able to navigate...
            mTabBar.setVisibility(View.GONE);
        } else {
            mTabBar.setVisibility(View.VISIBLE);
        }
    }

    public void hideTopBar() {
        mTopBar.setVisibility(View.GONE);
        mTopBarTitle.setText("");
    }

    private void showTopBar() {
        mTopBar.setVisibility(View.VISIBLE);
    }

    private void showTopBarWithTitle(String title) {
        showTopBar();
        mTopBarTitle.setText(title);
    }

    public void selectMenu(FragmentsAvailable menuToSelect) {
        selectMenu(menuToSelect, null);
    }

    @SuppressWarnings("incomplete-switch")
    public void selectMenu(FragmentsAvailable menuToSelect, String customTitle) {
        mCurrentFragment = menuToSelect;
        resetSelection();
        hideTopBar();
        boolean hideBottomBar =
                getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views);

        switch (menuToSelect) {
            case HISTORY_LIST:
                hideTabBar(false);
                mHistorySelected.setVisibility(View.VISIBLE);
                break;
            case HISTORY_DETAIL:
                hideTabBar(hideBottomBar);
                mHistorySelected.setVisibility(View.VISIBLE);
                break;
            case CONTACTS_LIST:
                hideTabBar(false);
                mContactsSelected.setVisibility(View.VISIBLE);
                break;
            case CONTACT_DETAIL:
            case CONTACT_EDITOR:
                hideTabBar(hideBottomBar);
                mContactsSelected.setVisibility(View.VISIBLE);
                break;
            case DIALER:
                hideTabBar(false);
                mDialerSelected.setVisibility(View.VISIBLE);
                break;
            case SETTINGS:
            case ACCOUNT_SETTINGS:
            case SETTINGS_SUBLEVEL:
                hideTabBar(hideBottomBar);
                if (customTitle == null) {
                    showTopBarWithTitle(getString(R.string.settings));
                } else {
                    showTopBarWithTitle(customTitle);
                }
                break;
            case ABOUT:
                showTopBarWithTitle(getString(R.string.about));
                hideTabBar(hideBottomBar);
                break;
            case CHAT_LIST:
                hideTabBar(false);
                mChatSelected.setVisibility(View.VISIBLE);
                break;
            case CREATE_CHAT:
            case GROUP_CHAT:
            case INFO_GROUP_CHAT:
            case MESSAGE_IMDN:
            case CONTACT_DEVICES:
            case CHAT:
                hideTabBar(hideBottomBar);
                mChatSelected.setVisibility(View.VISIBLE);
                break;
            case RECORDING_LIST:
                hideTabBar(hideBottomBar);
                break;
        }
    }

    public void updateDialerFragment() {
        // Hack to maintain soft input flags
        getWindow()
                .setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void goToDialerFragment() {
        Bundle extras = new Bundle();
        extras.putString("SipUri", "");
        changeCurrentFragment(FragmentsAvailable.DIALER, extras);
        mDialerSelected.setVisibility(View.VISIBLE);
    }

    public void updateStatusFragment(StatusFragment fragment) {
        mStatusFragment = fragment;
    }

    public void displaySettings() {
        changeCurrentFragment(FragmentsAvailable.SETTINGS, null);
    }

    private void displayDialer() {
        changeCurrentFragment(FragmentsAvailable.DIALER, null);
    }

    public void displayAccountSettings(int accountNumber) {
        Bundle bundle = new Bundle();
        bundle.putInt("Account", accountNumber);
        changeCurrentFragment(FragmentsAvailable.ACCOUNT_SETTINGS, bundle);
    }

    public void refreshMissedChatCountDisplay() {
        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
    }

    public void displayMissedCalls(final int missedCallsCount) {
        if (missedCallsCount > 0) {
            mMissedCalls.setText(missedCallsCount + "");
            mMissedCalls.setVisibility(View.VISIBLE);
        } else {
            if (LinphoneManager.isInstanciated()) LinphoneManager.getLc().resetMissedCallsCount();
            mMissedCalls.clearAnimation();
            mMissedCalls.setVisibility(View.GONE);
        }
    }

    public void displayMissedChats(final int missedChatCount) {
        if (missedChatCount > 0) {
            mMissedChats.setText(missedChatCount + "");
            mMissedChats.setVisibility(View.VISIBLE);
        } else {
            mMissedChats.clearAnimation();
            mMissedChats.setVisibility(View.GONE);
        }
        if (mCurrentFragment == FragmentsAvailable.CHAT_LIST
                && mFragment instanceof ChatRoomsFragment) {
            ((ChatRoomsFragment) mFragment).invalidate();
        }
    }

    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public void displayChatRoomError() {
        final Dialog dialog = displayDialog(getString(R.string.chat_room_creation_failed));
        dialog.findViewById(R.id.dialog_delete_button).setVisibility(View.GONE);
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setText(getString(R.string.ok));
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

        dialog.show();
    }

    public Dialog displayDialog(String text) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.dark_grey_color));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView customText = dialog.findViewById(R.id.dialog_message);
        customText.setText(text);
        return dialog;
    }

    public void setAddresGoToDialerAndCall(String number, String name) {
        AddressType address = new AddressText(this, null);
        address.setText(number);
        address.setDisplayedName(name);
        if (!mCallTransfer) {
            LinphoneManager.getInstance().newOutgoingCall(address);
        } else {
            addressWaitingToBeCalled = number;
            displayDialer();
        }
    }

    public void startIncallActivity() {
        Intent intent = new Intent(this, CallActivity.class);
        startOrientationSensor();
        startActivityForResult(intent, CALL_ACTIVITY);
    }

    /** Register a sensor to track phoneOrientation changes */
    private synchronized void startOrientationSensor() {
        if (mOrientationHelper == null) {
            mOrientationHelper = new LocalOrientationEventListener(this);
        }
        mOrientationHelper.enable();
    }

    public Boolean isCallTransfer() {
        return mCallTransfer;
    }

    private void initInCallMenuLayout() {
        selectMenu(FragmentsAvailable.DIALER);
        DialerFragment dialerFragment = DialerFragment.instance();
        if (dialerFragment != null) {
            (dialerFragment).resetLayout();
        }
    }

    public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
        DialerFragment dialerFragment = DialerFragment.instance();
        if (dialerFragment != null) {
            (dialerFragment).resetLayout();
        }

        if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
            Call call = LinphoneManager.getLc().getCalls()[0];
            if (call.getState() == Call.State.IncomingReceived
                    || call.getState() == State.IncomingEarlyMedia) {
                startActivity(new Intent(LinphoneActivity.this, CallIncomingActivity.class));
            } else {
                startIncallActivity();
            }
        }
    }

    public FragmentsAvailable getCurrentFragment() {
        return mCurrentFragment;
    }

    public void addContact(String displayName, String sipUri) {
        Bundle extras = new Bundle();
        extras.putSerializable("NewSipAdress", sipUri);
        extras.putSerializable("NewDisplayName", displayName);
        changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
    }

    public void editContact(LinphoneContact contact, String sipUri) {
        Bundle extras = new Bundle();
        extras.putSerializable("Contact", contact);
        extras.putString("NewSipAdress", sipUri);
        changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
    }

    private void quit() {
        finish();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public boolean checkAndRequestOverlayPermission() {
        Log.i(
                "[Permission] Draw overlays permission is "
                        + (Compatibility.canDrawOverlays(this) ? "granted" : "denied"));
        if (!Compatibility.canDrawOverlays(this)) {
            Log.i("[Permission] Asking for overlay");
            Intent intent =
                    new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSIONS_REQUEST_OVERLAY);
            return false;
        }
        return true;
    }

    public void checkAndRequestExternalStoragePermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 0);
    }

    public void checkAndRequestReadContactsPermission() {
        checkAndRequestPermission(Manifest.permission.READ_CONTACTS, PERMISSIONS_REQUEST_SYNC);
    }

    public void checkAndRequestCameraPermission() {
        checkAndRequestPermission(Manifest.permission.CAMERA, 0);
    }

    public void checkAndRequestRecordAudioPermissionForEchoCanceller() {
        checkAndRequestPermission(
                Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER);
    }

    public void checkAndRequestRecordAudioPermissionsForEchoTester() {
        checkAndRequestPermission(
                Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_TESTER);
    }

    public void checkAndRequestReadExternalStoragePermissionForDeviceRingtone() {
        checkAndRequestPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
    }

    public void checkAndRequestPermissionsToSendImage() {
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        String[] permissionsToHave = {
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA
        };

        for (String permissionToHave : permissionsToHave) {
            if (!checkPermission(permissionToHave)) {
                permissionsToAskFor.add(permissionToHave);
            }
        }

        if (permissionsToAskFor.size() > 0) {
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    private void checkSyncPermission() {
        checkAndRequestPermission(
                Manifest.permission.WRITE_SYNC_SETTINGS, PERMISSIONS_REQUEST_SYNC);
    }

    private boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
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

        if (!checkPermission(permission)) {
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[] {permission}, result);
        }
    }

    public boolean isOnBackground() {
        return mIsOnBackground;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (mCurrentFragment) {
                case DIALER:
                case CONTACTS_LIST:
                case HISTORY_LIST:
                case CHAT_LIST:
                    if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) {
                        return true;
                    }
                    break;
                case GROUP_CHAT:
                    hideTopBar(); // just in case
                    LinphoneActivity.instance().goToChatList();
                    return true;
                case SETTINGS_SUBLEVEL:
                    if (!isTablet()) {
                        popBackStack();
                        return true;
                    }
                case SETTINGS:
                case ACCOUNT_SETTINGS:
                case ABOUT:
                    hideTopBar(); // just in case
                    LinphoneActivity.instance().goToDialerFragment();
                    return true;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // SIDE MENU
    private void openOrCloseSideMenu(boolean open) {
        if (open) {
            mSideMenu.openDrawer(mSideMenuContent);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent);
        }
    }

    private void initSideMenu() {
        mSideMenu = findViewById(R.id.side_menu);
        mSideMenuItems = new ArrayList<>();
        if (getResources().getBoolean(R.bool.show_log_out_in_side_menu)) {
            mSideMenuItems.add(
                    new MenuItem(
                            getResources().getString(R.string.menu_logout),
                            R.drawable.quit_default));
        }
        if (!getResources().getBoolean(R.bool.hide_assistant_from_side_menu)) {
            mSideMenuItems.add(
                    new MenuItem(
                            getResources().getString(R.string.menu_assistant),
                            R.drawable.menu_assistant));
        }
        if (!getResources().getBoolean(R.bool.hide_settings_from_side_menu)) {
            mSideMenuItems.add(
                    new MenuItem(
                            getResources().getString(R.string.menu_settings),
                            R.drawable.menu_options));
        }
        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            mSideMenuItems.add(
                    new MenuItem(
                            getResources().getString(R.string.inapp), R.drawable.menu_options));
        }
        if (!getResources().getBoolean(R.bool.hide_recordings_from_side_menu)) {
            mSideMenuItems.add(
                    new MenuItem(
                            getResources().getString(R.string.menu_recordings),
                            R.drawable.menu_recordings));
        }
        mSideMenuItems.add(
                new MenuItem(getResources().getString(R.string.menu_about), R.drawable.menu_about));
        mSideMenuContent = findViewById(R.id.side_menu_content);
        mSideMenuItemList = findViewById(R.id.item_list);
        mMenu = findViewById(R.id.side_menu_button);

        mSideMenuItemList.setAdapter(
                new MenuAdapter(this, R.layout.side_menu_item_cell, mSideMenuItems));
        mSideMenuItemList.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        String selectedItem = mSideMenuItemList.getAdapter().getItem(i).toString();
                        if (selectedItem.equals(getString(R.string.menu_logout))) {
                            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (lc != null) {
                                lc.setDefaultProxyConfig(null);
                                lc.clearAllAuthInfo();
                                lc.clearProxyConfig();
                                startActivity(
                                        new Intent()
                                                .setClass(
                                                        LinphoneManager.getInstance().getContext(),
                                                        AssistantActivity.class));
                                finish();
                            }
                        } else if (selectedItem.equals(getString(R.string.menu_settings))) {
                            LinphoneActivity.instance().displaySettings();
                        } else if (selectedItem.equals(getString(R.string.menu_about))) {
                            LinphoneActivity.instance().displayAbout();
                        } else if (selectedItem.equals(getString(R.string.menu_assistant))) {
                            LinphoneActivity.instance().displayAssistant();
                        }
                        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
                            if (mSideMenuItemList
                                    .getAdapter()
                                    .getItem(i)
                                    .toString()
                                    .equals(getString(R.string.inapp))) {
                                LinphoneActivity.instance().displayInapp();
                            }
                        }
                        if (mSideMenuItemList
                                .getAdapter()
                                .getItem(i)
                                .toString()
                                .equals(getString(R.string.menu_recordings))) {
                            LinphoneActivity.instance().displayRecordings();
                        }
                        openOrCloseSideMenu(false);
                    }
                });

        initAccounts();

        mMenu.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        if (mSideMenu.isDrawerVisible(Gravity.LEFT)) {
                            mSideMenu.closeDrawer(mSideMenuContent);
                        } else {
                            mSideMenu.openDrawer(mSideMenuContent);
                        }
                    }
                });

        mQuitLayout = findViewById(R.id.side_menu_quit);
        mQuitLayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        LinphoneActivity.instance().quit();
                    }
                });
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            if (state == RegistrationState.Ok) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    private void displayMainAccount() {
        mDefaultAccount.setVisibility(View.VISIBLE);
        ImageView status = mDefaultAccount.findViewById(R.id.main_account_status);
        TextView address = mDefaultAccount.findViewById(R.id.main_account_address);
        TextView displayName = mDefaultAccount.findViewById(R.id.main_account_display_name);

        ProxyConfig proxy = LinphoneManager.getLc().getDefaultProxyConfig();
        if (proxy == null) {
            displayName.setText(getString(R.string.no_account));
            status.setVisibility(View.GONE);
            address.setText("");
            mStatusFragment.resetAccountStatus();

            mDefaultAccount.setOnClickListener(null);
        } else {
            address.setText(proxy.getIdentityAddress().asStringUriOnly());
            displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
            status.setImageResource(getStatusIconResource(proxy.getState()));
            status.setVisibility(View.VISIBLE);

            if (!getResources().getBoolean(R.bool.disable_accounts_settings_from_side_menu)) {
                mDefaultAccount.setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                LinphoneActivity.instance()
                                        .displayAccountSettings(
                                                LinphonePreferences.instance()
                                                        .getDefaultAccountIndex());
                                openOrCloseSideMenu(false);
                            }
                        });
            }
        }
    }

    public void refreshAccounts() {
        if (LinphoneManager.getLc().getProxyConfigList() != null
                && LinphoneManager.getLc().getProxyConfigList().length > 1) {
            mAccountsList.setVisibility(View.VISIBLE);
            mAccountsList.setAdapter(new AccountsListAdapter());
            mAccountsList.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(
                                AdapterView<?> adapterView, View view, int i, long l) {
                            if (view != null && view.getTag() != null) {
                                int position = Integer.parseInt(view.getTag().toString());
                                LinphoneActivity.instance().displayAccountSettings(position);
                            }
                            openOrCloseSideMenu(false);
                        }
                    });
        } else {
            mAccountsList.setVisibility(View.GONE);
        }
        displayMainAccount();
    }

    private void initAccounts() {
        mAccountsList = findViewById(R.id.accounts_list);
        mDefaultAccount = findViewById(R.id.default_account);
    }

    // Inapp Purchase
    private void isTrialAccount() {
        if (LinphoneManager.getLc().getDefaultProxyConfig() != null
                && LinphonePreferences.instance().getInappPopupTime() != null) {
            XmlRpcHelper helper = new XmlRpcHelper();
            helper.isTrialAccountAsync(
                    new XmlRpcListenerBase() {
                        @Override
                        public void onTrialAccountFetched(boolean isTrial) {
                            mIsTrialAccount = isTrial;
                            getExpirationAccount();
                        }

                        @Override
                        public void onError() {}
                    },
                    LinphonePreferences.instance()
                            .getAccountUsername(
                                    LinphonePreferences.instance().getDefaultAccountIndex()),
                    LinphonePreferences.instance()
                            .getAccountHa1(
                                    LinphonePreferences.instance().getDefaultAccountIndex()));
        }
    }

    private void getExpirationAccount() {
        if (LinphoneManager.getLc().getDefaultProxyConfig() != null
                && LinphonePreferences.instance().getInappPopupTime() != null) {
            XmlRpcHelper helper = new XmlRpcHelper();
            helper.getAccountExpireAsync(
                    new XmlRpcListenerBase() {
                        @Override
                        public void onAccountExpireFetched(String result) {
                            if (result != null) {
                                long timestamp = Long.parseLong(result);

                                Calendar calresult = Calendar.getInstance();
                                calresult.setTimeInMillis(timestamp);

                                int diff = getDiffDays(calresult, Calendar.getInstance());
                                if (diff != -1
                                        && diff
                                                <= getResources()
                                                        .getInteger(
                                                                R.integer
                                                                        .days_notification_shown)) {
                                    displayInappNotification(timestampToHumanDate(calresult));
                                }
                            }
                        }

                        @Override
                        public void onError() {}
                    },
                    LinphonePreferences.instance()
                            .getAccountUsername(
                                    LinphonePreferences.instance().getDefaultAccountIndex()),
                    LinphonePreferences.instance()
                            .getAccountHa1(
                                    LinphonePreferences.instance().getDefaultAccountIndex()));
        }
    }

    private void displayInappNotification(String date) {
        Timestamp now = new Timestamp(new Date().getTime());
        if (LinphonePreferences.instance().getInappPopupTime() != null
                && Long.parseLong(LinphonePreferences.instance().getInappPopupTime())
                        > now.getTime()) {
            return;
        } else {
            long newDate =
                    now.getTime()
                            + getResources().getInteger(R.integer.time_between_inapp_notification);
            LinphonePreferences.instance().setInappPopupTime(String.valueOf(newDate));
        }
        if (mIsTrialAccount) {
            LinphoneService.instance()
                    .getNotificationManager()
                    .displayInappNotification(
                            String.format(
                                    getString(R.string.inapp_notification_trial_expire), date));
        } else {
            LinphoneService.instance()
                    .getNotificationManager()
                    .displayInappNotification(
                            String.format(
                                    getString(R.string.inapp_notification_account_expire), date));
        }
    }

    private String timestampToHumanDate(Calendar cal) {
        SimpleDateFormat dateFormat;
        dateFormat =
                new SimpleDateFormat(getResources().getString(R.string.inapp_popup_date_format));
        return dateFormat.format(cal.getTime());
    }

    private int getDiffDays(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return -1;
        }
        if (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA)
                && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
            return cal1.get(Calendar.DAY_OF_YEAR) - cal2.get(Calendar.DAY_OF_YEAR);
        }
        return -1;
    }

    private class LocalOrientationEventListener extends OrientationEventListener {
        LocalOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(final int o) {
            if (o == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;
            }

            int degrees = 270;
            if (o < 45 || o > 315) degrees = 0;
            else if (o < 135) degrees = 90;
            else if (o < 225) degrees = 180;

            if (mAlwaysChangingPhoneAngle == degrees) {
                return;
            }
            mAlwaysChangingPhoneAngle = degrees;

            Log.d("Phone orientation changed to ", degrees);
            int rotation = (360 - degrees) % 360;
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                lc.setDeviceRotation(rotation);
            }
        }
    }

    class AccountsListAdapter extends BaseAdapter {
        List<ProxyConfig> proxy_list;

        AccountsListAdapter() {
            proxy_list = new ArrayList<>();
            refresh();
        }

        void refresh() {
            proxy_list = new ArrayList<>();
            for (ProxyConfig proxyConfig : LinphoneManager.getLc().getProxyConfigList()) {
                if (proxyConfig != LinphoneManager.getLc().getDefaultProxyConfig()) {
                    proxy_list.add(proxyConfig);
                }
            }
        }

        public int getCount() {
            if (proxy_list != null) {
                return proxy_list.size();
            } else {
                return 0;
            }
        }

        public Object getItem(int position) {
            return proxy_list.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(final int position, View convertView, ViewGroup parent) {
            View view;
            ProxyConfig lpc = (ProxyConfig) getItem(position);
            if (convertView != null) {
                view = convertView;
            } else {
                view = getLayoutInflater().inflate(R.layout.side_menu_account_cell, parent, false);
            }

            ImageView status = view.findViewById(R.id.account_status);
            TextView address = view.findViewById(R.id.account_address);
            String sipAddress = lpc.getIdentityAddress().asStringUriOnly();

            address.setText(sipAddress);

            int nbAccounts = LinphonePreferences.instance().getAccountCount();
            int accountIndex;

            for (int i = 0; i < nbAccounts; i++) {
                String username = LinphonePreferences.instance().getAccountUsername(i);
                String domain = LinphonePreferences.instance().getAccountDomain(i);
                String id = "sip:" + username + "@" + domain;
                if (id.equals(sipAddress)) {
                    accountIndex = i;
                    view.setTag(accountIndex);
                    break;
                }
            }
            status.setImageResource(getStatusIconResource(lpc.getState()));
            return view;
        }
    }

    private class MenuItem {
        final String name;
        final int icon;

        MenuItem(String name, int icon) {
            this.name = name;
            this.icon = icon;
        }

        public String toString() {
            return name;
        }
    }

    private class MenuAdapter extends ArrayAdapter<MenuItem> {
        private final List<MenuItem> mItems;
        private final int mResource;

        MenuAdapter(@NonNull Context context, int resource, @NonNull List<MenuItem> objects) {
            super(context, resource, objects);
            mResource = resource;
            mItems = objects;
        }

        @Nullable
        @Override
        public MenuItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView = inflater.inflate(mResource, parent, false);

            TextView textView = rowView.findViewById(R.id.item_name);
            ImageView imageView = rowView.findViewById(R.id.item_icon);

            MenuItem item = getItem(position);
            textView.setText(item.name);
            imageView.setImageResource(item.icon);

            return rowView;
        }
    }
}
