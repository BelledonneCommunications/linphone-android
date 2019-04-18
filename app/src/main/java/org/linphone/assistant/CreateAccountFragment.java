package org.linphone.assistant;
/*
CreateAccountFragment.java
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.AccountCreator.Status;
import org.linphone.core.AccountCreatorListener;
import org.linphone.core.DialPlan;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class CreateAccountFragment extends Fragment
        implements CompoundButton.OnCheckedChangeListener, OnClickListener, AccountCreatorListener {
    private final Pattern UPPER_CASE_REGEX = Pattern.compile("[A-Z]");

    private EditText mPhoneNumberEdit,
            mUsernameEdit,
            mPasswordEdit,
            mPasswordConfirmEdit,
            mEmailEdit,
            mDialCode;
    private TextView mPhoneNumberError,
            mPasswordError,
            mPasswordConfirmError,
            mEmailError,
            mAssisstantTitle,
            mSipUri,
            mSkip,
            mInstruction;
    private ImageView mPhoneNumberInfo;
    private boolean mPasswordOk = false;
    private boolean mEmailOk = false;
    private boolean mConfirmPasswordOk = false;
    private boolean mLinkAccount = false;
    private Button mCreateAccount, mSelectCountry;
    private CheckBox mUseUsername, mUseEmail;
    private String mAddressSip = "";
    private int mCountryCode;
    private LinearLayout mPhoneNumberLayout,
            mUsernameLayout,
            mEmailLayout,
            mPasswordLayout,
            mPasswordConfirmLayout;
    private AccountCreator mAccountCreator;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.assistant_account_creation, container, false);

        // Initialize mAccountCreator
        mAccountCreator =
                LinphoneManager.getLc()
                        .createAccountCreator(LinphonePreferences.instance().getXmlrpcUrl());
        mAccountCreator.setListener(this);
        mAccountCreator.setDomain(getString(R.string.default_domain));

        mInstruction = view.findViewById(R.id.message_create_account);

        mCreateAccount = view.findViewById(R.id.assistant_create);

        mPhoneNumberLayout = view.findViewById(R.id.phone_number_layout);
        mUsernameLayout = view.findViewById(R.id.username_layout);
        mEmailLayout = view.findViewById(R.id.email_layout);
        mPasswordLayout = view.findViewById(R.id.password_layout);
        mPasswordConfirmLayout = view.findViewById(R.id.password_confirm_layout);

        mUseUsername = view.findViewById(R.id.use_username);
        mUseEmail = view.findViewById(R.id.use_email);

        mUsernameEdit = view.findViewById(R.id.username);

        mPhoneNumberError = view.findViewById(R.id.phone_number_error);
        mPhoneNumberEdit = view.findViewById(R.id.phone_number);
        mSipUri = view.findViewById(R.id.sip_uri);

        mPhoneNumberInfo = view.findViewById(R.id.info_phone_number);

        mSelectCountry = view.findViewById(R.id.select_country);
        mDialCode = view.findViewById(R.id.dial_code);
        mAssisstantTitle = view.findViewById(R.id.assistant_title);

        mPasswordError = view.findViewById(R.id.password_error);
        mPasswordEdit = view.findViewById(R.id.password);

        mPasswordConfirmError = view.findViewById(R.id.confirm_password_error);
        mPasswordConfirmEdit = view.findViewById(R.id.confirm_password);

        mEmailError = view.findViewById(R.id.email_error);
        mEmailEdit = view.findViewById(R.id.email);

        mSkip = view.findViewById(R.id.assistant_skip);

        // Phone number
        if (!getResources().getBoolean(R.bool.isTablet)
                && getResources().getBoolean(R.bool.use_phone_number_validation)) {
            getActivity().getApplicationContext();
            // Automatically get the country code from the phone
            TelephonyManager tm =
                    (TelephonyManager)
                            getActivity()
                                    .getApplicationContext()
                                    .getSystemService(Context.TELEPHONY_SERVICE);
            String countryIso = tm.getNetworkCountryIso();
            mCountryCode = org.linphone.core.Utils.getCccFromIso(countryIso.toUpperCase());

            mPhoneNumberLayout.setVisibility(View.VISIBLE);

            mPhoneNumberInfo.setOnClickListener(this);
            mSelectCountry.setOnClickListener(this);

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

            // Allow user to enter a username instead use the phone number as username
            if (getResources().getBoolean(R.bool.assistant_allow_username)) {
                mUseUsername.setVisibility(View.VISIBLE);
                mUseUsername.setOnCheckedChangeListener(this);
            }
            addPhoneNumberHandler(mPhoneNumberEdit);
            addPhoneNumberHandler(mDialCode);
        } else {
            if (!getResources().getBoolean(R.bool.isTablet)) {
                mUseEmail.setVisibility(View.VISIBLE);
            }
            mUseEmail.setOnCheckedChangeListener(this);

            if (getResources().getBoolean(R.bool.pre_fill_email_in_assistant)) {
                Account[] accounts =
                        AccountManager.get(getActivity()).getAccountsByType("com.google");

                for (Account account : accounts) {
                    if (isEmailCorrect(account.name)) {
                        String possibleEmail = account.name;
                        mEmailEdit.setText(possibleEmail);
                        mAccountCreator.setEmail(possibleEmail);
                        mEmailOk = true;
                        break;
                    }
                }
            }

            addPasswordHandler(mPasswordEdit);
            addConfirmPasswordHandler(mPasswordEdit, mPasswordConfirmEdit);
            addEmailHandler(mEmailEdit);
        }

        // Hide phone number and display username/email/password
        if (!getResources().getBoolean(R.bool.use_phone_number_validation)) {
            mUseEmail.setVisibility(View.GONE);
            mUseUsername.setVisibility(View.GONE);

            mUsernameLayout.setVisibility(View.VISIBLE);
            mPasswordLayout.setVisibility(View.VISIBLE);
            mPasswordConfirmLayout.setVisibility(View.VISIBLE);
            mEmailLayout.setVisibility(View.VISIBLE);
        }

        // Link account with phone number
        if (getArguments().getBoolean("LinkPhoneNumber")) {
            mLinkAccount = true;
            mUseEmail.setVisibility(View.GONE);
            mUseUsername.setVisibility(View.GONE);

            mUsernameLayout.setVisibility(View.GONE);
            mPasswordLayout.setVisibility(View.GONE);
            mPasswordConfirmLayout.setVisibility(View.GONE);
            mEmailLayout.setVisibility(View.GONE);

            mSkip.setVisibility(View.VISIBLE);
            mSkip.setOnClickListener(this);

            mCreateAccount.setText(getResources().getString(R.string.link_account));
            mAssisstantTitle.setText(getResources().getString(R.string.link_account));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mAccountCreator.setLanguage(Locale.getDefault().toLanguageTag());
        }

        addUsernameHandler(mUsernameEdit);

        mCreateAccount.setEnabled(true);
        mCreateAccount.setOnClickListener(this);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        mAccountCreator.setListener(null);
    }

    private String getUsername() {
        if (mUsernameEdit != null) {
            String username = mUsernameEdit.getText().toString();
            return username.toLowerCase(Locale.getDefault());
        }
        return null;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.getId() == R.id.use_username) {
            if (isChecked) {
                mUsernameLayout.setVisibility(View.VISIBLE);
                onTextChanged2();
            } else {
                mUsernameLayout.setVisibility(View.GONE);
                mAccountCreator.setUsername(null);
                onTextChanged2();
            }
        } else if (buttonView.getId() == R.id.use_email) {
            if (isChecked) {
                mDialCode.setBackgroundResource(R.drawable.resizable_textfield);
                mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
                mUseUsername.setEnabled(false);
                mDialCode.setEnabled(false);
                mSelectCountry.setEnabled(false);
                mPhoneNumberEdit.setEnabled(false);
                mEmailLayout.setVisibility(View.VISIBLE);
                mPasswordLayout.setVisibility(View.VISIBLE);
                mPasswordConfirmLayout.setVisibility(View.VISIBLE);
                mUsernameLayout.setVisibility(View.VISIBLE);
                mUseUsername.setVisibility(CheckBox.GONE);
                mPhoneNumberLayout.setVisibility(LinearLayout.GONE);
                mInstruction.setText(getString(R.string.assistant_create_account_part_email));
            } else {
                if (!mUseUsername.isChecked()) {
                    mUsernameLayout.setVisibility(View.GONE);
                }
                mUseUsername.setEnabled(true);
                mDialCode.setEnabled(true);
                mSelectCountry.setEnabled(true);
                mPhoneNumberEdit.setEnabled(true);
                mEmailLayout.setVisibility(View.GONE);
                mPasswordLayout.setVisibility(View.GONE);
                mPasswordConfirmLayout.setVisibility(View.GONE);
                mUseUsername.setVisibility(CheckBox.VISIBLE);
                mPhoneNumberLayout.setVisibility(LinearLayout.VISIBLE);
                mInstruction.setText(getString(R.string.assistant_create_account_part_1));
            }
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.select_country) {
            AssistantActivity.instance().displayCountryChooser();
        } else if (id == R.id.assistant_skip) {
            if (getArguments().getBoolean("LinkFromPref")) {
                AssistantActivity.instance().finish();
            } else {
                AssistantActivity.instance().success();
            }
        } else if (id == R.id.info_phone_number) {
            if (mLinkAccount) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.phone_number_info_title))
                        .setMessage(
                                getString(R.string.phone_number_link_info_content)
                                        + "\n"
                                        + getString(
                                                R.string
                                                        .phone_number_link_info_content_already_account))
                        .show();
            } else {
                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.phone_number_info_title))
                        .setMessage(getString(R.string.phone_number_info_content))
                        .show();
            }
        } else if (id == R.id.assistant_create) {
            mCreateAccount.setEnabled(false);
            if (mLinkAccount) {
                addAlias();
            } else {
                if (mUseEmail.isChecked()) mAccountCreator.setPhoneNumber(null, null);
                if (getUsername().length() > 0) {
                    mAccountCreator.isAccountExist();
                } else {
                    LinphoneUtils.displayErrorAlert(
                            LinphoneUtils.errorForUsernameStatus(
                                    AccountCreator.UsernameStatus.TooShort),
                            AssistantActivity.instance());
                    mCreateAccount.setEnabled(true);
                }
            }
        }
    }

    private boolean isEmailCorrect(String email) {
        Pattern emailPattern = Patterns.EMAIL_ADDRESS;
        return emailPattern.matcher(email).matches();
    }

    private boolean isPasswordCorrect(String password) {
        return password.length() >= 1;
    }

    private void addAlias() {
        mAccountCreator.setUsername(
                LinphonePreferences.instance()
                        .getAccountUsername(
                                LinphonePreferences.instance().getDefaultAccountIndex()));
        int status =
                mAccountCreator.setPhoneNumber(
                        mPhoneNumberEdit.getText().toString(),
                        LinphoneUtils.getCountryCode(mDialCode));
        boolean isOk = status == AccountCreator.PhoneNumberStatus.Ok.toInt();
        if (isOk) {
            mAccountCreator.linkAccount();
        } else {
            mCreateAccount.setEnabled(true);
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForPhoneNumberStatus(status), AssistantActivity.instance());
            LinphoneUtils.displayError(
                    isOk, mPhoneNumberError, LinphoneUtils.errorForPhoneNumberStatus(status));
        }
    }

    private void createAccount() {
        if ((getResources().getBoolean(R.bool.isTablet)
                        || !getResources().getBoolean(R.bool.use_phone_number_validation))
                && mUseEmail.isChecked()) {
            AccountCreator.EmailStatus emailStatus;
            AccountCreator.PasswordStatus passwordStatus;

            passwordStatus = mAccountCreator.setPassword(mPasswordEdit.getText().toString());
            emailStatus = mAccountCreator.setEmail(mEmailEdit.getText().toString());

            if (!mEmailOk) {
                LinphoneUtils.displayError(
                        false, mEmailError, LinphoneUtils.errorForEmailStatus(emailStatus));
                LinphoneUtils.displayErrorAlert(
                        LinphoneUtils.errorForEmailStatus(emailStatus),
                        AssistantActivity.instance());
            } else if (!mPasswordOk) {
                LinphoneUtils.displayError(
                        false,
                        mPasswordError,
                        LinphoneUtils.errorForPasswordStatus(passwordStatus));
                LinphoneUtils.displayErrorAlert(
                        LinphoneUtils.errorForPasswordStatus(passwordStatus),
                        AssistantActivity.instance());
            } else if (!mConfirmPasswordOk) {
                String msg;
                if (mPasswordConfirmEdit
                        .getText()
                        .toString()
                        .equals(mPasswordEdit.getText().toString())) {
                    msg = getString(R.string.wizard_password_incorrect);
                } else {
                    msg = getString(R.string.wizard_passwords_unmatched);
                }
                LinphoneUtils.displayError(false, mPasswordError, msg);
                LinphoneUtils.displayErrorAlert(msg, AssistantActivity.instance());
            } else {
                mAccountCreator.createAccount();
            }
        } else {
            if (mPhoneNumberEdit.length() > 0 || mDialCode.length() > 1) {
                int phoneStatus;
                boolean isOk;
                phoneStatus =
                        mAccountCreator.setPhoneNumber(
                                mPhoneNumberEdit.getText().toString(),
                                LinphoneUtils.getCountryCode(mDialCode));
                isOk = phoneStatus == AccountCreator.PhoneNumberStatus.Ok.toInt();
                if (!mUseUsername.isChecked() && mAccountCreator.getUsername() == null) {
                    mAccountCreator.setUsername(mAccountCreator.getPhoneNumber());
                } else {
                    mAccountCreator.setUsername(mUsernameEdit.getText().toString());
                    mAccountCreator.setPhoneNumber(
                            mPhoneNumberEdit.getText().toString(), mDialCode.getText().toString());
                }
                if (isOk) {
                    mAccountCreator.createAccount();
                } else {
                    LinphoneUtils.displayErrorAlert(
                            LinphoneUtils.errorForPhoneNumberStatus(phoneStatus),
                            AssistantActivity.instance());
                    LinphoneUtils.displayError(
                            isOk,
                            mPhoneNumberError,
                            LinphoneUtils.errorForPhoneNumberStatus(phoneStatus));
                }
            } else {
                LinphoneUtils.displayErrorAlert(
                        getString(R.string.assistant_create_account_part_1),
                        AssistantActivity.instance());
            }
        }
        mCreateAccount.setEnabled(true);
    }

    private int getPhoneNumberStatus() {
        int status =
                mAccountCreator.setPhoneNumber(
                        mPhoneNumberEdit.getText().toString(),
                        LinphoneUtils.getCountryCode(mDialCode));
        mAddressSip = mAccountCreator.getPhoneNumber();
        return status;
    }

    private void onTextChanged2() {
        String msg = "";
        mAccountCreator.setUsername(getUsername());

        if (!mUseEmail.isChecked()
                && getResources().getBoolean(R.bool.use_phone_number_validation)) {
            int status = getPhoneNumberStatus();
            boolean isOk = (status == AccountCreator.PhoneNumberStatus.Ok.toInt());
            LinphoneUtils.displayError(
                    isOk, mPhoneNumberError, LinphoneUtils.errorForPhoneNumberStatus(status));

            // Username or phone number
            if (getResources().getBoolean(R.bool.assistant_allow_username)
                    && mUseUsername.isChecked()) {
                mAddressSip = getUsername();
            }

            if (!isOk) {
                if (status == AccountCreator.PhoneNumberStatus.InvalidCountryCode.toInt()) {
                    mDialCode.setBackgroundResource(R.drawable.resizable_textfield_error);
                    mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
                } else {
                    mDialCode.setBackgroundResource(R.drawable.resizable_textfield);
                    mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield_error);
                }

            } else {
                mDialCode.setBackgroundResource(R.drawable.resizable_textfield);
                mPhoneNumberEdit.setBackgroundResource(R.drawable.resizable_textfield);
                if (!mLinkAccount && mAddressSip.length() > 0) {
                    msg =
                            getResources()
                                            .getString(
                                                    R.string
                                                            .assistant_create_account_phone_number_address)
                                    + " <"
                                    + mAddressSip
                                    + "@"
                                    + getResources().getString(R.string.default_domain)
                                    + ">";
                }
            }
        } else {
            mAddressSip = getUsername();
            if (mAddressSip.length() > 0) {
                msg =
                        getResources()
                                        .getString(
                                                R.string
                                                        .assistant_create_account_phone_number_address)
                                + " <sip:"
                                + mAddressSip
                                + "@"
                                + getResources().getString(R.string.default_domain)
                                + ">";
            }
        }
        mSipUri.setText(msg);
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

    private void addUsernameHandler(final EditText field) {
        field.addTextChangedListener(
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        Matcher matcher = UPPER_CASE_REGEX.matcher(s);
                        while (matcher.find()) {
                            CharSequence upperCaseRegion =
                                    s.subSequence(matcher.start(), matcher.end());
                            s.replace(
                                    matcher.start(),
                                    matcher.end(),
                                    upperCaseRegion.toString().toLowerCase());
                        }
                    }

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {
                        onTextChanged2();
                    }
                });
    }

    private void addEmailHandler(final EditText field) {
        field.addTextChangedListener(
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        mEmailOk = false;
                        AccountCreator.EmailStatus status =
                                mAccountCreator.setEmail(field.getText().toString());
                        if (status.equals(AccountCreator.EmailStatus.Ok)) {
                            mEmailOk = true;
                            LinphoneUtils.displayError(mEmailOk, mEmailError, "");
                        } else {
                            LinphoneUtils.displayError(
                                    mEmailOk,
                                    mEmailError,
                                    LinphoneUtils.errorForEmailStatus(status));
                        }
                    }

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {}
                });
    }

    private void addPasswordHandler(final EditText field1) {
        TextWatcher passwordListener =
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        mPasswordOk = false;
                        AccountCreator.PasswordStatus status =
                                mAccountCreator.setPassword(field1.getText().toString());
                        if (isPasswordCorrect(field1.getText().toString())) {
                            mPasswordOk = true;
                            LinphoneUtils.displayError(mPasswordOk, mPasswordError, "");
                        } else {
                            LinphoneUtils.displayError(
                                    mPasswordOk,
                                    mPasswordError,
                                    LinphoneUtils.errorForPasswordStatus(status));
                        }
                    }

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {}
                };

        field1.addTextChangedListener(passwordListener);
    }

    private void addConfirmPasswordHandler(final EditText field1, final EditText field2) {
        TextWatcher passwordListener =
                new TextWatcher() {
                    public void afterTextChanged(Editable s) {
                        mConfirmPasswordOk = false;
                        if (field1.getText().toString().equals(field2.getText().toString())) {
                            mConfirmPasswordOk = true;
                            if (!isPasswordCorrect(field1.getText().toString())) {
                                LinphoneUtils.displayError(
                                        mPasswordOk,
                                        mPasswordError,
                                        getString(R.string.wizard_password_incorrect));
                            } else {
                                LinphoneUtils.displayError(
                                        mConfirmPasswordOk, mPasswordConfirmError, "");
                            }
                        } else {
                            LinphoneUtils.displayError(
                                    mConfirmPasswordOk,
                                    mPasswordConfirmError,
                                    getString(R.string.wizard_passwords_unmatched));
                        }
                    }

                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int count, int after) {}
                };
        field1.addTextChangedListener(passwordListener);
        field2.addTextChangedListener(passwordListener);
    }

    @Override
    public void onIsAccountExist(AccountCreator accountCreator, final Status status, String resp) {
        if (status.equals(Status.AccountExist) || status.equals(Status.AccountExistWithAlias)) {
            if (mUseEmail.isChecked()) {
                mCreateAccount.setEnabled(true);
                LinphoneUtils.displayErrorAlert(
                        LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
            } else {
                accountCreator.isAliasUsed();
            }
        } else {
            createAccount();
        }
    }

    @Override
    public void onCreateAccount(AccountCreator accountCreator, Status status, String resp) {
        if (status.equals(Status.AccountCreated)) {
            if (mUseEmail.isChecked()
                    || !getResources().getBoolean(R.bool.use_phone_number_validation)) {
                AssistantActivity.instance()
                        .displayAssistantConfirm(
                                getUsername(),
                                mPasswordEdit.getText().toString(),
                                mEmailEdit.getText().toString());
            } else {
                AssistantActivity.instance()
                        .displayAssistantCodeConfirm(
                                getUsername(),
                                mPhoneNumberEdit.getText().toString(),
                                LinphoneUtils.getCountryCode(mDialCode),
                                false);
            }
        } else {
            mCreateAccount.setEnabled(true);
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
        }
    }

    @Override
    public void onActivateAccount(AccountCreator accountCreator, Status status, String resp) {}

    @Override
    public void onLinkAccount(AccountCreator accountCreator, Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(Status.RequestOk)) {
            AssistantActivity.instance()
                    .displayAssistantCodeConfirm(
                            getUsername(),
                            mPhoneNumberEdit.getText().toString(),
                            LinphoneUtils.getCountryCode(mDialCode),
                            false);
        }
    }

    @Override
    public void onActivateAlias(AccountCreator accountCreator, Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(Status.RequestOk)) {
            AssistantActivity.instance()
                    .displayAssistantCodeConfirm(
                            getUsername(),
                            mPhoneNumberEdit.getText().toString(),
                            LinphoneUtils.getCountryCode(mDialCode),
                            false);
        }
    }

    @Override
    public void onIsAccountActivated(AccountCreator accountCreator, Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(Status.AccountNotActivated)) {
            if (getResources().getBoolean(R.bool.isTablet)
                    || !getResources().getBoolean(R.bool.use_phone_number_validation)) {
                // mAccountCreator.activateAccount(); // Resend email TODO
            } else {
                accountCreator.recoverAccount(); // Resend SMS
            }
        } else {
            mCreateAccount.setEnabled(true);
            LinphoneUtils.displayErrorAlert(
                    LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
        }
    }

    @Override
    public void onRecoverAccount(AccountCreator accountCreator, Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(Status.RequestOk)) {
            AssistantActivity.instance()
                    .displayAssistantCodeConfirm(
                            getUsername(),
                            mPhoneNumberEdit.getText().toString(),
                            mDialCode.getText().toString(),
                            false);
        } else {
            mCreateAccount.setEnabled(true);
            // SMS error
            LinphoneUtils.displayErrorAlert(
                    getString(R.string.request_failed), AssistantActivity.instance());
        }
    }

    @Override
    public void onIsAccountLinked(AccountCreator accountCreator, Status status, String resp) {}

    @Override
    public void onIsAliasUsed(AccountCreator ac, Status status, String resp) {
        if (AssistantActivity.instance() == null) {
            return;
        }
        if (status.equals(Status.AliasIsAccount) || status.equals(Status.AliasExist)) {
            if (mAccountCreator.getPhoneNumber() != null
                    && mAccountCreator.getUsername() != null
                    && mAccountCreator.getPhoneNumber().compareTo(mAccountCreator.getUsername())
                            == 0) {
                mAccountCreator.isAccountActivated();
            } else {
                mCreateAccount.setEnabled(true);
                LinphoneUtils.displayErrorAlert(
                        LinphoneUtils.errorForStatus(status), AssistantActivity.instance());
            }
        } else {
            mAccountCreator.isAccountActivated();
        }
    }

    @Override
    public void onUpdateAccount(AccountCreator accountCreator, Status status, String resp) {}
}
