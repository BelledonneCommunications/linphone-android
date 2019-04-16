package org.linphone.assistant;
/*
CreateAccountCodeActivationFragment.java
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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.settings.LinphonePreferences;

public class CreateAccountCodeActivationFragment extends Fragment
        implements AccountCreatorListener {
    private String mUsername, mPhone, mDialcode;
    private TextView mTitle, mPhonenumber;
    private EditText mCode;
    private boolean mRecoverAccount = false, mLinkAccount = false;
    private int mCodeLength, mAccountNumber;
    private ImageView mBack;
    private Button mCheckAccount;
    private AccountCreator mAccountCreator;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view =
                inflater.inflate(
                        R.layout.assistant_account_creation_code_activation, container, false);

        mUsername = getArguments().getString("Username");
        mPhone = getArguments().getString("Phone");
        mDialcode = getArguments().getString("Dialcode");
        mRecoverAccount = getArguments().getBoolean("RecoverAccount");
        mLinkAccount = getArguments().getBoolean("LinkAccount");
        mAccountNumber = getArguments().getInt("AccountNumber");

        mCodeLength = LinphonePreferences.instance().getCodeLength();
        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);
        mAccountCreator.setUsername(mUsername);
        mAccountCreator.setPhoneNumber(mPhone, mDialcode);

        mBack = view.findViewById(R.id.back);
        if (mBack != null) mBack.setVisibility(Button.INVISIBLE);

        mTitle = view.findViewById(R.id.title_account_activation);
        if (mLinkAccount) {
            mTitle.setText(getString(R.string.assistant_link_account));
        } else if (mRecoverAccount) {
            mTitle.setText(getString(R.string.assistant_linphone_account));
        }

        mPhonenumber = view.findViewById(R.id.send_phone_number);
        mPhonenumber.setText(mAccountCreator.getPhoneNumber());

        mCode = view.findViewById(R.id.assistant_code);
        mCode.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s.length() == mCodeLength) {
                            mCheckAccount.setEnabled(true);
                        } else {
                            mCheckAccount.setEnabled(false);
                        }
                    }
                });

        mCheckAccount = view.findViewById(R.id.assistant_check);
        mCheckAccount.setEnabled(false);
        mCheckAccount.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mCheckAccount.setEnabled(false);
                        mAccountCreator.setActivationCode(mCode.getText().toString());
                        if (mLinkAccount) {
                            linkAccount();
                        } else {
                            activateAccount();
                        }
                    }
                });

        return view;
    }

    private void linkAccount() {
        mAccountCreator.setUsername(
                LinphonePreferences.instance().getAccountUsername(mAccountNumber));
        mAccountCreator.setHa1(LinphonePreferences.instance().getAccountHa1(mAccountNumber));
        mAccountCreator.activateAlias();
    }

    private void activateAccount() {
        if (mAccountCreator.getUsername() == null) {
            mAccountCreator.setUsername(mAccountCreator.getPhoneNumber());
        }
        mAccountCreator.activateAccount();
    }

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onCreateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(AccountCreator.Status.AccountActivated)) {
            mCheckAccount.setEnabled(true);
            if (accountCreator.getUsername() != null) {
                AssistantActivity.instance().linphoneLogIn(accountCreator);
                if (!mRecoverAccount) {
                    AssistantActivity.instance().isAccountVerified();
                } else {
                    AssistantActivity.instance().success();
                }
            } else {
                AssistantActivity.instance().linphoneLogIn(accountCreator);
                if (!mRecoverAccount) {
                    AssistantActivity.instance().isAccountVerified();
                } else {
                    AssistantActivity.instance().success();
                }
            }
        } else if (status.equals(AccountCreator.Status.RequestFailed)) {
            Toast.makeText(
                            getActivity(),
                            getString(R.string.wizard_server_unavailable),
                            Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(
                            getActivity(),
                            getString(R.string.assistant_error_confirmation_code),
                            Toast.LENGTH_LONG)
                    .show();
            AssistantActivity.instance().displayAssistantLinphoneLogin(mPhone, mDialcode);
        }
    }

    @Override
    public void onLinkAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onActivateAlias(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(AccountCreator.Status.AccountActivated)) {
            LinphonePreferences.instance()
                    .setPrefix(
                            mAccountNumber,
                            org.linphone.core.Utils.getPrefixFromE164(
                                    accountCreator.getPhoneNumber()));
            LinphonePreferences.instance().setLinkPopupTime("");
            AssistantActivity.instance().hideKeyboard();
            AssistantActivity.instance().success();
        }
    }

    @Override
    public void onIsAccountActivated(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

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
