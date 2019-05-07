package org.linphone.call;

/*
CallActivity.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

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

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.ThemableActivity;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Core;
import org.linphone.core.CoreListener;
import org.linphone.core.CoreListenerStub;

public class NewCallActivity extends ThemableActivity
        implements CallStatusBarFragment.StatsClikedListener {
    private CallStatusBarFragment mStatusBarFragment;
    private CallStatsFragment mStatsFragment;
    private CoreListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Compatibility.setShowWhenLocked(this, true);

        setContentView(R.layout.call);

        DrawerLayout mSideMenu = findViewById(R.id.side_menu);
        RelativeLayout mSideMenuContent = findViewById(R.id.side_menu_content);
        mStatsFragment =
                (CallStatsFragment) getFragmentManager().findFragmentById(R.id.call_stats_fragment);
        mStatsFragment.setDrawer(mSideMenu, mSideMenuContent);

        mStatusBarFragment =
                (CallStatusBarFragment)
                        getFragmentManager().findFragmentById(R.id.status_bar_fragment);
        mStatusBarFragment.setStatsListener(this);

        mListener = new CoreListenerStub() {};
    }

    @Override
    protected void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }
    }

    @Override
    protected void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStatsClicked() {
        if (mStatsFragment.isOpened()) {
            mStatsFragment.openOrCloseSideMenu(false, true);
        } else {
            mStatsFragment.openOrCloseSideMenu(true, true);
        }
    }
}
