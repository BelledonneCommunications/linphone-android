package org.linphone.activities;

/*
MainActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.fragments.EmptyFragment;
import org.linphone.fragments.StatusBarFragment;
import org.linphone.history.HistoryActivity;
import org.linphone.menu.SideMenuFragment;
import org.linphone.settings.LinphonePreferences;
import org.linphone.settings.SettingsActivity;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.PushNotificationUtils;

public abstract class MainActivity extends LinphoneGenericActivity
        implements StatusBarFragment.MenuClikedListener, SideMenuFragment.QuitClikedListener {
    private static final int MAIN_PERMISSIONS = 1;
    protected static final int FRAGMENT_SPECIFIC_PERMISSION = 2;

    private TextView mMissedCalls;
    private TextView mMissedMessages;
    protected View mContactsSelected;
    protected View mHistorySelected;
    View mDialerSelected;
    protected View mChatSelected;
    private LinearLayout mTopBar;
    private TextView mTopBarTitle;
    private LinearLayout mTabBar;

    private SideMenuFragment mSideMenuFragment;
    private StatusBarFragment mStatusBarFragment;

    protected boolean mOnBackPressGoHome;
    protected boolean mAlwaysHideTabBar;
    protected String[] mPermissionsToHave;

    private CoreListenerStub mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

        if (!LinphoneService.isReady()) {
            finish();
        }

        setContentView(R.layout.main);

        mOnBackPressGoHome = true;
        mAlwaysHideTabBar = false;

        RelativeLayout history = findViewById(R.id.history);
        history.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                        addFlagsToIntent(intent);
                        startActivity(intent);
                    }
                });
        RelativeLayout contacts = findViewById(R.id.contacts);
        contacts.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
                        addFlagsToIntent(intent);
                        startActivity(intent);
                    }
                });
        RelativeLayout dialer = findViewById(R.id.dialer);
        dialer.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, DialerActivity.class);
                        addFlagsToIntent(intent);
                        startActivity(intent);
                    }
                });
        RelativeLayout chat = findViewById(R.id.chat);
        chat.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        addFlagsToIntent(intent);
                        startActivity(intent);
                    }
                });

        mMissedCalls = findViewById(R.id.missed_calls);
        mMissedMessages = findViewById(R.id.missed_chats);

        mHistorySelected = findViewById(R.id.history_select);
        mContactsSelected = findViewById(R.id.contacts_select);
        mDialerSelected = findViewById(R.id.dialer_select);
        mChatSelected = findViewById(R.id.chat_select);

        mTabBar = findViewById(R.id.footer);
        mTopBar = findViewById(R.id.top_bar);
        mTopBarTitle = findViewById(R.id.top_bar_title);

        ImageView back = findViewById(R.id.cancel);
        back.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBack();
                    }
                });

        mStatusBarFragment =
                (StatusBarFragment) getFragmentManager().findFragmentById(R.id.status_fragment);

        DrawerLayout mSideMenu = findViewById(R.id.side_menu);
        RelativeLayout mSideMenuContent = findViewById(R.id.side_menu_content);
        mSideMenuFragment =
                (SideMenuFragment)
                        getSupportFragmentManager().findFragmentById(R.id.side_menu_fragment);
        mSideMenuFragment.setDrawer(mSideMenu, mSideMenuContent);

        if (getResources().getBoolean(R.bool.disable_chat)) {
            chat.setVisibility(View.GONE);
        }

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Released) {
                            displayMissedCalls();
                        }
                    }

                    @Override
                    public void onMessageReceived(Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onChatRoomRead(Core core, ChatRoom room) {
                        displayMissedChats();
                    }

                    @Override
                    public void onMessageReceivedUnableDecrypt(
                            Core core, ChatRoom room, ChatMessage message) {
                        displayMissedChats();
                    }

                    @Override
                    public void onRegistrationStateChanged(
                            Core core,
                            ProxyConfig proxyConfig,
                            RegistrationState state,
                            String message) {
                        mSideMenuFragment.displayAccountsInSideMenu();

                        if (state == RegistrationState.Ok) {
                            // For push notifications to work on some devices,
                            // app must be in "protected mode" in battery settings...
                            // https://stackoverflow.com/questions/31638986/protected-apps-setting-on-huawei-phones-and-how-to-handle-it
                            DeviceUtils
                                    .displayDialogIfDeviceHasPowerManagerThatCouldPreventPushNotifications(
                                            MainActivity.this);

                            if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
                                AuthInfo authInfo =
                                        core.findAuthInfo(
                                                proxyConfig.getRealm(),
                                                proxyConfig.getIdentityAddress().getUsername(),
                                                proxyConfig.getDomain());
                                if (authInfo != null
                                        && authInfo.getDomain()
                                                .equals(getString(R.string.default_domain))) {
                                    LinphoneManager.getInstance().isAccountWithAlias();
                                }
                            }

                            if (!Compatibility.isDoNotDisturbSettingsAccessGranted(
                                    MainActivity.this)) {
                                displayDNDSettingsDialog();
                            }
                        }
                    }

                    @Override
                    public void onLogCollectionUploadStateChanged(
                            Core core, Core.LogCollectionUploadState state, String info) {
                        Log.d(
                                "[Main Activity] Log upload state: "
                                        + state.toString()
                                        + ", info = "
                                        + info);
                        if (state == Core.LogCollectionUploadState.Delivered) {
                            ClipboardManager clipboard =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Logs url", info);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(
                                            MainActivity.this,
                                            getString(R.string.logs_url_copied_to_clipboard),
                                            Toast.LENGTH_SHORT)
                                    .show();
                            shareUploadedLogsUrl(info);
                        }
                    }
                };
    }

    @Override
    protected void onStart() {
        super.onStart();

        requestRequiredPermissions();

        if (DeviceUtils.isAppUserRestricted(this)) {
            Log.w(
                    "[Main Activity] Device has been restricted by user (Android 9+), push notifications won't work !");
        }

        int bucket = DeviceUtils.getAppStandbyBucket(this);
        if (bucket > 0) {
            Log.w(
                    "[Main Activity] Device is in bucket "
                            + Compatibility.getAppStandbyBucketNameFromValue(bucket));
        }

        if (!PushNotificationUtils.isAvailable(this)) {
            Log.w("[Main Activity] Push notifications won't work !");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        LinphoneService.instance()
                .getNotificationManager()
                .removeForegroundServiceNotificationIfPossible();

        hideTopBar();
        if (!mAlwaysHideTabBar
                && (getFragmentManager().getBackStackEntryCount() == 0
                        || !getResources()
                                .getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
            showTabBar();
        }

        mHistorySelected.setVisibility(View.GONE);
        mContactsSelected.setVisibility(View.GONE);
        mDialerSelected.setVisibility(View.GONE);
        mChatSelected.setVisibility(View.GONE);

        mStatusBarFragment.setMenuListener(this);
        mSideMenuFragment.setQuitListener(this);
        mSideMenuFragment.displayAccountsInSideMenu();

        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.closeDrawer();
        }

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
            displayMissedChats();
            displayMissedCalls();
        }
    }

    @Override
    protected void onPause() {
        mStatusBarFragment.setMenuListener(null);
        mSideMenuFragment.setQuitListener(null);

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (IllegalStateException ise) {
            // Do not log this exception
        }
    }

    @Override
    public void onMenuCliked() {
        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.openOrCloseSideMenu(false, true);
        } else {
            mSideMenuFragment.openOrCloseSideMenu(true, true);
        }
    }

    @Override
    public void onQuitClicked() {
        quit();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mOnBackPressGoHome) {
                if (getFragmentManager().getBackStackEntryCount() == 0) {
                    goHomeAndClearStack();
                    return true;
                }
            }
            goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public boolean popBackStack() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStackImmediate();
            if (!mAlwaysHideTabBar
                    && (getFragmentManager().getBackStackEntryCount() == 0
                            && getResources()
                                    .getBoolean(R.bool.hide_bottom_bar_on_second_level_views))) {
                showTabBar();
            }
            return true;
        }
        return false;
    }

    public void goBack() {
        finish();
    }

    protected boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    private void goHomeAndClearStack() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void quit() {
        goHomeAndClearStack();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    // Tab, Top and Status bars

    public void hideStatusBar() {
        findViewById(R.id.status_fragment).setVisibility(View.GONE);
    }

    public void showStatusBar() {
        findViewById(R.id.status_fragment).setVisibility(View.VISIBLE);
    }

    public void hideTabBar() {
        if (!isTablet()) { // do not hide if tablet, otherwise won't be able to navigate...
            mTabBar.setVisibility(View.GONE);
        }
    }

    public void showTabBar() {
        mTabBar.setVisibility(View.VISIBLE);
    }

    protected void hideTopBar() {
        mTopBar.setVisibility(View.GONE);
        mTopBarTitle.setText("");
    }

    private void showTopBar() {
        mTopBar.setVisibility(View.VISIBLE);
    }

    protected void showTopBarWithTitle(String title) {
        showTopBar();
        mTopBarTitle.setText(title);
    }

    // Permissions

    public boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissionIfNotGranted(String permission) {
        if (!checkPermission(permission)) {
            Log.i("[Permission] Requesting " + permission + " permission");

            String[] permissions = new String[] {permission};
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                // This is to workaround an infinite loop of pause/start in Activity issue
                // if incoming call ends while screen if off and locked
                ActivityCompat.requestPermissions(this, permissions, FRAGMENT_SPECIFIC_PERMISSION);
            }
        }
    }

    public void requestPermissionsIfNotGranted(String[] perms) {
        requestPermissionsIfNotGranted(perms, FRAGMENT_SPECIFIC_PERMISSION);
    }

    private void requestPermissionsIfNotGranted(String[] perms, int resultCode) {
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        if (perms != null) { // This is created (or not) by the child activity
            for (String permissionToHave : perms) {
                if (!checkPermission(permissionToHave)) {
                    permissionsToAskFor.add(permissionToHave);
                }
            }
        }

        if (permissionsToAskFor.size() > 0) {
            for (String permission : permissionsToAskFor) {
                Log.i("[Permission] Requesting " + permission + " permission");
            }
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);

            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            boolean locked = km.inKeyguardRestrictedInputMode();
            if (!locked) {
                // This is to workaround an infinite loop of pause/start in Activity issue
                // if incoming call ends while screen if off and locked
                ActivityCompat.requestPermissions(this, permissions, resultCode);
            }
        }
    }

    private void requestRequiredPermissions() {
        requestPermissionsIfNotGranted(mPermissionsToHave, MAIN_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (permissions.length <= 0) return;

        for (int i = 0; i < permissions.length; i++) {
            Log.i(
                    "[Permission] "
                            + permissions[i]
                            + " is "
                            + (grantResults[i] == PackageManager.PERMISSION_GRANTED
                                    ? "granted"
                                    : "denied"));
            if (permissions[i].equals(Manifest.permission.READ_CONTACTS)
                    || permissions[i].equals(Manifest.permission.WRITE_CONTACTS)) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    ContactsManager.getInstance().enableContactsAccess();
                    ContactsManager.getInstance().initializeContactManager();
                }
            } else if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                boolean enableRingtone = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
            } else if (permissions[i].equals(Manifest.permission.CAMERA)
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                LinphoneUtils.reloadVideoDevices();
            }
        }
    }

    // Missed calls & chat indicators

    protected void displayMissedCalls() {
        int count = 0;
        Core core = LinphoneManager.getCore();
        if (core != null) {
            count = core.getMissedCallsCount();
        }

        if (count > 0) {
            mMissedCalls.setText(String.valueOf(count));
            mMissedCalls.setVisibility(View.VISIBLE);
        } else {
            mMissedCalls.clearAnimation();
            mMissedCalls.setVisibility(View.GONE);
        }
    }

    public void displayMissedChats() {
        int count = 0;
        Core core = LinphoneManager.getCore();
        if (core != null) {
            count = core.getUnreadChatMessageCountFromActiveLocals();
        }

        if (count > 0) {
            mMissedMessages.setText(String.valueOf(count));
            mMissedMessages.setVisibility(View.VISIBLE);
        } else {
            mMissedMessages.clearAnimation();
            mMissedMessages.setVisibility(View.GONE);
        }
    }

    // Navigation between actvities

    private void addFlagsToIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    protected void changeFragment(Fragment fragment, String name, boolean isChild) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        if (transaction.isAddToBackStackAllowed()) {
            int count = fragmentManager.getBackStackEntryCount();
            if (count > 0) {
                FragmentManager.BackStackEntry entry =
                        fragmentManager.getBackStackEntryAt(count - 1);

                if (entry != null && name.equals(entry.getName())) {
                    fragmentManager.popBackStack();
                    if (!isChild) {
                        // We just removed it's duplicate from the back stack
                        // And we want at least one in it
                        transaction.addToBackStack(name);
                    }
                }
            }

            if (isChild) {
                transaction.addToBackStack(name);
            }
        }

        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            if (isChild) {
                if (!isTablet()) {
                    hideTabBar();
                }
            } else {
                showTabBar();
            }
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        if (isChild && isTablet()) {
            transaction.replace(R.id.fragmentContainer2, fragment, name);
            findViewById(R.id.fragmentContainer2).setVisibility(View.VISIBLE);
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, name);
        }
        transaction.commitAllowingStateLoss();
        fragmentManager.executePendingTransactions();
    }

    public void showEmptyChildFragment() {
        changeFragment(new EmptyFragment(), "Empty", true);
    }

    public void showAccountSettings(int accountIndex) {
        Intent intent = new Intent(this, SettingsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("Account", accountIndex);
        startActivity(intent);
    }

    public void showContactDetails(LinphoneContact contact) {
        Intent intent = new Intent(this, ContactsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("Contact", contact);
        startActivity(intent);
    }

    public void showContactsListForCreationOrEdition(Address address) {
        if (address == null) return;

        Intent intent = new Intent(this, ContactsActivity.class);
        addFlagsToIntent(intent);
        intent.putExtra("CreateOrEdit", true);
        intent.putExtra("SipUri", address.asStringUriOnly());
        if (address.getDisplayName() != null) {
            intent.putExtra("DisplayName", address.getDisplayName());
        }
        startActivity(intent);
    }

    public void showChatRoom(Address localAddress, Address peerAddress) {
        Intent intent = new Intent(this, ChatActivity.class);
        addFlagsToIntent(intent);
        if (localAddress != null) {
            intent.putExtra("LocalSipUri", localAddress.asStringUriOnly());
        }
        if (peerAddress != null) {
            intent.putExtra("RemoteSipUri", peerAddress.asStringUriOnly());
        }
        startActivity(intent);
    }

    // Dialogs

    public Dialog displayDialog(String text) {
        return LinphoneUtils.getDialog(this, text);
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

    private void displayDNDSettingsDialog() {
        if (!LinphonePreferences.instance().isDNDSettingsPopupEnabled()) return;
        Log.w("[Permission] Asking user to grant us permission to read DND settings");

        final Dialog dialog =
                displayDialog(getString(R.string.pref_grant_read_dnd_settings_permission_desc));
        dialog.findViewById(R.id.dialog_do_not_ask_again_layout).setVisibility(View.VISIBLE);
        final CheckBox doNotAskAgain = dialog.findViewById(R.id.doNotAskAgain);
        dialog.findViewById(R.id.doNotAskAgainLabel)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                doNotAskAgain.setChecked(!doNotAskAgain.isChecked());
                            }
                        });
        Button cancel = dialog.findViewById(R.id.dialog_cancel_button);
        cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (doNotAskAgain.isChecked()) {
                            LinphonePreferences.instance().enableDNDSettingsPopup(false);
                        }
                        dialog.dismiss();
                    }
                });
        Button ok = dialog.findViewById(R.id.dialog_ok_button);
        ok.setVisibility(View.VISIBLE);
        ok.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(
                                new Intent("android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"));
                        dialog.dismiss();
                    }
                });
        Button delete = dialog.findViewById(R.id.dialog_delete_button);
        delete.setVisibility(View.GONE);
        dialog.show();
    }

    // Logs

    private void shareUploadedLogsUrl(String info) {
        final String appName = getString(R.string.app_name);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_EMAIL, new String[] {getString(R.string.about_bugreport_email)});
        i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
        i.putExtra(Intent.EXTRA_TEXT, info);
        i.setType("application/zip");

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(ex);
        }
    }

    // Others

    public SideMenuFragment getSideMenuFragment() {
        return mSideMenuFragment;
    }
}
