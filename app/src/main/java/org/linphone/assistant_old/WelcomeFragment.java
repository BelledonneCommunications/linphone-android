package org.linphone.assistant_old;
/*
WelcomeFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import org.linphone.R;

public class WelcomeFragment extends Fragment implements OnClickListener {
    private Button mCreateAccount, mLogLinphoneAccount, mLogGenericAccount, mRemoteProvisioning;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.assistant_welcome, container, false);

        mCreateAccount = view.findViewById(R.id.create_account);
        mCreateAccount.setOnClickListener(this);

        mLogLinphoneAccount = view.findViewById(R.id.login_linphone);
        if (getResources().getBoolean(R.bool.hide_linphone_accounts_in_assistant)) {
            mLogLinphoneAccount.setVisibility(View.GONE);
        } else {
            mLogLinphoneAccount.setOnClickListener(this);
        }

        mLogGenericAccount = view.findViewById(R.id.login_generic);
        if (getResources().getBoolean(R.bool.hide_generic_accounts_in_assistant)) {
            mLogGenericAccount.setVisibility(View.GONE);
        } else {
            mLogGenericAccount.setOnClickListener(this);
        }

        mRemoteProvisioning = view.findViewById(R.id.remote_provisioning);
        if (getResources().getBoolean(R.bool.hide_remote_provisioning_in_assistant)) {
            mRemoteProvisioning.setVisibility(View.GONE);
        } else {
            mRemoteProvisioning.setOnClickListener(this);
        }

        return view;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.login_generic) {
            AssistantActivity.instance().displayLoginGeneric();
        } else if (id == R.id.login_linphone) {
            AssistantActivity.instance().displayLoginLinphone(null, null);
        } else if (id == R.id.create_account) {
            AssistantActivity.instance().displayCreateAccount();
        } else if (id == R.id.remote_provisioning) {
            AssistantActivity.instance().displayRemoteProvisioning("");
        }
    }
}
