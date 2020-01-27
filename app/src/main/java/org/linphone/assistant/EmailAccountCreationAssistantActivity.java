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
package org.linphone.assistant;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.Core;
import org.linphone.core.tools.Log;

public class EmailAccountCreationAssistantActivity extends AssistantActivity {
    private EditText mUsername, mPassword, mPasswordConfirm, mEmail;
    private TextView mCreate, mUsernameError, mPasswordError, mPasswordConfirmError, mEmailError;

    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_email_account_creation);

        mUsernameError = findViewById(R.id.username_error);
        mPasswordError = findViewById(R.id.password_error);
        mPasswordConfirmError = findViewById(R.id.confirm_password_error);
        mEmailError = findViewById(R.id.email_error);

        mUsername = findViewById(R.id.assistant_username);
        mUsername.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        AccountCreator.UsernameStatus status =
                                getAccountCreator().setUsername(s.toString());
                        mUsernameError.setVisibility(
                                status == AccountCreator.UsernameStatus.Ok
                                        ? View.INVISIBLE
                                        : View.VISIBLE);
                        mUsernameError.setText(getErrorFromUsernameStatus(status));
                        updateCreateButton();
                    }
                });

        mPassword = findViewById(R.id.assistant_password);
        mPassword.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        AccountCreator.PasswordStatus status =
                                getAccountCreator().setPassword(s.toString());
                        mPasswordError.setVisibility(
                                status == AccountCreator.PasswordStatus.Ok
                                        ? View.INVISIBLE
                                        : View.VISIBLE);

                        mPasswordConfirmError.setVisibility(
                                s.toString().equals(mPasswordConfirm.getText().toString())
                                        ? View.INVISIBLE
                                        : View.VISIBLE);

                        switch (status) {
                            case InvalidCharacters:
                                mPasswordError.setText(getString(R.string.invalid_characters));
                                break;
                            case TooLong:
                                mPasswordError.setText(getString(R.string.password_too_long));
                                break;
                            case TooShort:
                                mPasswordError.setText(getString(R.string.password_too_short));
                                break;
                        }
                        updateCreateButton();
                    }
                });

        mPasswordConfirm = findViewById(R.id.assistant_password_confirmation);
        mPasswordConfirm.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        mPasswordConfirmError.setVisibility(
                                s.toString().equals(mPassword.getText().toString())
                                        ? View.INVISIBLE
                                        : View.VISIBLE);
                        updateCreateButton();
                    }
                });

        mEmail = findViewById(R.id.assistant_email);
        mEmail.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        AccountCreator.EmailStatus status =
                                getAccountCreator().setEmail(s.toString());
                        mEmailError.setVisibility(
                                status == AccountCreator.EmailStatus.Ok
                                        ? View.INVISIBLE
                                        : View.VISIBLE);
                        updateCreateButton();
                    }
                });

        mCreate = findViewById(R.id.assistant_create);
        mCreate.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        enableButtonsAndFields(false);

                        AccountCreator.Status status = getAccountCreator().isAccountExist();
                        if (status != AccountCreator.Status.RequestOk) {
                            enableButtonsAndFields(true);
                            Log.e(
                                    "[Email Account Creation Assistant] isAccountExists returned "
                                            + status);
                            showGenericErrorDialog(status);
                        }
                    }
                });
        mCreate.setEnabled(false);

        mListener =
                new AccountCreatorListenerStub() {
                    public void onIsAccountExist(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Email Account Creation Assistant] onIsAccountExist status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AccountExist)
                                || status.equals(AccountCreator.Status.AccountExistWithAlias)) {
                            showAccountAlreadyExistsDialog();
                            enableButtonsAndFields(true);
                        } else if (status.equals(AccountCreator.Status.AccountNotExist)) {
                            status = getAccountCreator().createAccount();
                            if (status != AccountCreator.Status.RequestOk) {
                                Log.e(
                                        "[Email Account Creation Assistant] createAccount returned "
                                                + status);
                                enableButtonsAndFields(true);
                                showGenericErrorDialog(status);
                            }
                        } else {
                            enableButtonsAndFields(true);
                            showGenericErrorDialog(status);
                        }
                    }

                    @Override
                    public void onCreateAccount(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Email Account Creation Assistant] onCreateAccount status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AccountCreated)) {
                            startActivity(
                                    new Intent(
                                            EmailAccountCreationAssistantActivity.this,
                                            EmailAccountValidationAssistantActivity.class));
                        } else {
                            enableButtonsAndFields(true);
                            showGenericErrorDialog(status);
                        }
                    }
                };
    }

    private void enableButtonsAndFields(boolean enable) {
        mUsername.setEnabled(enable);
        mPassword.setEnabled(enable);
        mPasswordConfirm.setEnabled(enable);
        mEmail.setEnabled(enable);
        mCreate.setEnabled(enable);
    }

    private void updateCreateButton() {
        mCreate.setEnabled(
                mUsername.getText().length() > 0
                        && mPassword.getText().toString().length() > 0
                        && mEmail.getText().toString().length() > 0
                        && mEmailError.getVisibility() == View.INVISIBLE
                        && mUsernameError.getVisibility() == View.INVISIBLE
                        && mPasswordError.getVisibility() == View.INVISIBLE
                        && mPasswordConfirmError.getVisibility() == View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            reloadLinphoneAccountCreatorConfig();
        }

        getAccountCreator().addListener(mListener);

        if (getResources().getBoolean(R.bool.pre_fill_email_in_assistant)) {
            Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
            for (Account account : accounts) {
                if (Patterns.EMAIL_ADDRESS.matcher(account.name).matches()) {
                    String possibleEmail = account.name;
                    mEmail.setText(possibleEmail);
                    break;
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getAccountCreator().removeListener(mListener);
    }
}
