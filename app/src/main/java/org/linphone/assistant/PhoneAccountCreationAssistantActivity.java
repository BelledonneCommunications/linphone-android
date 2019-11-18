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

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.tools.Log;

public class PhoneAccountCreationAssistantActivity extends AssistantActivity {
    private TextView mCountryPicker, mError, mSipUri, mCreate;
    private EditText mPrefix, mPhoneNumber, mUsername;
    private CheckBox mUseUsernameInsteadOfPhoneNumber;

    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_phone_account_creation);

        mCountryPicker = findViewById(R.id.select_country);
        mCountryPicker.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showCountryPickerDialog();
                    }
                });

        mError = findViewById(R.id.phone_number_error);

        mSipUri = findViewById(R.id.sip_uri);

        mCreate = findViewById(R.id.assistant_create);
        mCreate.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AccountCreator accountCreator = getAccountCreator();
                        enableButtonsAndFields(false);

                        if (mUseUsernameInsteadOfPhoneNumber.isChecked()) {
                            accountCreator.setUsername(mUsername.getText().toString());
                        } else {
                            accountCreator.setUsername(accountCreator.getPhoneNumber());
                        }

                        AccountCreator.Status status = accountCreator.isAccountExist();
                        if (status != AccountCreator.Status.RequestOk) {
                            Log.e(
                                    "[Phone Account Creation Assistant] isAccountExists returned "
                                            + status);
                            enableButtonsAndFields(true);
                            showGenericErrorDialog(status);
                        }
                    }
                });
        mCreate.setEnabled(false);

        mPrefix = findViewById(R.id.dial_code);
        mPrefix.setText("+");
        mPrefix.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        String prefix = s.toString();
                        if (prefix.startsWith("+")) {
                            prefix = prefix.substring(1);
                        }
                        DialPlan dp = getDialPlanFromPrefix(prefix);
                        if (dp != null) {
                            mCountryPicker.setText(dp.getCountry());
                        }

                        updateCreateButtonAndDisplayError();
                    }
                });

        mPhoneNumber = findViewById(R.id.phone_number);
        mPhoneNumber.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateCreateButtonAndDisplayError();
                    }
                });

        ImageView phoneNumberInfos = findViewById(R.id.info_phone_number);
        phoneNumberInfos.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPhoneNumberDialog();
                    }
                });

        mUseUsernameInsteadOfPhoneNumber = findViewById(R.id.use_username);
        mUseUsernameInsteadOfPhoneNumber.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mUsername.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                        updateCreateButtonAndDisplayError();
                    }
                });

        mUsername = findViewById(R.id.username);
        mUsername.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        updateCreateButtonAndDisplayError();
                    }
                });

        mListener =
                new AccountCreatorListenerStub() {
                    public void onIsAccountExist(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Phone Account Creation Assistant] onIsAccountExist status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AccountExist)
                                || status.equals(AccountCreator.Status.AccountExistWithAlias)) {
                            showAccountAlreadyExistsDialog();
                            enableButtonsAndFields(true);
                        } else if (status.equals(AccountCreator.Status.AccountNotExist)) {
                            status = getAccountCreator().createAccount();
                            if (status != AccountCreator.Status.RequestOk) {
                                Log.e(
                                        "[Phone Account Creation Assistant] createAccount returned "
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
                                "[Phone Account Creation Assistant] onCreateAccount status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AccountCreated)) {
                            startActivity(
                                    new Intent(
                                            PhoneAccountCreationAssistantActivity.this,
                                            PhoneAccountValidationAssistantActivity.class));
                        } else {
                            enableButtonsAndFields(true);
                            showGenericErrorDialog(status);
                        }
                    }
                };
    }

    @Override
    protected void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            reloadLinphoneAccountCreatorConfig();
        }

        getAccountCreator().addListener(mListener);

        DialPlan dp = getDialPlanForCurrentCountry();
        displayDialPlan(dp);

        String phoneNumber = getDevicePhoneNumber();
        if (phoneNumber != null) {
            mPhoneNumber.setText(phoneNumber);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        getAccountCreator().removeListener(mListener);
    }

    @Override
    public void onCountryClicked(DialPlan dialPlan) {
        super.onCountryClicked(dialPlan);
        displayDialPlan(dialPlan);
    }

    private void enableButtonsAndFields(boolean enable) {
        mPrefix.setEnabled(enable);
        mPhoneNumber.setEnabled(enable);
        mCreate.setEnabled(enable);
    }

    private void updateCreateButtonAndDisplayError() {
        if (mPrefix.getText().toString().isEmpty() || mPhoneNumber.getText().toString().isEmpty())
            return;

        mCreate.setEnabled(true);
        mError.setText("");
        mError.setVisibility(View.INVISIBLE);

        int status = arePhoneNumberAndPrefixOk(mPrefix, mPhoneNumber);
        if (status == AccountCreator.PhoneNumberStatus.Ok.toInt()) {
            if (mUseUsernameInsteadOfPhoneNumber.isChecked()) {
                AccountCreator.UsernameStatus usernameStatus =
                        getAccountCreator().setUsername(mUsername.getText().toString());
                if (usernameStatus != AccountCreator.UsernameStatus.Ok) {
                    mCreate.setEnabled(false);
                    mError.setText(getErrorFromUsernameStatus(usernameStatus));
                    mError.setVisibility(View.VISIBLE);
                }
            }
        } else {
            mCreate.setEnabled(false);
            mError.setText(getErrorFromPhoneNumberStatus(status));
            mError.setVisibility(View.VISIBLE);
        }

        String username;
        if (mUseUsernameInsteadOfPhoneNumber.isChecked()) {
            username = mUsername.getText().toString();
        } else {
            username = getAccountCreator().getPhoneNumber();
        }

        if (username != null) {
            String sip =
                    getString(R.string.assistant_create_account_phone_number_address)
                            + " <sip:"
                            + username
                            + "@"
                            + getResources().getString(R.string.default_domain)
                            + ">";
            mSipUri.setText(sip);
        }
    }

    private void displayDialPlan(DialPlan dp) {
        if (dp != null) {
            mPrefix.setText("+" + dp.getCountryCallingCode());
            mCountryPicker.setText(dp.getCountry());
        }
    }
}
