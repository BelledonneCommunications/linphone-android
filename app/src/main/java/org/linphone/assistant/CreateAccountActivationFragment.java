package org.linphone.assistant;
/*
CreateAccountActivationFragment.java
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
import android.widget.TextView;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class CreateAccountActivationFragment extends Fragment
        implements OnClickListener, AccountCreatorListener {
    private String mUsername, mPassword;
    private Button mCheckAccount;
    private TextView mEmail;
    private AccountCreator mAccountCreator;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view =
                inflater.inflate(
                        R.layout.assistant_account_creation_email_activation, container, false);

        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);

        mUsername = getArguments().getString("Username");
        mPassword = getArguments().getString("Password");

        mAccountCreator.setUsername(mUsername);
        mAccountCreator.setPassword(mPassword);
        mAccountCreator.setDomain(getString(R.string.default_domain));

        mEmail = view.findViewById(R.id.send_email);
        mEmail.setText(getArguments().getString("Email"));

        mCheckAccount = view.findViewById(R.id.assistant_check);
        mCheckAccount.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.assistant_check) {
            mCheckAccount.setEnabled(false);
            AccountCreator.Status status = mAccountCreator.isAccountActivated();
        }
    }

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onCreateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onLinkAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAlias(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountActivated(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(AccountCreator.Status.AccountNotActivated)) {
            Toast.makeText(
                            getActivity(),
                            getString(R.string.assistant_account_not_validated),
                            Toast.LENGTH_LONG)
                    .show();
        } else if (status.equals(AccountCreator.Status.AccountActivated)) {
            AssistantActivity.instance().linphoneLogIn(accountCreator);
            AssistantActivity.instance().isAccountVerified();
        } else {
            Log.w("Unexpected error " + status.name());
            Toast.makeText(
                            getActivity(),
                            getString(R.string.wizard_server_unavailable),
                            Toast.LENGTH_LONG)
                    .show();
        }
        mCheckAccount.setEnabled(true);
    }

    @Override
    public void onRecoverAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAccountLinked(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAliasUsed(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onUpdateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}
}
