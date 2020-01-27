/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.call;

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
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;

public class CallStatsFragment extends Fragment {
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;;
    private ExpandableListView mExpandableList;
    private CallStatsAdapter mAdapter;
    private CoreListenerStub mListener;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.call_stats, container, false);

        mExpandableList = view.findViewById(R.id.devices_list);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.End || state == Call.State.Error) {
                            mAdapter.updateListItems(
                                    Arrays.asList(LinphoneManager.getCore().getCalls()));
                        }
                    }
                };
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

        core.addListener(mListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }
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
