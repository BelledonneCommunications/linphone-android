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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.drawerlayout.widget.DrawerLayout;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.fragments.StatusFragment;
import org.linphone.utils.ThemableActivity;

public abstract class MainActivity extends ThemableActivity
        implements StatusFragment.MenuClikedListener, SideMenuFragment.QuitClikedListener {
    protected LinearLayout mFragment, mChildFragment;
    protected RelativeLayout mHistory, mContacts, mDialer, mChat;
    protected TextView mMissedCalls, mMissedMessages;
    protected View mContactsSelected, mHistorySelected, mDialerSelected, mChatSelected;
    protected LinearLayout mTopBar;
    protected TextView mTopBarTitle;
    protected LinearLayout mTabBar;

    protected DrawerLayout mSideMenu;
    protected RelativeLayout mSideMenuContent;

    protected SideMenuFragment mSideMenuFragment;
    protected StatusFragment mStatusFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinphoneService.instance().removeForegroundServiceNotificationIfPossible();

        setContentView(R.layout.main);

        mFragment = findViewById(R.id.fragmentContainer);
        mChildFragment = findViewById(R.id.fragmentContainer2);

        mHistory = findViewById(R.id.history);
        mHistory.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
                });
        mContacts = findViewById(R.id.contacts);
        mContacts.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
                });
        mDialer = findViewById(R.id.dialer);
        mDialer.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
                });
        mChat = findViewById(R.id.chat);
        mChat.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
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

        mStatusFragment =
                (StatusFragment) getSupportFragmentManager().findFragmentById(R.id.status_fragment);

        mSideMenu = findViewById(R.id.side_menu);
        mSideMenuContent = findViewById(R.id.side_menu_content);
        mSideMenuFragment =
                (SideMenuFragment)
                        getSupportFragmentManager().findFragmentById(R.id.side_menu_fragment);
        mSideMenuFragment.setDrawer(mSideMenu, mSideMenuContent);
    }

    @Override
    public void onMenuCliked() {
        if (mSideMenuFragment.isOpened()) {
            mSideMenuFragment.openOrCloseSideMenu(false);
        } else {
            mSideMenuFragment.openOrCloseSideMenu(true);
        }
    }

    @Override
    public void onQuitClicked() {
        quit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mStatusFragment.setMenuListener(this);
        mSideMenuFragment.setQuitListener(this);
        mSideMenuFragment.displayAccountsInSideMenu();
    }

    @Override
    protected void onPause() {
        mStatusFragment.setMenuListener(null);
        mSideMenuFragment.setQuitListener(null);
        super.onPause();
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

    // Tab, Top and Status bars

    protected void hideStatusBar() {
        if (isTablet()) {
            return;
        }

        findViewById(R.id.status).setVisibility(View.GONE);
    }

    protected void showStatusBar() {
        if (isTablet()) {
            return;
        }
        findViewById(R.id.status).setVisibility(View.VISIBLE);
    }

    protected void hideTabBar(Boolean hide) {
        if (hide && !isTablet()) { // do not hide if tablet, otherwise won't be able to navigate...
            mTabBar.setVisibility(View.GONE);
        } else {
            mTabBar.setVisibility(View.VISIBLE);
        }
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
}
