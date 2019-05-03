package org.linphone.assistant;
/*
LinphoneLoginFragment.java
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

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.DialPlan;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class LinphoneLoginFragment extends Fragment
        implements CompoundButton.OnCheckedChangeListener,
                OnClickListener,
                TextWatcher,
                AccountCreatorListener {
    private EditText mLogin, mPassword, mPhoneNumberEdit, mDialCode;
    private Button mApply, mSelectCountry;
    private CheckBox mUseUsername;
    private LinearLayout mPhoneNumberLayout, mUsernameLayout, mPasswordLayout;
    private TextView mForgotPassword, mMessagePhoneNumber, mPhoneNumberError;
    private AccountCreator mAccountCreator;
    private int mCountryCode;
    private String mPhone, mUsername, mPwd;
    private ImageView mPhoneNumberInfo;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.assistant_linphone_login, container, false);

        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);

        mLogin = view.findViewById(R.id.assistant_username);
        mLogin.addTextChangedListener(this);

        mDialCode = view.findViewById(R.id.dial_code);

        mPhoneNumberEdit = view.findViewById(R.id.phone_number);
        mPhoneNumberLayout = view.findViewById(R.id.phone_number_layout);
        mPhoneNumberError = view.findViewById(R.id.phone_number_error_2);

        mPhoneNumberInfo = view.findViewById(R.id.info_phone_number);

        mUseUsername = view.findViewById(R.id.use_username);
        mUsernameLayout = view.findViewById(R.id.username_layout);
        mPasswordLayout = view.findViewById(R.id.password_layout);
        mPassword = view.findViewById(R.id.assistant_password);
        mMessagePhoneNumber = view.findViewById(R.id.message_phone_number);

        mForgotPassword = view.findViewById(R.id.forgot_password);
        mSelectCountry = view.findViewById(R.id.select_country);

        mApply = view.findViewById(R.id.assistant_apply);
        mApply.setEnabled(true);
        mApply.setOnClickListener(this);

        // Phone number
        if (getResources().getBoolean(R.bool.use_phone_number_validation)) {
            mMessagePhoneNumber.setText(getString(R.string.assistant_create_account_part_1));
            mPhone = getArguments().getString("Phone");
            String prefix = getArguments().getString("Dialcode");

            getActivity().getApplicationContext();
            // Automatically get the country code from the mPhone
            TelephonyManager tm =
                    (TelephonyManager)
                            getActivity()
                                    .getApplicationContext()
                                    .getSystemService(Context.TELEPHONY_SERVICE);
            String countryIso = tm.getNetworkCountryIso();
            mCountryCode = org.linphone.core.Utils.getCccFromIso(countryIso.toUpperCase());

            DialPlan c = AssistantActivity.instance().country;
            if (c != null) {
                mSelectCountry.setText(c.getCountry());
                mDialCode.setText(
                        c.getCountryCallingCode().contains("+")
                                ? c.getCountryCallingCode()
                                : "+" + c.getCountryCallingCode());
            } else {
                c =
                        AssistantActivity.instance()
                                .getCountryListAdapter()
                                .getCountryFromCountryCode(String.valueOf(mCountryCode));
                if (c != null) {
                    mSelectCountry.setText(c.getCountry());
                    mDialCode.setText(
                            c.getCountryCallingCode().contains("+")
                                    ? c.getCountryCallingCode()
                                    : "+" + c.getCountryCallingCode());
                }
            }

            mPhoneNumberLayout.setVisibility(View.VISIBLE);
            mSelectCountry.setOnClickListener(this);
            mPhoneNumberInfo.setOnClickListener(this);

            // Allow user to enter a mUsername instead use the mPhone number as mUsername
            if (getResources().getBoolean(R.bool.assistant_allow_username)) {
                mUseUsername.setVisibility(View.VISIBLE);
                mUseUsername.setOnCheckedChangeListener(this);
            }

            if (mPhone != null) mPhoneNumberEdit.setText(mPhone);
            if (prefix != null) mDialCode.setText("+" + prefix);
        }

        if (getResources().getBoolean(R.bool.assistant_allow_username)) {
            mUseUsername.setVisibility(View.VISIBLE);
            mUseUsername.setOnCheckedChangeListener(this);
            mPassword.addTextChangedListener(this);
            mForgotPassword.setText(
                    Html.fromHtml(
                            "<a href=\""
                                    + getString(R.string.recover_password_link)
                                    + "\"'>"
                                    + getString(R.string.forgot_password)
                                    + "</a>"));
            mForgotPassword.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // Hide mPhone number and display mUsername/email/mPassword
        if (!getResources().getBoolean(R.bool.use_phone_number_validation)) {
            mPhoneNumberLayout.setVisibility(View.GONE);
            mUseUsername.setVisibility(View.GONE);

            mUsernameLayout.setVisibility(View.VISIBLE);
            mPasswordLayout.setVisibility(View.VISIBLE);
        }

        // When we come from generic mLogin fragment
        mUsername = getArguments().getString("Username");
        mPwd = getArguments().getString("Password");
        if (mUsername != null && mPwd != null) {
            mUseUsername.setChecked(true);
            onCheckedChanged(mUseUsername, true);
            mLogin.setText(mUsername);
            mPassword.setText(mPwd);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAccountCreator.setLanguage(Locale.getDefault().toLanguageTag());
        }

        addPhoneNumberHandler(mDialCode);
        addPhoneNumberHandler(mPhoneNumberEdit);

        return view;
    }

    private void linphoneLogIn() {
        if (mLogin.getText() == null
                || mLogin.length() == 0
                || mPassword.getText() == null
                || mPassword.length() == 0) {
            LinphoneUtils.displayErrorAlert(
                    getString(R.string.first_launch_no_login_password),
                    AssistantActivity.instance());
            mApply.setEnabled(true);
            return;
        }
        mAccountCreator.setUsername(mLogin.getText().toString());
        mAccountCreator.setPassword(mPassword.getText().toString());
        mAccountCreator.setDomain(getString(R.string.default_domain));
        mAccountCreator.isAccountExist();
    }

    private int getPhoneNumberStatus() {
        mAccountCreator.setDomain(getString(R.string.default_domain));
        return mAccountCreator.setPhoneNumber(
                mPhoneNumberEdit.getText().toString(), LinphoneUtils.getCountryCode(mDialCode));
    }

    private void addPhoneNumberHandler(final EditText field) {
        field.addTextChangedListener(
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        if (field.equals(mDialCode)) {
                            DialPlan c =
                                    AssistantActivity.instance()
                                            .getCountryListAdapter()
                                            .getCountryFromCountryCode(
                                                    mDialCode.getText().toString());
                            if (c != null) {
                                AssistantActivity.instance().country = c;
                                mSelectCountry.setText(c.getCountry());
                            } else {
                                mSelectCountry.setText(R.string.select_your_country);
                            }
                        }
                    }

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {
                        onTextChanged2();
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.assistant_apply) {
            mApply.setEnabled(false);
            if (mUseUsername == null || !mUseUsername.isChecked()) {
                recoverAccount();
            } else {
                linphoneLogIn();
            }
        } else if (id == R.id.info_phone_number) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.phone_number_info_title))
                    .setMessage(getString(R.string.phone_number_link_info_content))
                    .show();
        } else if (id == R.id.select_country) {
            AssistantActivity.instance().displayCountryChooser();
        }
    }

    private void recoverAccount() {
        if (mPhoneNumberEdit.getText().length() > 0 || mDialCode.getText().length() > 1) {
            int status = getPhoneNumberStatus();
            boolean isOk = status == AccountCreator.PhoneNumberStatus.Ok.toInt();
            if (isOk) {
                mAccountCreator.isAliasUsed();
            } else {
                mApply.setEnabled(true);
                LinphoneUtils.displayErrorAlert(
                        LinphoneUtils.errorForPhoneNumberStatus(status),
                        AssistantActivity.instance());
                LinphoneUtils.displayError(
                        isOk, mPhoneNumberError, LinphoneUtils.errorForPhoneNumberStatus(status));
            }
        } else {
            mApply.setEnabled(true);
            LinphoneUtils.displayErrorAlert(
                    getString(R.string.assistant_create_account_part_1),
                    AssistantActivity.instance());
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    private void onTextChanged2() {
        int status = getPhoneNumberStatus();
        boolean isOk = status == AccountCreator.PhoneNumberStatus.Ok.toInt();
        LinphoneUtils.displayError(
                isOk, mPhoneNumberError, LinphoneUtils.errorForPhoneNumberStatus(status));
        if (!isOk) {
            if ((1 == (status & AccountCreator.PhoneNumberStatus.InvalidCountryCode.toInt()))) {
                mDialCode.setBackgroundResource(R.drawable.resizable_textfield_error);
                mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
            } else {
                mDialCode.setBackgroundResource(R.drawable.resizable_textfield);
                mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield_error);
            }
        } else {
            mAccountCreator.setPhoneNumber(
                    mPhoneNumberEdit.getText().toString(), mDialCode.getText().toString());
            mDialCode.setBackgroundResource(R.drawable.resizable_textfield);
            mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        onTextChanged2();
    }

    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.use_username) {
            if (isChecked) {
                mUsernameLayout.setVisibility(View.VISIBLE);
                mPasswordLayout.setVisibility(View.VISIBLE);
                mPhoneNumberEdit.setVisibility(EditText.GONE);
                mPhoneNumberLayout.setVisibility(LinearLayout.GONE);
                mMessagePhoneNumber.setText(getString(R.string.assistant_linphone_login_desc));
            } else {
                mUsernameLayout.setVisibility(View.GONE);
                mPasswordLayout.setVisibility(View.GONE);
                mPhoneNumberEdit.setVisibility(EditText.VISIBLE);
                mPhoneNumberLayout.setVisibility(LinearLayout.VISIBLE);
                mMessagePhoneNumber.setText(getString(R.string.assistant_create_account_part_1));
            }
        }
    }

    @Override
    public void onIsAccountExist(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            mApply.setEnabled(true);
            return;
        }
        if (status.equals(AccountCreator.Status.AccountExist)
                || status.equals(AccountCreator.Status.AccountExistWithAlias)) {
            AssistantActivity.instance().linphoneLogIn(accountCreator);
        } else {
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
        }
        mApply.setEnabled(true);
    }

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
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onRecoverAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            mApply.setEnabled(true);
            return;
        }
        if (status.equals(AccountCreator.Status.ServerError)) {
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(AccountCreator.Status.RequestFailed),
                    AssistantActivity.instance());
            mApply.setEnabled(true);
        } else {
            AssistantActivity.instance()
                    .displayAssistantCodeConfirm(
                            accountCreator.getUsername(),
                            mPhoneNumberEdit.getText().toString(),
                            LinphoneUtils.getCountryCode(mDialCode),
                            true);
        }
    }

    @Override
    public void onIsAccountLinked(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}

    @Override
    public void onIsAliasUsed(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            mApply.setEnabled(true);
            return;
        }
        if (status.equals(AccountCreator.Status.AliasIsAccount)
                || status.equals(AccountCreator.Status.AliasExist)) {
            accountCreator.recoverAccount();
        } else {
            mApply.setEnabled(true);
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
        }
    }

    @Override
    public void onUpdateAccount(
            AccountCreator accountCreator, AccountCreator.Status status, String resp) {}
}
