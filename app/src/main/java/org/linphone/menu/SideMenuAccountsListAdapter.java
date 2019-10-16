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
package org.linphone.menu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

class SideMenuAccountsListAdapter extends BaseAdapter {
    private final Context mContext;
    private List<ProxyConfig> proxy_list;

    SideMenuAccountsListAdapter(Context context) {
        mContext = context;
        proxy_list = new ArrayList<>();
        refresh();
    }

    private void refresh() {
        proxy_list = new ArrayList<>();
        Core core = LinphoneManager.getCore();
        for (ProxyConfig proxyConfig : core.getProxyConfigList()) {
            if (proxyConfig != core.getDefaultProxyConfig()) {
                proxy_list.add(proxyConfig);
            }
        }
    }

    public int getCount() {
        if (proxy_list != null) {
            return proxy_list.size();
        } else {
            return 0;
        }
    }

    public Object getItem(int position) {
        return proxy_list.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(final int position, View convertView, ViewGroup parent) {
        View view;
        ProxyConfig lpc = (ProxyConfig) getItem(position);
        if (convertView != null) {
            view = convertView;
        } else {
            view =
                    LayoutInflater.from(mContext)
                            .inflate(R.layout.side_menu_account_cell, parent, false);
        }

        ImageView status = view.findViewById(R.id.account_status);
        TextView address = view.findViewById(R.id.account_address);
        String sipAddress = lpc.getIdentityAddress().asStringUriOnly();

        address.setText(sipAddress);

        int nbAccounts = LinphonePreferences.instance().getAccountCount();
        int accountIndex;

        for (int i = 0; i < nbAccounts; i++) {
            String username = LinphonePreferences.instance().getAccountUsername(i);
            String domain = LinphonePreferences.instance().getAccountDomain(i);
            String id = "sip:" + username + "@" + domain;
            if (id.equals(sipAddress)) {
                accountIndex = i;
                view.setTag(accountIndex);
                break;
            }
        }
        status.setImageResource(getStatusIconResource(lpc.getState()));
        return view;
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
}
