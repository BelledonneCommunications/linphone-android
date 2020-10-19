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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.tools.Log;

public class AccountConnectionAssistantActivity extends AssistantActivity {
    private RelativeLayout mPhoneNumberConnection, mUsernameConnection;
    private Switch mUsernameConnectionSwitch;
    private EditText mPrefix, mPhoneNumber, mUsername, mPassword;
    private TextView mCountryPicker, mError, mConnect;

    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_account_connection);

        mPhoneNumberConnection = findViewById(R.id.phone_number_form);

        mUsernameConnection = findViewById(R.id.username_form);

        mUsernameConnectionSwitch = findViewById(R.id.username_login);
        mUsernameConnectionSwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mPhoneNumberConnection.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                        mUsernameConnection.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                });

        mConnect = findViewById(R.id.assistant_login);
        mConnect.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AccountCreator accountCreator = getAccountCreator();
                        accountCreator.reset();
                        mConnect.setEnabled(false);

                        if (mUsernameConnectionSwitch.isChecked()) {
                            accountCreator.setUsername(mUsername.getText().toString());
                            accountCreator.setPassword(mPassword.getText().toString());

                            createProxyConfigAndLeaveAssistant();
                        } else {
                            accountCreator.setPhoneNumber(
                                    mPhoneNumber.getText().toString(),
                                    mPrefix.getText().toString());
                            accountCreator.setUsername(accountCreator.getPhoneNumber());

                            AccountCreator.Status status = accountCreator.recoverAccount();
                            if (status != AccountCreator.Status.RequestOk) {
                                Log.e(
                                        "[Account Connection Assistant] recoverAccount returned "
                                                + status);
                                mConnect.setEnabled(true);
                                showGenericErrorDialog(status);
                            }
                        }
                    }
                });
        mConnect.setEnabled(false);

        if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
            if (getResources().getBoolean(R.bool.isTablet)) {
                mUsernameConnectionSwitch.setChecked(true);
            } else {
                mUsernameConnection.setVisibility(View.GONE);
            }
        } else {
            mUsernameConnectionSwitch.setChecked(true);
            mPhoneNumberConnection.setVisibility(View.GONE);
            findViewById(R.id.username_switch_layout).setVisibility(View.GONE);
        }

        mCountryPicker = findViewById(R.id.select_country);
        mCountryPicker.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showCountryPickerDialog();
                    }
                });

        mError = findViewById(R.id.phone_number_error);

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

                        updateConnectButtonAndDisplayError();
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
                        updateConnectButtonAndDisplayError();
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
                        mConnect.setEnabled(s.length() > 0 && mPassword.getText().length() > 0);
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
                        mConnect.setEnabled(s.length() > 0 && mUsername.getText().length() > 0);
                    }
                });

        mListener =
                new AccountCreatorListenerStub() {
                    @Override
                    public void onRecoverAccount(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Account Connection Assistant] onRecoverAccount status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.RequestOk)) {
                            Intent intent =
                                    new Intent(
                                            AccountConnectionAssistantActivity.this,
                                            PhoneAccountValidationAssistantActivity.class);
                            intent.putExtra("isLoginVerification", true);
                            startActivity(intent);
                        } else {
                            mConnect.setEnabled(true);
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

    private void updateConnectButtonAndDisplayError() {
        if (mPrefix.getText().toString().isEmpty() || mPhoneNumber.getText().toString().isEmpty())
            return;

        int status = arePhoneNumberAndPrefixOk(mPrefix, mPhoneNumber);
        if (status == AccountCreator.PhoneNumberStatus.Ok.toInt()) {
            mConnect.setEnabled(true);
            mError.setText("");
            mError.setVisibility(View.INVISIBLE);
        } else {
            mConnect.setEnabled(false);
            mError.setText(getErrorFromPhoneNumberStatus(status));
            mError.setVisibility(View.VISIBLE);
        }
    }

    private void displayDialPlan(DialPlan dp) {
        if (dp != null) {
            mPrefix.setText("+" + dp.getCountryCallingCode());
            mCountryPicker.setText(dp.getCountry());
        }
    }
}
