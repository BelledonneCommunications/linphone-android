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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListenerStub;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;

public class PhoneAccountLinkingAssistantActivity extends AssistantActivity {
    private TextView mCountryPicker, mError, mLink;
    private EditText mPrefix, mPhoneNumber;

    private AccountCreatorListenerStub mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_phone_account_linking);

        if (getIntent() != null && getIntent().hasExtra("AccountNumber")) {
            int proxyConfigIndex = getIntent().getExtras().getInt("AccountNumber");
            Core core = LinphoneManager.getCore();
            if (core == null) {
                Log.e("[Account Linking Assistant] Core not available");
                unexpectedError();
                return;
            }

            ProxyConfig[] proxyConfigs = core.getProxyConfigList();
            if (proxyConfigIndex >= 0 && proxyConfigIndex < proxyConfigs.length) {
                ProxyConfig mProxyConfig = proxyConfigs[proxyConfigIndex];
                AccountCreator accountCreator = getAccountCreator();

                Address identity = mProxyConfig.getIdentityAddress();
                if (identity == null) {
                    Log.e("[Account Linking Assistant] Proxy doesn't have an identity address");
                    unexpectedError();
                    return;
                }
                if (!mProxyConfig.getDomain().equals(getString(R.string.default_domain))) {
                    Log.e(
                            "[Account Linking Assistant] Can't link account on domain "
                                    + mProxyConfig.getDomain());
                    unexpectedError();
                    return;
                }
                accountCreator.setUsername(identity.getUsername());

                AuthInfo authInfo = mProxyConfig.findAuthInfo();
                if (authInfo == null) {
                    Log.e("[Account Linking Assistant] Auth info not found");
                    unexpectedError();
                    return;
                }
                accountCreator.setHa1(authInfo.getHa1());
                accountCreator.setAlgorithm((authInfo.getAlgorithm()));
            } else {
                Log.e(
                        "[Account Linking Assistant] Proxy config index out of bounds: "
                                + proxyConfigIndex);
                unexpectedError();
                return;
            }
        } else {
            Log.e("[Account Linking Assistant] Proxy config index not found");
            unexpectedError();
            return;
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

        mLink = findViewById(R.id.assistant_link);
        mLink.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        enableButtonsAndFields(false);

                        AccountCreator.Status status = getAccountCreator().isAliasUsed();
                        if (status != AccountCreator.Status.RequestOk) {
                            Log.e(
                                    "[Phone Account Linking Assistant] isAliasUsed returned "
                                            + status);
                            enableButtonsAndFields(true);
                            showGenericErrorDialog(status);
                        }
                    }
                });
        mLink.setEnabled(false);

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

        mListener =
                new AccountCreatorListenerStub() {
                    @Override
                    public void onIsAliasUsed(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Phone Account Linking Assistant] onIsAliasUsed status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.AliasNotExist)) {
                            status = getAccountCreator().linkAccount();
                            if (status != AccountCreator.Status.RequestOk) {
                                Log.e(
                                        "[Phone Account Linking Assistant] linkAccount returned "
                                                + status);
                                enableButtonsAndFields(true);
                                showGenericErrorDialog(status);
                            }
                        } else {
                            if (status.equals(AccountCreator.Status.AliasIsAccount)
                                    || status.equals(AccountCreator.Status.AliasExist)) {
                                showAccountAlreadyExistsDialog();
                            } else {
                                showGenericErrorDialog(status);
                            }
                            enableButtonsAndFields(true);
                        }
                    }

                    @Override
                    public void onLinkAccount(
                            AccountCreator creator, AccountCreator.Status status, String resp) {
                        Log.i(
                                "[Phone Account Linking Assistant] onLinkAccount status is "
                                        + status);
                        if (status.equals(AccountCreator.Status.RequestOk)) {
                            Intent intent =
                                    new Intent(
                                            PhoneAccountLinkingAssistantActivity.this,
                                            PhoneAccountValidationAssistantActivity.class);
                            intent.putExtra("isLinkingVerification", true);
                            startActivity(intent);
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
        mLink.setEnabled(enable);
    }

    private void updateCreateButtonAndDisplayError() {
        if (mPrefix.getText().toString().isEmpty() || mPhoneNumber.getText().toString().isEmpty())
            return;

        int status = arePhoneNumberAndPrefixOk(mPrefix, mPhoneNumber);
        if (status == AccountCreator.PhoneNumberStatus.Ok.toInt()) {
            mLink.setEnabled(true);
            mError.setText("");
            mError.setVisibility(View.INVISIBLE);
        } else {
            mLink.setEnabled(false);
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

    private void unexpectedError() {
        Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_SHORT).show();
        finish();
    }
}
