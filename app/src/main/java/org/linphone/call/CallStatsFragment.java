package org.linphone.call;

/*
CallStatsFragment.java
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import org.linphone.R;

public class CallStatsFragment extends Fragment {
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.call_stats, container, false);

        return view;
    }

    public void setDrawer(DrawerLayout drawer, RelativeLayout content) {
        mSideMenu = drawer;
        mSideMenuContent = content;
    }

    public boolean isOpened() {
        return mSideMenu != null && mSideMenu.isDrawerVisible(Gravity.LEFT);
    }

    public void closeDrawer() {
        openOrCloseSideMenu(false, false);
    }

    public void openOrCloseSideMenu(boolean open, boolean animate) {
        if (mSideMenu == null || mSideMenuContent == null) return;

        if (open) {
            mSideMenu.openDrawer(mSideMenuContent, animate);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent, animate);
        }
    }
}
