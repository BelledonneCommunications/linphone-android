package org.linphone.assistant;

/*
AssistantActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

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
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.ThemableActivity;

public abstract class AssistantActivity extends ThemableActivity
        implements CountryPicker.CountryPickedListener {
    protected static AccountCreator mAccountCreator;

    protected View mTopBar, mStatusBar;
    protected ImageView mBack;
    protected AlertDialog mCountryPickerDialog;

    protected CountryPicker mCountryPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mAccountCreator == null) {
            String url = LinphonePreferences.instance().getXmlrpcUrl();
            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            mAccountCreator = core.createAccountCreator(url);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        mStatusBar = findViewById(R.id.status);
        if (getResources().getBoolean(R.bool.assistant_hide_status_bar)) {
            mStatusBar.setVisibility(View.GONE);
        }

        mTopBar = findViewById(R.id.top_bar);
        if (getResources().getBoolean(R.bool.assistant_hide_top_bar)) {
            mTopBar.setVisibility(View.GONE);
        }

        mBack = findViewById(R.id.back);
        mBack.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finish();
                    }
                });
    }

    @Override
    public void onCountryClicked(DialPlan dialPlan) {
        if (mCountryPickerDialog != null) {
            mCountryPickerDialog.dismiss();
            mCountryPickerDialog = null;
        }
    }

    protected void createProxyConfigAndLeaveAssistant() {
        ProxyConfig proxyConfig = mAccountCreator.configure();
        if (proxyConfig == null) {
            // An error has happened !
            // TODO: display error message
        } else {
            goToLinphoneActivity();
        }
    }

    protected void goToLinphoneActivity() {
        mAccountCreator = null;
        Intent intent = new Intent(this, LinphoneActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    protected void showPhoneNumberDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.phone_number_info_title))
                .setMessage(
                        getString(R.string.phone_number_link_info_content)
                                + "\n"
                                + getString(
                                        R.string.phone_number_link_info_content_already_account))
                .show();
    }

    protected void showAccountAlreadyExistsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.account_already_exist))
                .setMessage(getString(R.string.assistant_phone_number_unavailable))
                .show();
    }

    protected void showGenericErrorDialog(AccountCreator.Status status) {
        String message;

        switch (status) {
                // TODO
            case PhoneNumberInvalid:
                message = getString(R.string.phone_number_invalid);
                break;
            case WrongActivationCode:
                message = getString(R.string.activation_code_invalid);
                break;
            default:
                message = getString(R.string.error_unknown);
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.error))
                .setMessage(message)
                .show();
    }

    protected void showCountryPickerDialog() {
        if (mCountryPicker == null) {
            mCountryPicker = new CountryPicker(this, this);
        }
        mCountryPickerDialog =
                new AlertDialog.Builder(this).setView(mCountryPicker.getView()).show();
    }

    protected DialPlan getDialPlanForCurrentCountry() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String countryIso = tm.getNetworkCountryIso();
            return getDialPlanFromCountryCode(countryIso);
        } catch (Exception e) {
            Log.e("[Assistant] " + e);
        }
        return null;
    }

    protected DialPlan getDialPlanFromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;

        for (DialPlan c : Factory.instance().getDialPlans()) {
            if (prefix.equalsIgnoreCase(c.getCountryCallingCode())) return c;
        }
        return null;
    }

    protected DialPlan getDialPlanFromCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) return null;

        for (DialPlan c : Factory.instance().getDialPlans()) {
            if (countryCode.equalsIgnoreCase(c.getIsoCountryCode())) return c;
        }
        return null;
    }

    protected int arePhoneNumberAndPrefixOk(EditText prefixEditText, EditText phoneNumberEditText) {
        String prefix = prefixEditText.getText().toString();
        if (prefix.startsWith("+")) {
            prefix = prefix.substring(1);
        }

        String phoneNumber = phoneNumberEditText.getText().toString();
        return mAccountCreator.setPhoneNumber(phoneNumber, prefix);
    }

    protected String getErrorFromPhoneNumberStatus(int status) {
        AccountCreator.PhoneNumberStatus phoneNumberStatus =
                AccountCreator.PhoneNumberStatus.fromInt(status);
        switch (phoneNumberStatus) {
            case InvalidCountryCode:
                return getString(R.string.country_code_invalid);
            case TooShort:
                return getString(R.string.phone_number_too_short);
            case TooLong:
                return getString(R.string.phone_number_too_long);
            case Invalid:
                return getString(R.string.phone_number_invalid);
        }
        return null;
    }

    /*public static String errorForEmailStatus(AccountCreator.EmailStatus status) {
        Context ctxt = getContext();
        if (ctxt != null) {
            if (status.equals(AccountCreator.EmailStatus.InvalidCharacters)
                    || status.equals(AccountCreator.EmailStatus.Malformed))
                return ctxt.getString(R.string.invalid_email);
        }
        return null;
    }

    public static String errorForUsernameStatus(AccountCreator.UsernameStatus status) {
        Context ctxt = getContext();
        if (ctxt != null) {
            if (status.equals(AccountCreator.UsernameStatus.InvalidCharacters))
                return ctxt.getString(R.string.invalid_username);
            if (status.equals(AccountCreator.UsernameStatus.TooShort))
                return ctxt.getString(R.string.username_too_short);
            if (status.equals(AccountCreator.UsernameStatus.TooLong))
                return ctxt.getString(R.string.username_too_long);
            if (status.equals(AccountCreator.UsernameStatus.Invalid))
                return ctxt.getString(R.string.username_invalid_size);
            if (status.equals(AccountCreator.UsernameStatus.InvalidCharacters))
                return ctxt.getString(R.string.invalid_display_name);
        }
        return null;
    }

    public static String errorForPasswordStatus(AccountCreator.PasswordStatus status) {
        Context ctxt = getContext();
        if (ctxt != null) {
            if (status.equals(AccountCreator.PasswordStatus.TooShort))
                return ctxt.getString(R.string.password_too_short);
            if (status.equals(AccountCreator.PasswordStatus.TooLong))
                return ctxt.getString(R.string.password_too_long);
        }
        return null;
    }

    public static String errorForStatus(AccountCreator.Status status) {
        Context ctxt = getContext();
        if (ctxt != null) {
            if (status.equals(AccountCreator.Status.RequestFailed))
                return ctxt.getString(R.string.request_failed);
            if (status.equals(AccountCreator.Status.ServerError))
                return ctxt.getString(R.string.wizard_failed);
            if (status.equals(AccountCreator.Status.AccountExist)
                    || status.equals(AccountCreator.Status.AccountExistWithAlias))
                return ctxt.getString(R.string.account_already_exist);
            if (status.equals(AccountCreator.Status.AliasIsAccount)
                    || status.equals(AccountCreator.Status.AliasExist))
                return ctxt.getString(R.string.assistant_phone_number_unavailable);
            if (status.equals(AccountCreator.Status.AccountNotExist))
                return ctxt.getString(R.string.assistant_error_bad_credentials);
            if (status.equals(AccountCreator.Status.AliasNotExist))
                return ctxt.getString(R.string.phone_number_not_exist);
            if (status.equals(AccountCreator.Status.AliasNotExist)
                    || status.equals(AccountCreator.Status.AccountNotActivated)
                    || status.equals(AccountCreator.Status.AccountAlreadyActivated)
                    || status.equals(AccountCreator.Status.AccountActivated)
                    || status.equals(AccountCreator.Status.AccountNotCreated)
                    || status.equals(AccountCreator.Status.RequestOk)) return "";
        }
        return null;
    }*/
}
