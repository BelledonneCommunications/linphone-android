package org.linphone.activities;

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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
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

import org.linphone.LinphoneManager;
import org.linphone.LinphoneManager.AddressType;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.assistant.AssistantActivity;
import org.linphone.assistant.RemoteProvisioningLoginActivity;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.chat.ChatCreationFragment;
import org.linphone.chat.ChatListFragment;
import org.linphone.chat.GroupChatFragment;
import org.linphone.chat.GroupInfoFragment;
import org.linphone.chat.ImdnFragment;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactAddress;
import org.linphone.contacts.ContactDetailsFragment;
import org.linphone.contacts.ContactEditorFragment;
import org.linphone.contacts.ContactPicked;
import org.linphone.contacts.ContactsListFragment;
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
import org.linphone.fragments.AboutFragment;
import org.linphone.fragments.AccountPreferencesFragment;
import org.linphone.fragments.DialerFragment;
import org.linphone.fragments.EmptyFragment;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.fragments.HistoryDetailFragment;
import org.linphone.fragments.HistoryListFragment;
import org.linphone.fragments.SettingsFragment;
import org.linphone.fragments.StatusFragment;
import org.linphone.mediastream.Log;
import org.linphone.purchase.InAppPurchaseActivity;
import org.linphone.ui.AddressText;
import org.linphone.xmlrpc.XmlRpcHelper;
import org.linphone.xmlrpc.XmlRpcListenerBase;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class LinphoneActivity extends LinphoneGenericActivity implements OnClickListener, ContactPicked, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final int SETTINGS_ACTIVITY = 123;
    private static final int CALL_ACTIVITY = 19;
    private static final int PERMISSIONS_REQUEST_OVERLAY = 206;
    private static final int PERMISSIONS_REQUEST_SYNC = 207;
    private static final int PERMISSIONS_REQUEST_CONTACTS = 208;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER = 209;
    private static final int PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE = 210;
    private static final int PERMISSIONS_RECORD_AUDIO_ECHO_TESTER = 211;

    private static LinphoneActivity instance;

    private StatusFragment statusFragment;
    private TextView missedCalls, missedChats;
    private RelativeLayout contacts, history, dialer, chat;
    private View contacts_selected, history_selected, dialer_selected, chat_selected;
    private RelativeLayout mTopBar;
    private ImageView cancel;
    private FragmentsAvailable pendingFragmentTransaction, currentFragment, leftFragment;
    private Fragment fragment;
    private List<FragmentsAvailable> fragmentsHistory;
    private Fragment.SavedState dialerSavedState;
    private boolean newProxyConfig;
    private boolean emptyFragment = false;
    private boolean isTrialAccount = false;
    private OrientationEventListener mOrientationHelper;
    private CoreListenerStub mListener;
    private LinearLayout mTabBar;

    private DrawerLayout sideMenu;
    private RelativeLayout sideMenuContent, quitLayout, defaultAccount;
    private ListView accountsList, sideMenuItemList;
    private ImageView menu;
    private boolean doNotGoToCallActivity = false;
    private List<String> sideMenuItems;
    private boolean callTransfer = false;
    private boolean isOnBackground = false;

    public String mAddressWaitingToBeCalled;

    static public final boolean isInstanciated() {
        return instance != null;
    }

    public static final LinphoneActivity instance() {
        if (instance != null)
            return instance;
        throw new RuntimeException("LinphoneActivity not instantiated yet");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //This must be done before calling super.onCreate().
        super.onCreate(savedInstanceState);

        if (getResources().getBoolean(R.bool.orientation_portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        boolean useFirstLoginActivity = getResources().getBoolean(R.bool.display_account_assistant_at_first_start);
        if (LinphonePreferences.instance().isProvisioningLoginViewEnabled()) {
            Intent wizard = new Intent();
            wizard.setClass(this, RemoteProvisioningLoginActivity.class);
            wizard.putExtra("Domain", LinphoneManager.getInstance().wizardLoginViewDomain);
            startActivity(wizard);
            finish();
            return;
        } else if (savedInstanceState == null && (useFirstLoginActivity && LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null
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
            if (getPackageManager().checkPermission(Manifest.permission.WRITE_SYNC_SETTINGS, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
                checkSyncPermission();
            } else {
                if (LinphoneService.isReady())
                    ContactsManager.getInstance().initializeSyncAccount(this);
            }
        } else {
            if (LinphoneService.isReady())
                ContactsManager.getInstance().initializeContactManager(this);
        }

        setContentView(R.layout.main);
        instance = this;
        fragmentsHistory = new ArrayList<>();
        pendingFragmentTransaction = FragmentsAvailable.UNKNOW;

        initButtons();
        initSideMenu();

        currentFragment = FragmentsAvailable.EMPTY;
        if (savedInstanceState == null) {
            changeCurrentFragment(FragmentsAvailable.DIALER, getIntent().getExtras());
        } else {
            currentFragment = (FragmentsAvailable) savedInstanceState.getSerializable("currentFragment");
        }

        mListener = new CoreListenerStub() {
            @Override
            public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
                displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
            }

            @Override
            public void onRegistrationStateChanged(Core lc, ProxyConfig proxy, RegistrationState state, String smessage) {
                AuthInfo authInfo = lc.findAuthInfo(proxy.getRealm(), proxy.getIdentityAddress().getUsername(), proxy.getDomain());

                refreshAccounts();

                if (getResources().getBoolean(R.bool.use_phone_number_validation)
                        && authInfo != null && authInfo.getDomain().equals(getString(R.string.default_domain))) {
                    if (state.equals(RegistrationState.Ok)) {
                        LinphoneManager.getInstance().isAccountWithAlias();
                    }
                }

                if (state.equals(RegistrationState.Failed) && newProxyConfig) {
                    newProxyConfig = false;
                    if (proxy.getError() == Reason.Forbidden) {
                        //displayCustomToast(getString(R.string.error_bad_credentials), Toast.LENGTH_LONG);
                    }
                    if (proxy.getError() == Reason.Unauthorized) {
                        displayCustomToast(getString(R.string.error_unauthorized), Toast.LENGTH_LONG);
                    }
                    if (proxy.getError() == Reason.IOError) {
                        displayCustomToast(getString(R.string.error_io_error), Toast.LENGTH_LONG);
                    }
                }
            }

            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (state == State.IncomingReceived) {
                    startActivity(new Intent(LinphoneActivity.instance(), CallIncomingActivity.class));
                } else if (state == State.OutgoingInit || state == State.OutgoingProgress) {
                    startActivity(new Intent(LinphoneActivity.instance(), CallOutgoingActivity.class));
                } else if (state == State.End || state == State.Error || state == State.Released) {
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

        if (LinphoneManager.isInstanciated()) {
            LinphoneManager.getLc().setDeviceRotation(rotation);
        }
        mAlwaysChangingPhoneAngle = rotation;

        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean("GoToChat", false)) {
            onNewIntent(getIntent());
        }
    }

    private void initButtons() {
        mTabBar = findViewById(R.id.footer);
        mTopBar = findViewById(R.id.top_bar);

        cancel = findViewById(R.id.cancel);
        cancel.setOnClickListener(this);

        history = findViewById(R.id.history);
        history.setOnClickListener(this);
        contacts = findViewById(R.id.contacts);
        contacts.setOnClickListener(this);
        dialer = findViewById(R.id.dialer);
        dialer.setOnClickListener(this);
        chat = findViewById(R.id.chat);
        chat.setOnClickListener(this);

        history_selected = findViewById(R.id.history_select);
        contacts_selected = findViewById(R.id.contacts_select);
        dialer_selected = findViewById(R.id.dialer_select);
        chat_selected = findViewById(R.id.chat_select);

        missedCalls = findViewById(R.id.missed_calls);
        missedChats = findViewById(R.id.missed_chats);
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

        if (statusFragment != null && !statusFragment.isVisible()) {
            statusFragment.getView().setVisibility(View.VISIBLE);
        }
        findViewById(R.id.status).setVisibility(View.VISIBLE);
    }

    public void isNewProxyConfig() {
        newProxyConfig = true;
    }

    public void popBackStack() {
        getFragmentManager().popBackStackImmediate();
        currentFragment = FragmentsAvailable.EMPTY;
    }

    private void changeCurrentFragment(FragmentsAvailable newFragmentType, Bundle extras) {
        if (newFragmentType == currentFragment && newFragmentType != FragmentsAvailable.CHAT
                && newFragmentType != FragmentsAvailable.GROUP_CHAT) {
            return;
        }

        if (currentFragment == FragmentsAvailable.DIALER) {
            try {
                DialerFragment dialerFragment = DialerFragment.instance();
                dialerSavedState = getFragmentManager().saveFragmentInstanceState(dialerFragment);
            } catch (Exception e) {
            }
        }

        fragment = null;

        switch (newFragmentType) {
            case HISTORY_LIST:
                fragment = new HistoryListFragment();
                break;
            case HISTORY_DETAIL:
                fragment = new HistoryDetailFragment();
                break;
            case CONTACTS_LIST:
                checkAndRequestWriteContactsPermission();
                fragment = new ContactsListFragment();
                break;
            case CONTACT_DETAIL:
                fragment = new ContactDetailsFragment();
                break;
            case CONTACT_EDITOR:
                fragment = new ContactEditorFragment();
                break;
            case DIALER:
                fragment = new DialerFragment();
                if (extras == null) {
                    fragment.setInitialSavedState(dialerSavedState);
                }
                break;
            case SETTINGS:
                fragment = new SettingsFragment();
                break;
            case ACCOUNT_SETTINGS:
                fragment = new AccountPreferencesFragment();
                break;
            case ABOUT:
                fragment = new AboutFragment();
                break;
            case EMPTY:
                fragment = new EmptyFragment();
                break;
            case CHAT_LIST:
                fragment = new ChatListFragment();
                break;
            case CREATE_CHAT:
                checkAndRequestWriteContactsPermission();
                fragment = new ChatCreationFragment();
                break;
            case INFO_GROUP_CHAT:
                fragment = new GroupInfoFragment();
                break;
            case GROUP_CHAT:
                fragment = new GroupChatFragment();
                break;
            case MESSAGE_IMDN:
                fragment = new ImdnFragment();
                break;
            default:
                break;
        }

        if (fragment != null) {
            fragment.setArguments(extras);
            if (isTablet()) {
                changeFragmentForTablets(fragment, newFragmentType);
                switch (newFragmentType) {
                    case HISTORY_LIST:
                        ((HistoryListFragment) fragment).displayFirstLog();
                        break;
                    case CONTACTS_LIST:
                        ((ContactsListFragment) fragment).displayFirstContact();
                        break;
                    case CHAT_LIST:
                        ((ChatListFragment) fragment).displayFirstChat();
                        break;
                }
            } else {
                changeFragment(fragment, newFragmentType);
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

        currentFragment = newFragmentType;
    }

    private void changeFragmentForTablets(Fragment newFragment, FragmentsAvailable newFragmentType) {
        if (getResources().getBoolean(R.bool.show_statusbar_only_on_dialer)) {
            if (newFragmentType == FragmentsAvailable.DIALER) {
                showStatusBar();
            } else {
                hideStatusBar();
            }
        }
        emptyFragment = false;
        LinearLayout ll = findViewById(R.id.fragmentContainer2);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        if (newFragmentType == FragmentsAvailable.EMPTY) {
            ll.setVisibility(View.VISIBLE);
            emptyFragment = true;
            transaction.replace(R.id.fragmentContainer2, newFragment);
            transaction.commitAllowingStateLoss();
            getFragmentManager().executePendingTransactions();
        } else {
            if (newFragmentType.shouldAddItselfToTheRightOf(currentFragment) || newFragmentType.shouldAddItselfToTheRightOf(leftFragment)) {
                ll.setVisibility(View.VISIBLE);

                if (newFragmentType == FragmentsAvailable.CONTACT_EDITOR) {
                    transaction.addToBackStack(newFragmentType.toString());
                }
                transaction.replace(R.id.fragmentContainer2, newFragment);
                leftFragment = currentFragment;

                if (newFragmentType == FragmentsAvailable.GROUP_CHAT && leftFragment != FragmentsAvailable.CHAT_LIST) {
                    leftFragment = FragmentsAvailable.CHAT_LIST;
                    transaction.replace(R.id.fragmentContainer, new ChatListFragment());
                }
            } else {
                if (newFragmentType == FragmentsAvailable.EMPTY) {
                    ll.setVisibility(View.VISIBLE);
                    transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
                    emptyFragment = true;
                }

                if (newFragmentType == FragmentsAvailable.DIALER
                        || newFragmentType == FragmentsAvailable.ABOUT
                        || newFragmentType == FragmentsAvailable.SETTINGS
                        || newFragmentType == FragmentsAvailable.ACCOUNT_SETTINGS
                        || newFragmentType == FragmentsAvailable.CREATE_CHAT
                        || newFragmentType == FragmentsAvailable.INFO_GROUP_CHAT) {
                    ll.setVisibility(View.GONE);
                } else {
                    ll.setVisibility(View.VISIBLE);
                    transaction.replace(R.id.fragmentContainer2, new EmptyFragment());
                }

				/*if (!withoutAnimation && !isAnimationDisabled && currentFragment.shouldAnimate()) {
					if (newFragmentType.isRightOf(currentFragment)) {
						transaction.setCustomAnimations(R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left, R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right);
					} else {
						transaction.setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_left_to_right, R.anim.slide_in_right_to_left, R.anim.slide_out_right_to_left);
					}
				}*/
                transaction.replace(R.id.fragmentContainer, newFragment);
            }
            transaction.commitAllowingStateLoss();
            getFragmentManager().executePendingTransactions();

            currentFragment = newFragmentType;
            if (newFragmentType == FragmentsAvailable.DIALER
                    || newFragmentType == FragmentsAvailable.SETTINGS
                    || newFragmentType == FragmentsAvailable.CONTACTS_LIST
                    || newFragmentType == FragmentsAvailable.CHAT_LIST
                    || newFragmentType == FragmentsAvailable.HISTORY_LIST) {
                try {
                    getFragmentManager().popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                } catch (java.lang.IllegalStateException e) {

                }
            }
            fragmentsHistory.add(currentFragment);
        }
    }

    public void displayHistoryDetail(String sipUri, CallLog log) {
        Address lAddress = null;
        LinphoneContact c = null;

        lAddress = Factory.instance().createAddress(sipUri);
        if (lAddress != null) {
            c = ContactsManager.getInstance().findContactFromAddress(lAddress);
        }

        String displayName = c != null ? c.getFullName() : LinphoneUtils.getAddressDisplayName(sipUri);
        String pictureUri = c != null && c.getPhotoUri() != null ? c.getPhotoUri().toString() : null;

        String status;
        if (log.getDir() == Call.Dir.Outgoing) {
            status = getString(R.string.outgoing);
        } else {
            if (log.getStatus() == Call.Status.Missed) {
                status = getString(R.string.missed);
            } else {
                status = getString(R.string.incoming);
            }
        }

        String callTime = secondsToDisplayableString(log.getDuration());
        String callDate = String.valueOf(log.getStartDate());

        Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
        if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.HISTORY_DETAIL) {
            HistoryDetailFragment historyDetailFragment = (HistoryDetailFragment) fragment2;
            historyDetailFragment.changeDisplayedHistory(sipUri, displayName, pictureUri, status, callTime, callDate);
        } else {
            Bundle extras = new Bundle();
            extras.putString("SipUri", sipUri);
            if (displayName != null) {
                extras.putString("DisplayName", displayName);
                extras.putString("PictureUri", pictureUri);
            }
            extras.putString("Call.Status", status);
            extras.putString("CallTime", callTime);
            extras.putString("CallDate", callDate);

            changeCurrentFragment(FragmentsAvailable.HISTORY_DETAIL, extras);
        }
    }

    public void displayEmptyFragment() {
        changeCurrentFragment(FragmentsAvailable.EMPTY, new Bundle());
    }

    @SuppressLint("SimpleDateFormat")
    private String secondsToDisplayableString(int secs) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        Calendar cal = Calendar.getInstance();
        cal.set(0, 0, 0, 0, 0, secs);
        return dateFormat.format(cal.getTime());
    }

    public void displayContact(LinphoneContact contact, boolean chatOnly) {
        Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
        if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.CONTACT_DETAIL) {
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

    public void displayAbout() {
        changeCurrentFragment(FragmentsAvailable.ABOUT, null);
    }

    public void displayContactsForEdition(String sipAddress, String displayName) {
        Bundle extras = new Bundle();
        extras.putBoolean("EditOnClick", true);
        extras.putString("SipAddress", sipAddress);
        extras.putString("DisplayName", displayName);
        changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, extras);
    }

    public void displayAssistant() {
        startActivity(new Intent(LinphoneActivity.this, AssistantActivity.class));
    }


    public void displayInapp() {
        startActivity(new Intent(LinphoneActivity.this, InAppPurchaseActivity.class));
    }

    private void displayChat(String sipUri, String message, String fileUri, String pictureUri, String thumbnailUri, String displayName, Address lAddress) {
        Bundle extras = new Bundle();
        extras.putString("SipUri", sipUri);

        if (message != null)
            extras.putString("messageDraft", message);
        if (fileUri != null)
            extras.putString("fileSharedUri", fileUri);
        if (sipUri != null && lAddress.getDisplayName() != null) {
            extras.putString("DisplayName", displayName);
            extras.putString("PictureUri", pictureUri);
            extras.putString("ThumbnailUri", thumbnailUri);
        }

        if (sipUri == null) {
            changeCurrentFragment(FragmentsAvailable.CREATE_CHAT, extras);
        } else {
            changeCurrentFragment(FragmentsAvailable.GROUP_CHAT, extras);
        }
    }

    public void goToChatCreator(String address, ArrayList<ContactAddress> selectedContacts, String subject, boolean isGoBack, Bundle shareInfos) {
        if (currentFragment == FragmentsAvailable.INFO_GROUP_CHAT && isGoBack) {
            getFragmentManager().popBackStackImmediate();
            getFragmentManager().popBackStackImmediate();
        }
        Bundle extras = new Bundle();
        extras.putSerializable("selectedContacts", selectedContacts);
        extras.putString("subject", subject);
        extras.putString("groupChatRoomAddress", address);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        changeCurrentFragment(FragmentsAvailable.CREATE_CHAT, extras);
    }

    public void goToChat(String sipUri, Bundle shareInfos) {
        Bundle extras = new Bundle();
        extras.putString("SipUri", sipUri);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        if (isTablet()) {
            Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
            if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.GROUP_CHAT && !emptyFragment) {
                GroupChatFragment chatFragment = (GroupChatFragment) fragment2;
                chatFragment.changeDisplayedChat(sipUri);
            } else {
                changeCurrentFragment(FragmentsAvailable.GROUP_CHAT, extras);
            }
        } else {
            changeCurrentFragment(FragmentsAvailable.GROUP_CHAT, extras);
        }

        LinphoneManager.getInstance().updateUnreadCountForChatRoom(sipUri, 0);
        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
    }

    public void goToChatGroupInfos(String address, ArrayList<ContactAddress> contacts, String subject, boolean isEditionEnabled, boolean isGoBack, Bundle shareInfos) {
        if (currentFragment == FragmentsAvailable.CREATE_CHAT && isGoBack) {
            getFragmentManager().popBackStackImmediate();
            getFragmentManager().popBackStackImmediate();
        }
        Bundle extras = new Bundle();
        extras.putString("groupChatRoomAddress", address);
        extras.putBoolean("isEditionEnabled", isEditionEnabled);
        extras.putSerializable("ContactAddress", contacts);
        extras.putString("subject", subject);

        if (shareInfos != null) {
            if (shareInfos.getString("fileSharedUri") != null)
                extras.putString("fileSharedUri", shareInfos.getString("fileSharedUri"));
            if (shareInfos.getString("messageDraft") != null)
                extras.putString("messageDraft", shareInfos.getString("messageDraft"));
        }

        changeCurrentFragment(FragmentsAvailable.INFO_GROUP_CHAT, extras);
    }

    public void goToChatMessageImdnInfos(String sipUri, String messageId) {
        Bundle extras = new Bundle();
        extras.putSerializable("SipUri", sipUri);
        extras.putString("MessageId", messageId);
        changeCurrentFragment(FragmentsAvailable.MESSAGE_IMDN, extras);
    }

    public void goToChatList() {
        changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
    }

    public void displayChat(String sipUri, String message, String fileUri) {
        if (getResources().getBoolean(R.bool.disable_chat)) {
            return;
        }

        String pictureUri = null;
        String thumbnailUri = null;
        String displayName = null;

        Address lAddress = null;
        if (sipUri != null) {
            lAddress = LinphoneManager.getLc().interpretUrl(sipUri);
            if (lAddress == null) return;
            LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(lAddress);
            displayName = contact != null ? contact.getFullName() : null;

            if (contact != null && contact.getPhotoUri() != null) {
                pictureUri = contact.getPhotoUri().toString();
                thumbnailUri = contact.getThumbnailUri().toString();
            }
        }

        if (currentFragment == FragmentsAvailable.CHAT_LIST || currentFragment == FragmentsAvailable.GROUP_CHAT) {
            Fragment fragment2 = getFragmentManager().findFragmentById(R.id.fragmentContainer2);
            if (fragment2 != null && fragment2.isVisible() && currentFragment == FragmentsAvailable.GROUP_CHAT && !emptyFragment) {
                GroupChatFragment chatFragment = (GroupChatFragment) fragment2;
                chatFragment.changeDisplayedChat(sipUri);
            } else {
                displayChat(sipUri, message, fileUri, pictureUri, thumbnailUri, displayName, lAddress);
            }
        } else {
            if (isTablet()) {
                changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
            } else {
                displayChat(sipUri, message, fileUri, pictureUri, thumbnailUri, displayName, lAddress);
            }
        }

        LinphoneManager.getInstance().updateUnreadCountForChatRoom(sipUri, 0);
        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        resetSelection();

        if (id == R.id.history) {
            changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
            history_selected.setVisibility(View.VISIBLE);
            LinphoneManager.getLc().resetMissedCallsCount();
            displayMissedCalls(0);
        } else if (id == R.id.contacts) {
            changeCurrentFragment(FragmentsAvailable.CONTACTS_LIST, null);
            contacts_selected.setVisibility(View.VISIBLE);
        } else if (id == R.id.dialer) {
            changeCurrentFragment(FragmentsAvailable.DIALER, null);
            dialer_selected.setVisibility(View.VISIBLE);
        } else if (id == R.id.chat) {
            changeCurrentFragment(FragmentsAvailable.CHAT_LIST, null);
            chat_selected.setVisibility(View.VISIBLE);
        } else if (id == R.id.cancel) {
            hideTopBar();
            displayDialer();
        }
    }

    private void resetSelection() {
        history_selected.setVisibility(View.GONE);
        contacts_selected.setVisibility(View.GONE);
        dialer_selected.setVisibility(View.GONE);
        chat_selected.setVisibility(View.GONE);
    }

    public void hideTabBar(Boolean hide) {
        if (hide) {
            mTabBar.setVisibility(View.GONE);
        } else {
            mTabBar.setVisibility(View.VISIBLE);
        }
    }

    public void hideTopBar() {
        mTopBar.setVisibility(View.GONE);
    }

    @SuppressWarnings("incomplete-switch")
    public void selectMenu(FragmentsAvailable menuToSelect) {
        currentFragment = menuToSelect;
        resetSelection();

        switch (menuToSelect) {
            case HISTORY_LIST:
            case HISTORY_DETAIL:
                history_selected.setVisibility(View.VISIBLE);
                break;
            case CONTACTS_LIST:
            case CONTACT_DETAIL:
            case CONTACT_EDITOR:
                contacts_selected.setVisibility(View.VISIBLE);
                break;
            case DIALER:
                dialer_selected.setVisibility(View.VISIBLE);
                break;
            case SETTINGS:
            case ACCOUNT_SETTINGS:
                hideTabBar(true);
                mTopBar.setVisibility(View.VISIBLE);
                break;
            case ABOUT:
                hideTabBar(true);
                break;
            case CHAT_LIST:
            case CREATE_CHAT:
            case GROUP_CHAT:
            case INFO_GROUP_CHAT:
            case MESSAGE_IMDN:
            case CHAT:
                chat_selected.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void updateDialerFragment(DialerFragment fragment) {
        // Hack to maintain soft input flags
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    public void goToDialerFragment() {
        changeCurrentFragment(FragmentsAvailable.DIALER, null);
        dialer_selected.setVisibility(View.VISIBLE);
    }

    public void updateStatusFragment(StatusFragment fragment) {
        statusFragment = fragment;
    }

    public void displaySettings() {
        changeCurrentFragment(FragmentsAvailable.SETTINGS, null);
    }

    public void displayDialer() {
        changeCurrentFragment(FragmentsAvailable.DIALER, null);
    }

    public void displayAccountSettings(int accountNumber) {
        Bundle bundle = new Bundle();
        bundle.putInt("Account", accountNumber);
        changeCurrentFragment(FragmentsAvailable.ACCOUNT_SETTINGS, bundle);
        //settings.setSelected(true);
    }

    public StatusFragment getStatusFragment() {
        return statusFragment;
    }

    public void refreshMissedChatCountDisplay() {
        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
    }

    public void displayMissedCalls(final int missedCallsCount) {
        if (missedCallsCount > 0) {
            missedCalls.setText(missedCallsCount + "");
            missedCalls.setVisibility(View.VISIBLE);
        } else {
            if (LinphoneManager.isInstanciated()) LinphoneManager.getLc().resetMissedCallsCount();
            missedCalls.clearAnimation();
            missedCalls.setVisibility(View.GONE);
        }
    }

    private void displayMissedChats(final int missedChatCount) {
        if (missedChatCount > 0) {
            missedChats.setText(missedChatCount + "");
            missedChats.setVisibility(View.VISIBLE);
        } else {
            missedChats.clearAnimation();
            missedChats.setVisibility(View.GONE);
        }
    }

    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    public void displayChatRoomError() {
        final Dialog dialog = LinphoneActivity.instance().displayDialog(getString(R.string.chat_room_creation_failed));
        Button delete = dialog.findViewById(R.id.delete_button);
        Button cancel = dialog.findViewById(R.id.cancel);
        delete.setVisibility(View.GONE);
        cancel.setText(getString(R.string.ok));
        cancel.setOnClickListener(new View.OnClickListener() {
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
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.colorC));
        d.setAlpha(200);
        dialog.setContentView(R.layout.dialog);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.getWindow().setBackgroundDrawable(d);

        TextView customText = (TextView) dialog.findViewById(R.id.customText);
        customText.setText(text);
        return dialog;
    }

    @Override
    public void setAddresGoToDialerAndCall(String number, String name, Uri photo) {
//		Bundle extras = new Bundle();
//		extras.putString("SipUri", number);
//		extras.putString("DisplayName", name);
//		extras.putString("Photo", photo == null ? null : photo.toString());
//		changeCurrentFragment(FragmentsAvailable.DIALER, extras);

        AddressType address = new AddressText(this, null);
        address.setText(number);
        address.setDisplayedName(name);
        LinphoneManager.getInstance().newOutgoingCall(address);
    }

    public void startIncallActivity(Call currentCall) {
        Intent intent = new Intent(this, CallActivity.class);
        startOrientationSensor();
        startActivityForResult(intent, CALL_ACTIVITY);
    }

    /**
     * Register a sensor to track phoneOrientation changes
     */
    private synchronized void startOrientationSensor() {
        if (mOrientationHelper == null) {
            mOrientationHelper = new LocalOrientationEventListener(this);
        }
        mOrientationHelper.enable();
    }

    private int mAlwaysChangingPhoneAngle = -1;

    private class LocalOrientationEventListener extends OrientationEventListener {
        public LocalOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(final int o) {
            if (o == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;
            }

            int degrees = 270;
            if (o < 45 || o > 315)
                degrees = 0;
            else if (o < 135)
                degrees = 90;
            else if (o < 225)
                degrees = 180;

            if (mAlwaysChangingPhoneAngle == degrees) {
                return;
            }
            mAlwaysChangingPhoneAngle = degrees;

            Log.d("Phone orientation changed to ", degrees);
            int rotation = (360 - degrees) % 360;
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (lc != null) {
                lc.setDeviceRotation(rotation);
                Call currentCall = lc.getCurrentCall();
                if (currentCall != null && currentCall.cameraEnabled() && currentCall.getCurrentParams().videoEnabled()) {
                    lc.updateCall(currentCall, null);
                }
            }
        }
    }

    public Boolean isCallTransfer() {
        return callTransfer;
    }

    private void initInCallMenuLayout(final boolean callTransfer) {
        selectMenu(FragmentsAvailable.DIALER);
        DialerFragment dialerFragment = DialerFragment.instance();
        if (dialerFragment != null) {
            ((DialerFragment) dialerFragment).resetLayout(callTransfer);
        }
    }

    public void resetClassicMenuLayoutAndGoBackToCallIfStillRunning() {
        DialerFragment dialerFragment = DialerFragment.instance();
        if (dialerFragment != null) {
            ((DialerFragment) dialerFragment).resetLayout(true);
        }

        if (LinphoneManager.isInstanciated() && LinphoneManager.getLc().getCallsNb() > 0) {
            Call call = LinphoneManager.getLc().getCalls()[0];
            if (call.getState() == Call.State.IncomingReceived) {
                startActivity(new Intent(LinphoneActivity.this, CallIncomingActivity.class));
            } else {
                startIncallActivity(call);
            }
        }
    }

    public FragmentsAvailable getCurrentFragment() {
        return currentFragment;
    }

    public void addContact(String displayName, String sipUri) {
        Bundle extras = new Bundle();
        extras.putSerializable("NewSipAdress", sipUri);
        extras.putSerializable("NewDisplayName", displayName);
        changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
    }

    public void editContact(LinphoneContact contact) {
        Bundle extras = new Bundle();
        extras.putSerializable("Contact", contact);
        changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
    }

    public void editContact(LinphoneContact contact, String sipAddress) {
        Bundle extras = new Bundle();
        extras.putSerializable("Contact", contact);
        extras.putSerializable("NewSipAdress", sipAddress);
        changeCurrentFragment(FragmentsAvailable.CONTACT_EDITOR, extras);
    }

    public void quit() {
        finish();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (pendingFragmentTransaction != FragmentsAvailable.UNKNOW) {
            changeCurrentFragment(pendingFragmentTransaction, null);
            selectMenu(pendingFragmentTransaction);
            pendingFragmentTransaction = FragmentsAvailable.UNKNOW;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (getIntent() != null && getIntent().getExtras() != null) {
            newProxyConfig = getIntent().getExtras().getBoolean("isNewProxyConfig");
        }

        if (resultCode == Activity.RESULT_FIRST_USER && requestCode == SETTINGS_ACTIVITY) {
            if (data.getExtras().getBoolean("Exit", false)) {
                quit();
            } else {
                pendingFragmentTransaction = (FragmentsAvailable) data.getExtras().getSerializable("FragmentToDisplay");
            }
        } else if (resultCode == Activity.RESULT_FIRST_USER && requestCode == CALL_ACTIVITY) {
            getIntent().putExtra("PreviousActivity", CALL_ACTIVITY);
            callTransfer = data != null && data.getBooleanExtra("Transfer", false);
            boolean chat = data != null && data.getBooleanExtra("chat", false);
            if (chat) {
                pendingFragmentTransaction = FragmentsAvailable.CHAT_LIST;
            }
            if (LinphoneManager.getLc().getCallsNb() > 0) {
                initInCallMenuLayout(callTransfer);
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
    protected void onPause() {
        getIntent().putExtra("PreviousActivity", 0);

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        callTransfer = false;
        isOnBackground = true;

        super.onPause();
    }

    public boolean checkAndRequestOverlayPermission() {
        Log.i("[Permission] Draw overlays permission is " + (Compatibility.canDrawOverlays(this) ? "granted" : "denied"));
        if (!Compatibility.canDrawOverlays(this)) {
            Log.i("[Permission] Asking for overlay");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, PERMISSIONS_REQUEST_OVERLAY);
            return false;
        }
        return true;
    }

    public void checkAndRequestExternalStoragePermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 0);
    }

    public void checkAndRequestCameraPermission() {
        checkAndRequestPermission(Manifest.permission.CAMERA, 0);
    }

    public void checkAndRequestWriteContactsPermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_CONTACTS, 0);
    }

    public void checkAndRequestRecordAudioPermissionForEchoCanceller() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER);
    }

    public void checkAndRequestRecordAudioPermissionsForEchoTester() {
        checkAndRequestPermission(Manifest.permission.RECORD_AUDIO, PERMISSIONS_RECORD_AUDIO_ECHO_TESTER);
    }

    public void checkAndRequestReadExternalStoragePermissionForDeviceRingtone() {
        checkAndRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
    }

    public void checkAndRequestPermissionsToSendImage() {
        ArrayList<String> permissionsList = new ArrayList<>();

        int readExternalStorage = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
        Log.i("[Permission] Read external storage permission is " + (readExternalStorage == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        int camera = getPackageManager().checkPermission(Manifest.permission.CAMERA, getPackageName());
        Log.i("[Permission] Camera permission is " + (camera == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (readExternalStorage != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            Log.i("[Permission] Asking for read external storage");
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (camera != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA);
            Log.i("[Permission] Asking for camera");
            permissionsList.add(Manifest.permission.CAMERA);
        }
        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    private void checkSyncPermission() {
        checkAndRequestPermission(Manifest.permission.WRITE_SYNC_SETTINGS, PERMISSIONS_REQUEST_SYNC);
    }

    public void checkAndRequestPermission(String permission, int result) {
        int permissionGranted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i("[Permission] " + permission + " is " + (permissionGranted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (permissionGranted != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
            Log.i("[Permission] Asking for " + permission);
            ActivityCompat.requestPermissions(this, new String[]{permission}, result);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0)
            return;

        int readContactsI = -1;
        for (int i = 0; i < permissions.length; i++) {
            Log.i("[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
            if (permissions[i].compareTo(Manifest.permission.READ_CONTACTS) == 0 ||
                    permissions[i].compareTo(Manifest.permission.WRITE_CONTACTS) == 0)
                readContactsI = i;
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_SYNC:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ContactsManager.getInstance().initializeSyncAccount(this);
                } else {
                    ContactsManager.getInstance().initializeContactManager(this);
                }
                break;
            case PERMISSIONS_RECORD_AUDIO_ECHO_CANCELLER:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ((SettingsFragment) fragment).startEchoCancellerCalibration();
                } else {
                    ((SettingsFragment) fragment).echoCalibrationFail();
                }
                break;
            case PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE:
                if (permissions[0].compareTo(Manifest.permission.READ_EXTERNAL_STORAGE) != 0)
                    break;
                boolean enableRingtone = (grantResults[0] == PackageManager.PERMISSION_GRANTED);
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
                break;
            case PERMISSIONS_RECORD_AUDIO_ECHO_TESTER:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    ((SettingsFragment) fragment).startEchoTester();
                break;
        }
        if (readContactsI >= 0 && grantResults[readContactsI] == PackageManager.PERMISSION_GRANTED) {
            ContactsManager.getInstance().enableContactsAccess();
            if (!ContactsManager.getInstance().contactsFetchedOnce()) {
                ContactsManager.getInstance().initializeContactManager(this);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ArrayList<String> permissionsList = new ArrayList<>();

        int contacts = getPackageManager().checkPermission(Manifest.permission.READ_CONTACTS, getPackageName());
        Log.i("[Permission] Contacts permission is " + (contacts == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        int readPhone = getPackageManager().checkPermission(Manifest.permission.READ_PHONE_STATE, getPackageName());
        Log.i("[Permission] Read phone state permission is " + (readPhone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        int ringtone = getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getPackageName());
        Log.i("[Permission] Read external storage for ring tone permission is " + (ringtone == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));

        if (ringtone != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Log.i("[Permission] Asking for read external storage for ring tone");
                permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
        if (readPhone != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_PHONE_STATE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                Log.i("[Permission] Asking for read phone state");
                permissionsList.add(Manifest.permission.READ_PHONE_STATE);
            }
        }
        if (contacts != PackageManager.PERMISSION_GRANTED) {
            if (LinphonePreferences.instance().firstTimeAskingForPermission(Manifest.permission.READ_CONTACTS) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
                Log.i("[Permission] Asking for contacts");
                permissionsList.add(Manifest.permission.READ_CONTACTS);
            }
        } else {
            if (!ContactsManager.getInstance().contactsFetchedOnce()) {
                ContactsManager.getInstance().enableContactsAccess();
                ContactsManager.getInstance().fetchContactsAsync();
            }
        }
        // This one is to allow floating notifications
        permissionsList.add(Manifest.permission.SYSTEM_ALERT_WINDOW);

        if (permissionsList.size() > 0) {
            String[] permissions = new String[permissionsList.size()];
            permissions = permissionsList.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_READ_EXTERNAL_STORAGE_DEVICE_RINGTONE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("currentFragment", currentFragment);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    public void disableGoToCall() {
        doNotGoToCallActivity = true;
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
            if (!LinphoneService.instance().displayServiceNotification()) {
                lc.refreshRegisters();
            }
        }

        if (isTablet()) {
            // Prevent fragmentContainer2 to be visible when rotating the device
            LinearLayout ll = findViewById(R.id.fragmentContainer2);
            if (currentFragment == FragmentsAvailable.DIALER
                    || currentFragment == FragmentsAvailable.ABOUT
                    || currentFragment == FragmentsAvailable.SETTINGS
                    || currentFragment == FragmentsAvailable.ACCOUNT_SETTINGS) {
                ll.setVisibility(View.GONE);
            }
        }

        refreshAccounts();

        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            isTrialAccount();
        }

        displayMissedChats(LinphoneManager.getInstance().getUnreadMessageCount());
        displayMissedCalls(LinphoneManager.getLc().getMissedCallsCount());

        LinphoneManager.getInstance().changeStatusToOnline();

        if (getIntent().getIntExtra("PreviousActivity", 0) != CALL_ACTIVITY && !doNotGoToCallActivity) {
            if (LinphoneManager.getLc().getCalls().length > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];
                Call.State onCallStateChanged = call.getState();

                if (onCallStateChanged == State.IncomingReceived) {
                    startActivity(new Intent(this, CallIncomingActivity.class));
                } else if (onCallStateChanged == State.OutgoingInit || onCallStateChanged == State.OutgoingProgress || onCallStateChanged == State.OutgoingRinging) {
                    startActivity(new Intent(this, CallOutgoingActivity.class));
                } else {
                    startIncallActivity(call);
                }
            }
        }

        Intent intent = getIntent();

        if (intent.getStringExtra("msgShared") != null) {
            displayChat(null, intent.getStringExtra("msgShared"), null);
            intent.putExtra("msgShared", "");
        }
        if (intent.getStringExtra("fileShared") != null && intent.getStringExtra("fileShared") != "") {
            displayChat(null, null, intent.getStringExtra("fileShared"));
            intent.putExtra("fileShared", "");
        }
        doNotGoToCallActivity = false;
        isOnBackground = false;

        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey("SipUriOrNumber")) {
                mAddressWaitingToBeCalled = extras.getString("SipUriOrNumber");
                intent.removeExtra("SipUriOrNumber");
                goToDialerFragment();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mOrientationHelper != null) {
            mOrientationHelper.disable();
            mOrientationHelper = null;
        }

        instance = null;
        super.onDestroy();

        unbindDrawables(findViewById(R.id.topLayout));
        System.gc();
    }

    private void unbindDrawables(View view) {
        if (view != null && view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup && !(view instanceof AdapterView)) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (getCurrentFragment() == FragmentsAvailable.SETTINGS) {
            if (fragment instanceof SettingsFragment) {
                ((SettingsFragment) fragment).closePreferenceScreen();
            }
        }
        Bundle extras = intent.getExtras();
        if (extras != null && extras.getBoolean("GoToChat", false)) {
            String sipUri = extras.getString("ChatContactSipUri");
            doNotGoToCallActivity = true;
            displayChat(sipUri, null, null);
        } else if (extras != null && extras.getBoolean("GoToHistory", false)) {
            doNotGoToCallActivity = true;
            changeCurrentFragment(FragmentsAvailable.HISTORY_LIST, null);
        } else if (extras != null && extras.getBoolean("GoToInapp", false)) {
            doNotGoToCallActivity = true;
            displayInapp();
        } else if (extras != null && extras.getBoolean("Notification", false)) {
            if (LinphoneManager.getLc().getCallsNb() > 0) {
                Call call = LinphoneManager.getLc().getCalls()[0];
                startIncallActivity(call);
            }
        } else if (extras != null && extras.getBoolean("StartCall", false)) {
            boolean extraBool = extras.getBoolean("StartCall", false);
            if (CallActivity.isInstanciated()) {
                CallActivity.instance().startIncomingCallActivity();
            } else {
                mAddressWaitingToBeCalled = extras.getString("NumberToCall");
                goToDialerFragment();
                //startActivity(new Intent(this, CallIncomingActivity.class));
            }
        } else {
            DialerFragment dialerFragment = DialerFragment.instance();
            if (dialerFragment != null) {
                if (extras != null && extras.containsKey("SipUriOrNumber")) {
                    if (getResources().getBoolean(R.bool.automatically_start_intercepted_outgoing_gsm_call)) {
                        ((DialerFragment) dialerFragment).newOutgoingCall(extras.getString("SipUriOrNumber"));
                    } else {
                        ((DialerFragment) dialerFragment).displayTextInAddressBar(extras.getString("SipUriOrNumber"));
                    }
                } else {
                    ((DialerFragment) dialerFragment).newOutgoingCall(intent);
                }
            } else {
                if (extras != null && extras.containsKey("SipUriOrNumber")) {
                    mAddressWaitingToBeCalled = extras.getString("SipUriOrNumber");
                    goToDialerFragment();
                }
            }
            if (LinphoneManager.getLc().getCalls().length > 0) {
                // If a call is ringing, start incomingcallactivity
                Collection<Call.State> incoming = new ArrayList<Call.State>();
                incoming.add(Call.State.IncomingReceived);
                if (LinphoneUtils.getCallsInState(LinphoneManager.getLc(), incoming).size() > 0) {
                    if (CallActivity.isInstanciated()) {
                        CallActivity.instance().startIncomingCallActivity();
                    } else {
                        startActivity(new Intent(this, CallIncomingActivity.class));
                    }
                }
            }
        }
    }

    public boolean isOnBackground() {
        return isOnBackground;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (currentFragment) {
                case DIALER:
                case CONTACTS_LIST:
                case HISTORY_LIST:
                case CHAT_LIST:
                    boolean isBackgroundModeActive = LinphonePreferences.instance().isBackgroundModeEnabled();
                    if (!isBackgroundModeActive) {
                        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
                        finish();
                    } else if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) {
                        return true;
                    }
                    break;
                case GROUP_CHAT:
                    LinphoneActivity.instance().goToChatList();
                    return true;
                default:
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    //SIDE MENU
    public void openOrCloseSideMenu(boolean open) {
        if (open) {
            sideMenu.openDrawer(sideMenuContent);
        } else {
            sideMenu.closeDrawer(sideMenuContent);
        }
    }

    public void initSideMenu() {
        sideMenu = findViewById(R.id.side_menu);
        sideMenuItems = new ArrayList<>();
        if (!getResources().getBoolean(R.bool.hide_assistant_from_side_menu)) {
            sideMenuItems.add(getResources().getString(R.string.menu_assistant));
        }
        if (!getResources().getBoolean(R.bool.hide_settings_from_side_menu)) {
            sideMenuItems.add(getResources().getString(R.string.menu_settings));
        }
        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            sideMenuItems.add(getResources().getString(R.string.inapp));
        }
        sideMenuItems.add(getResources().getString(R.string.menu_about));
        sideMenuContent = findViewById(R.id.side_menu_content);
        sideMenuItemList = findViewById(R.id.item_list);
        menu = findViewById(R.id.side_menu_button);

        sideMenuItemList.setAdapter(new ArrayAdapter<>(this, R.layout.side_menu_item_cell, sideMenuItems));
        sideMenuItemList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_settings))) {
                    LinphoneActivity.instance().displaySettings();
                }
                if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_about))) {
                    LinphoneActivity.instance().displayAbout();
                }
                if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.menu_assistant))) {
                    LinphoneActivity.instance().displayAssistant();
                }
                if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
                    if (sideMenuItemList.getAdapter().getItem(i).toString().equals(getString(R.string.inapp))) {
                        LinphoneActivity.instance().displayInapp();
                    }
                }
                openOrCloseSideMenu(false);
            }
        });

        initAccounts();

        menu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                if (sideMenu.isDrawerVisible(Gravity.LEFT)) {
                    sideMenu.closeDrawer(sideMenuContent);
                } else {
                    sideMenu.openDrawer(sideMenuContent);
                }
            }
        });

        quitLayout = findViewById(R.id.side_menu_quit);
        quitLayout.setOnClickListener(new OnClickListener() {
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
        defaultAccount.setVisibility(View.VISIBLE);
        ImageView status = defaultAccount.findViewById(R.id.main_account_status);
        TextView address = defaultAccount.findViewById(R.id.main_account_address);
        TextView displayName = defaultAccount.findViewById(R.id.main_account_display_name);


        ProxyConfig proxy = LinphoneManager.getLc().getDefaultProxyConfig();
        if (proxy == null) {
            displayName.setText(getString(R.string.no_account));
            status.setVisibility(View.GONE);
            address.setText("");
            statusFragment.resetAccountStatus();
            LinphoneManager.getInstance().subscribeFriendList(false);

            defaultAccount.setOnClickListener(null);
        } else {
            address.setText(proxy.getIdentityAddress().asStringUriOnly());
            displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
            status.setImageResource(getStatusIconResource(proxy.getState()));
            status.setVisibility(View.VISIBLE);

            defaultAccount.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    LinphoneActivity.instance().displayAccountSettings(LinphonePreferences.instance().getDefaultAccountIndex());
                    openOrCloseSideMenu(false);
                }
            });
        }
    }

    public void refreshAccounts() {
        if (LinphoneManager.getLc().getProxyConfigList() != null &&
                LinphoneManager.getLc().getProxyConfigList().length > 1) {
            accountsList.setVisibility(View.VISIBLE);
            accountsList.setAdapter(new AccountsListAdapter());
            accountsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (view != null && view.getTag() != null) {
                        int position = Integer.parseInt(view.getTag().toString());
                        LinphoneActivity.instance().displayAccountSettings(position);
                    }
                    openOrCloseSideMenu(false);
                }
            });
        } else {
            accountsList.setVisibility(View.GONE);
        }
        displayMainAccount();
    }

    private void initAccounts() {
        accountsList = findViewById(R.id.accounts_list);
        defaultAccount = findViewById(R.id.default_account);
    }

    class AccountsListAdapter extends BaseAdapter {
        List<ProxyConfig> proxy_list;

        AccountsListAdapter() {
            proxy_list = new ArrayList<>();
            refresh();
        }

        public void refresh() {
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
            int accountIndex = 0;

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

    //Inapp Purchase
    private void isTrialAccount() {
        if (LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
            XmlRpcHelper helper = new XmlRpcHelper();
            helper.isTrialAccountAsync(new XmlRpcListenerBase() {
                @Override
                public void onTrialAccountFetched(boolean isTrial) {
                    isTrialAccount = isTrial;
                    getExpirationAccount();
                }

                @Override
                public void onError(String error) {
                }
            }, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
        }
    }

    private void getExpirationAccount() {
        if (LinphoneManager.getLc().getDefaultProxyConfig() != null && LinphonePreferences.instance().getInappPopupTime() != null) {
            XmlRpcHelper helper = new XmlRpcHelper();
            helper.getAccountExpireAsync(new XmlRpcListenerBase() {
                @Override
                public void onAccountExpireFetched(String result) {
                    if (result != null) {
                        long timestamp = Long.parseLong(result);

                        Calendar calresult = Calendar.getInstance();
                        calresult.setTimeInMillis(timestamp);

                        int diff = getDiffDays(calresult, Calendar.getInstance());
                        if (diff != -1 && diff <= getResources().getInteger(R.integer.days_notification_shown)) {
                            displayInappNotification(timestampToHumanDate(calresult));
                        }
                    }
                }

                @Override
                public void onError(String error) {
                }
            }, LinphonePreferences.instance().getAccountUsername(LinphonePreferences.instance().getDefaultAccountIndex()), LinphonePreferences.instance().getAccountHa1(LinphonePreferences.instance().getDefaultAccountIndex()));
        }
    }

    public void displayInappNotification(String date) {
        Timestamp now = new Timestamp(new Date().getTime());
        if (LinphonePreferences.instance().getInappPopupTime() != null && Long.parseLong(LinphonePreferences.instance().getInappPopupTime()) > now.getTime()) {
            return;
        } else {
            long newDate = now.getTime() + getResources().getInteger(R.integer.time_between_inapp_notification);
            LinphonePreferences.instance().setInappPopupTime(String.valueOf(newDate));
        }
        if (isTrialAccount) {
            LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_trial_expire), date));
        } else {
            LinphoneService.instance().displayInappNotification(String.format(getString(R.string.inapp_notification_account_expire), date));
        }

    }

    private String timestampToHumanDate(Calendar cal) {
        SimpleDateFormat dateFormat;
        dateFormat = new SimpleDateFormat(getResources().getString(R.string.inapp_popup_date_format));
        return dateFormat.format(cal.getTime());
    }

    private int getDiffDays(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            return -1;
        }
        if (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
            return cal1.get(Calendar.DAY_OF_YEAR) - cal2.get(Calendar.DAY_OF_YEAR);
        }
        return -1;
    }
}
