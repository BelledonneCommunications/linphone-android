package org.linphone.menu;

/*
SideMenuFragment.java
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

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.AboutActivity;
import org.linphone.activities.MainActivity;
import org.linphone.assistant.MenuAssistantActivity;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.recording.RecordingsActivity;
import org.linphone.settings.LinphonePreferences;
import org.linphone.settings.SettingsActivity;
import org.linphone.utils.LinphoneUtils;

public class SideMenuFragment extends Fragment {
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;
    private RelativeLayout mDefaultAccount;
    private ListView mAccountsList, mSideMenuItemList;
    private QuitClikedListener mQuitListener;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.side_menu, container, false);

        List<SideMenuItem> sideMenuItems = new ArrayList<>();
        if (getResources().getBoolean(R.bool.show_log_out_in_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_logout),
                            R.drawable.quit_default));
        }
        if (!getResources().getBoolean(R.bool.hide_assistant_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_assistant),
                            R.drawable.menu_assistant));
        }
        if (!getResources().getBoolean(R.bool.hide_settings_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_settings),
                            R.drawable.menu_options));
        }
        if (getResources().getBoolean(R.bool.enable_in_app_purchase)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.inapp), R.drawable.menu_options));
        }
        if (!getResources().getBoolean(R.bool.hide_recordings_from_side_menu)) {
            sideMenuItems.add(
                    new SideMenuItem(
                            getResources().getString(R.string.menu_recordings),
                            R.drawable.menu_recordings));
        }
        sideMenuItems.add(
                new SideMenuItem(
                        getResources().getString(R.string.menu_about), R.drawable.menu_about));
        mSideMenuItemList = view.findViewById(R.id.item_list);

        mSideMenuItemList.setAdapter(
                new SideMenuAdapter(getActivity(), R.layout.side_menu_item_cell, sideMenuItems));
        mSideMenuItemList.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        String selectedItem = mSideMenuItemList.getAdapter().getItem(i).toString();
                        if (selectedItem.equals(getString(R.string.menu_logout))) {
                            Core core = LinphoneManager.getCore();
                            if (core != null) {
                                core.setDefaultProxyConfig(null);
                                core.clearAllAuthInfo();
                                core.clearProxyConfig();
                                startActivity(
                                        new Intent()
                                                .setClass(
                                                        getActivity(),
                                                        MenuAssistantActivity.class));
                                getActivity().finish();
                            }
                        } else if (selectedItem.equals(getString(R.string.menu_settings))) {
                            startActivity(new Intent(getActivity(), SettingsActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_about))) {
                            startActivity(new Intent(getActivity(), AboutActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_assistant))) {
                            startActivity(new Intent(getActivity(), MenuAssistantActivity.class));
                        } else if (selectedItem.equals(getString(R.string.menu_recordings))) {
                            startActivity(new Intent(getActivity(), RecordingsActivity.class));
                        }
                    }
                });

        mAccountsList = view.findViewById(R.id.accounts_list);
        mDefaultAccount = view.findViewById(R.id.default_account);

        RelativeLayout quitLayout = view.findViewById(R.id.side_menu_quit);
        quitLayout.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mQuitListener != null) {
                            mQuitListener.onQuitClicked();
                        }
                    }
                });

        return view;
    }

    public void setQuitListener(QuitClikedListener listener) {
        mQuitListener = listener;
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

    private void displayMainAccount() {
        mDefaultAccount.setVisibility(View.VISIBLE);
        ImageView status = mDefaultAccount.findViewById(R.id.main_account_status);
        TextView address = mDefaultAccount.findViewById(R.id.main_account_address);
        TextView displayName = mDefaultAccount.findViewById(R.id.main_account_display_name);

        ProxyConfig proxy = LinphoneManager.getCore().getDefaultProxyConfig();
        if (proxy == null) {
            displayName.setText(getString(R.string.no_account));
            status.setVisibility(View.GONE);
            address.setText("");
            mDefaultAccount.setOnClickListener(null);
        } else {
            address.setText(proxy.getIdentityAddress().asStringUriOnly());
            displayName.setText(LinphoneUtils.getAddressDisplayName(proxy.getIdentityAddress()));
            status.setImageResource(getStatusIconResource(proxy.getState()));
            status.setVisibility(View.VISIBLE);

            if (!getResources().getBoolean(R.bool.disable_accounts_settings_from_side_menu)) {
                mDefaultAccount.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ((MainActivity) getActivity())
                                        .showAccountSettings(
                                                LinphonePreferences.instance()
                                                        .getDefaultAccountIndex());
                            }
                        });
            }
        }
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

    public void displayAccountsInSideMenu() {
        Core core = LinphoneManager.getCore();
        if (core != null
                && core.getProxyConfigList() != null
                && core.getProxyConfigList().length > 1) {
            mAccountsList.setVisibility(View.VISIBLE);
            mAccountsList.setAdapter(new SideMenuAccountsListAdapter(getActivity()));
            mAccountsList.setOnItemClickListener(
                    new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(
                                AdapterView<?> adapterView, View view, int i, long l) {
                            if (view != null && view.getTag() != null) {
                                int position = Integer.parseInt(view.getTag().toString());
                                ((MainActivity) getActivity()).showAccountSettings(position);
                            }
                        }
                    });
        } else {
            mAccountsList.setVisibility(View.GONE);
        }
        displayMainAccount();
    }

    public interface QuitClikedListener {
        void onQuitClicked();
    }
}
