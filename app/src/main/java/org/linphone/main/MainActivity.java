package org.linphone.main;

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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.chat.ChatActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.contacts.ContactsActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.tools.Log;
import org.linphone.fragments.StatusFragment;
import org.linphone.history.HistoryActivity;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.DeviceUtils;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.PushNotificationUtils;
import org.linphone.utils.ThemableActivity;

public abstract class MainActivity extends ThemableActivity
        implements StatusFragment.MenuClikedListener, SideMenuFragment.QuitClikedListener {
    private static final int MAIN_PERMISSIONS = 1;

    protected LinearLayout mFragment, mChildFragment;
    protected RelativeLayout mHistory, mContacts, mDialer, mChat;
    protected TextView mMissedCalls, mMissedMessages;
    protected View mContactsSelected, mHistorySelected, mDialerSelected, mChatSelected;
    protected LinearLayout mTopBar;
    protected TextView mTopBarTitle;
    protected LinearLayout mTabBar;
    protected ImageView mBack;

    protected DrawerLayout mSideMenu;
    protected RelativeLayout mSideMenuContent;

    protected SideMenuFragment mSideMenuFragment;
    protected StatusFragment mStatusFragment;

    protected boolean mOnBackPressGoHome;
    protected String[] mPermissionsToHave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinphoneService.instance().removeForegroundServiceNotificationIfPossible();

        setContentView(R.layout.main);

        mOnBackPressGoHome = true;

        mFragment = findViewById(R.id.fragmentContainer);
        mChildFragment = findViewById(R.id.fragmentContainer2);

        mHistory = findViewById(R.id.history);
        mHistory.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                });
        mContacts = findViewById(R.id.contacts);
        mContacts.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                });
        mDialer = findViewById(R.id.dialer);
        mDialer.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, DialerActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }
                });
        mChat = findViewById(R.id.chat);
        mChat.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        mBack = findViewById(R.id.cancel);
        mBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBack();
                    }
                });

        mStatusFragment =
                (StatusFragment) getSupportFragmentManager().findFragmentById(R.id.status_fragment);

        mSideMenu = findViewById(R.id.side_menu);
        mSideMenuContent = findViewById(R.id.side_menu_content);
        mSideMenuFragment =
                (SideMenuFragment)
                        getSupportFragmentManager().findFragmentById(R.id.side_menu_fragment);
        mSideMenuFragment.setDrawer(mSideMenu, mSideMenuContent);

        if (getResources().getBoolean(R.bool.disable_chat)) {
            mChat.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        askRequiredPermissions();

        if (checkPermission(Manifest.permission.READ_CONTACTS)) {
            ContactsManager.getInstance().enableContactsAccess();
        }
        ContactsManager.getInstance().initializeContactManager(this);

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

        hideTopBar();
        showTabBar();

        mHistorySelected.setVisibility(View.GONE);
        mContactsSelected.setVisibility(View.GONE);
        mDialerSelected.setVisibility(View.GONE);
        mChatSelected.setVisibility(View.GONE);

        mStatusFragment.setMenuListener(this);
        mSideMenuFragment.setQuitListener(this);
        mSideMenuFragment.displayAccountsInSideMenu();

        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.closeDrawer();
        }
    }

    @Override
    protected void onPause() {
        mStatusFragment.setMenuListener(null);
        mSideMenuFragment.setQuitListener(null);

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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mOnBackPressGoHome && keyCode == KeyEvent.KEYCODE_BACK) {
            if (LinphoneUtils.onKeyBackGoHome(this, keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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

    public void goBack() {
        finish();
    }

    protected boolean isTablet() {
        return getResources().getBoolean(R.bool.isTablet);
    }

    protected void quit() {
        finish();
        stopService(new Intent(Intent.ACTION_MAIN).setClass(this, LinphoneService.class));
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(getString(R.string.sync_account_type));
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    protected void changeFragment(Fragment fragment, String name, boolean isChild) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        if (isChild) {
            transaction.addToBackStack(name);
        }

        Compatibility.setFragmentTransactionReorderingAllowed(transaction, false);
        if (isChild && isTablet()) {
            transaction.replace(R.id.fragmentContainer2, fragment, name);
        } else {
            transaction.replace(R.id.fragmentContainer, fragment, name);
        }
        transaction.commitAllowingStateLoss();
        fm.executePendingTransactions();
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

    protected void showTopBar() {
        mTopBar.setVisibility(View.VISIBLE);
    }

    protected void showTopBarWithTitle(String title) {
        showTopBar();
        mTopBarTitle.setText(title);
    }

    // Permissions

    protected boolean checkPermission(String permission) {
        int granted = getPackageManager().checkPermission(permission, getPackageName());
        Log.i(
                "[Permission] "
                        + permission
                        + " permission is "
                        + (granted == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    private void askRequiredPermissions() {
        ArrayList<String> permissionsToAskFor = new ArrayList<>();
        if (mPermissionsToHave != null) { // This is created (or not) by the child activity
            for (String permissionToHave : mPermissionsToHave) {
                if (!checkPermission(permissionToHave)) {
                    permissionsToAskFor.add(permissionToHave);
                }
            }
        }

        if (permissionsToAskFor.size() > 0) {
            for (String permission : permissionsToAskFor) {
                Log.i("[Permission] Asking for " + permission + " permission");
            }
            String[] permissions = new String[permissionsToAskFor.size()];
            permissions = permissionsToAskFor.toArray(permissions);

            ActivityCompat.requestPermissions(this, permissions, MAIN_PERMISSIONS);
        }
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
                }
            } else if (permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                boolean enableRingtone = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                LinphonePreferences.instance().enableDeviceRingtone(enableRingtone);
                LinphoneManager.getInstance().enableDeviceRingtone(enableRingtone);
            }
        }
    }

    // Missed calls & chat indicators

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
            mMissedMessages.setText(missedChatCount + "");
            mMissedMessages.setVisibility(View.VISIBLE);
        } else {
            mMissedMessages.clearAnimation();
            mMissedMessages.setVisibility(View.GONE);
        }
    }
}
