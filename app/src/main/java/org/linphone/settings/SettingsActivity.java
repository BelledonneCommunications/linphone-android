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
import android.os.Bundle;
import android.view.KeyEvent;
import org.linphone.R;
import org.linphone.main.MainActivity;

public class SettingsActivity extends MainActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOnBackPressGoHome = false;

        changeFragment(new MenuSettingsFragment(), getString(R.string.settings), false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        hideTabBar();
        showTopBarWithTitle(getString(R.string.settings));
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isTablet() && keyCode == KeyEvent.KEYCODE_BACK) {
            if (popBackStack()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void goBack() {
        if (!isTablet()) {
            if (popBackStack()) {
                return;
            }
        }
        super.goBack();
    }

    public void showSettings(Fragment fragment, String name) {
        changeFragment(fragment, name, true);
    }
}
