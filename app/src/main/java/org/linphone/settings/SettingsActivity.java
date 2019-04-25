package org.linphone.settings;

/*
SettingsActivity.java
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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import org.linphone.R;
import org.linphone.main.MainActivity;

public class SettingsActivity extends MainActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment currentFragment = getFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (currentFragment == null) {
            if (getIntent() != null && getIntent().getExtras() != null) {
                Bundle extras = getIntent().getExtras();
                if (extras.containsKey("Account")) {
                    int accountIndex = extras.getInt("Account");
                    showAccountSettings(accountIndex, false);
                } else {
                    showSettingsMenu();
                    if (isTablet()) {
                        showEmptyChildFragment();
                    }
                }
            } else {
                showSettingsMenu();
                if (isTablet()) {
                    showEmptyChildFragment();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        hideTabBar();

        int count = getFragmentManager().getBackStackEntryCount();
        if (count == 0) {
            showTopBarWithTitle(getString(R.string.settings));
        } else {
            FragmentManager.BackStackEntry entry =
                    getFragmentManager().getBackStackEntryAt(count - 1);
            showTopBarWithTitle(entry.getName());
        }
    }

    @Override
    public void goBack() {
        if (!isTablet()) {
            if (popBackStack()) {
                showTopBarWithTitle(getString(R.string.settings));
                return;
            }
        }
        super.goBack();
    }

    private void showSettingsMenu() {
        Bundle extras = new Bundle();
        MenuSettingsFragment menuSettingsFragment = new MenuSettingsFragment();
        menuSettingsFragment.setArguments(extras);
        changeFragment(menuSettingsFragment, getString(R.string.settings), false);
        showTopBarWithTitle(getString(R.string.settings));
    }

    public void showSettings(Fragment fragment, String name) {
        changeFragment(fragment, name, true);
        showTopBarWithTitle(name);
    }

    public void showAccountSettings(int accountIndex, boolean isChild) {
        Bundle extras = new Bundle();
        extras.putInt("Account", accountIndex);
        AccountSettingsFragment accountSettingsFragment = new AccountSettingsFragment();
        accountSettingsFragment.setArguments(extras);
        changeFragment(accountSettingsFragment, getString(R.string.pref_sipaccount), isChild);
        showTopBarWithTitle(getString(R.string.pref_sipaccount));
    }
}
