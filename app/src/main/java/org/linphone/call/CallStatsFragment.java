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
import android.widget.ExpandableListView;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import java.util.Arrays;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;

public class CallStatsFragment extends Fragment {
    private View mView;
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;;
    private ExpandableListView mExpandableList;
    private CallStatsAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.call_stats, container, false);
        mView = view;

        mExpandableList = view.findViewById(R.id.call_list);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Core core = LinphoneManager.getCore();

        if (mAdapter == null) {
            mAdapter = new CallStatsAdapter(getActivity());

            mExpandableList.setAdapter(mAdapter);
            // allows you to open the first child in the list
            mExpandableList.expandGroup(0);
        }

        // Sends calls from the list to the adapter
        if (core != null && core.getCallsNb() >= 1) {
            mAdapter.updateListItems(Arrays.asList(core.getCalls()));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void setDrawer(DrawerLayout drawer, RelativeLayout content) {
        mSideMenu = drawer;
        mSideMenuContent = content;

        if (getResources().getBoolean(R.bool.hide_in_call_stats)) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    public boolean isOpened() {
        return mSideMenu != null && mSideMenu.isDrawerVisible(Gravity.LEFT);
    }

    public void closeDrawer() {
        openOrCloseSideMenu(false, false);
    }

    public void openOrCloseSideMenu(boolean open, boolean animate) {
        if (mSideMenu == null || mSideMenuContent == null) return;
        if (getResources().getBoolean(R.bool.hide_in_call_stats)) return;

        if (open) {
            mSideMenu.openDrawer(mSideMenuContent, animate);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent, animate);
        }
    }
}
