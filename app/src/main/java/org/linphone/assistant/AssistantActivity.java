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

import static org.linphone.core.AccountCreator.UsernameStatus.Invalid;
import static org.linphone.core.AccountCreator.UsernameStatus.InvalidCharacters;
import static org.linphone.core.AccountCreator.UsernameStatus.TooLong;
import static org.linphone.core.AccountCreator.UsernameStatus.TooShort;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.DialerActivity;
import org.linphone.activities.LinphoneGenericActivity;
import org.linphone.core.AccountCreator;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public abstract class AssistantActivity extends LinphoneGenericActivity
        implements CountryPicker.CountryPickedListener {
    static AccountCreator mAccountCreator;

    ImageView mBack;
    private AlertDialog mCountryPickerDialog;

    private CountryPicker mCountryPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

        if (mAccountCreator == null) {
            String url = LinphonePreferences.instance().getXmlrpcUrl();
            Core core = LinphoneManager.getCore();
            core.loadConfigFromXml(LinphonePreferences.instance().getDefaultDynamicConfigFile());
            mAccountCreator = core.createAccountCreator(url);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        View statusBar = findViewById(R.id.status);
        if (getResources().getBoolean(R.bool.assistant_hide_status_bar)) {
            statusBar.setVisibility(View.GONE);
        }

        View topBar = findViewById(R.id.top_bar);
        if (getResources().getBoolean(R.bool.assistant_hide_top_bar)) {
            topBar.setVisibility(View.GONE);
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

    void createProxyConfigAndLeaveAssistant() {
        Core core = LinphoneManager.getCore();
        boolean useLinphoneDefaultValues =
                getString(R.string.default_domain).equals(mAccountCreator.getDomain());
        if (useLinphoneDefaultValues) {
            core.loadConfigFromXml(LinphonePreferences.instance().getLinphoneDynamicConfigFile());
        }

        ProxyConfig proxyConfig = mAccountCreator.createProxyConfig();

        if (useLinphoneDefaultValues) {
            // Restore default values
            core.loadConfigFromXml(LinphonePreferences.instance().getDefaultDynamicConfigFile());
        } else {
            // If this isn't a sip.linphone.org account, disable push notifications and enable
            // service notification, otherwise incoming calls won't work (most probably)
            LinphonePreferences.instance().setServiceNotificationVisibility(true);
            LinphoneService.instance().getNotificationManager().startForeground();
        }

        if (proxyConfig == null) {
            Log.e("[Assistant] Account creator couldn't create proxy config");
            // TODO: display error message
        } else {
            if (proxyConfig.getDialPrefix() == null) {
                DialPlan dialPlan = getDialPlanForCurrentCountry();
                if (dialPlan != null) {
                    proxyConfig.setDialPrefix(dialPlan.getCountryCallingCode());
                }
            }

            LinphonePreferences.instance().firstLaunchSuccessful();
            goToLinphoneActivity();
        }
    }

    void goToLinphoneActivity() {
        boolean needsEchoCalibration =
                LinphoneManager.getCore().isEchoCancellerCalibrationRequired();
        boolean echoCalibrationDone =
                LinphonePreferences.instance().isEchoCancellationCalibrationDone();
        Log.i(
                "[Assistant] Echo cancellation calibration required ? "
                        + needsEchoCalibration
                        + ", already done ? "
                        + echoCalibrationDone);

        Intent intent;
        if (needsEchoCalibration && !echoCalibrationDone) {
            intent = new Intent(this, EchoCancellerCalibrationAssistantActivity.class);
        } else {
            /*boolean openH264 = LinphonePreferences.instance().isOpenH264CodecDownloadEnabled();
            boolean codecFound =
                    LinphoneManager.getInstance().getOpenH264DownloadHelper().isCodecFound();
            boolean abiSupported =
                    Version.getCpuAbis().contains("armeabi-v7a")
                            && !Version.getCpuAbis().contains("x86");
            boolean androidVersionOk = Version.sdkStrictlyBelow(Build.VERSION_CODES.M);

            if (openH264 && abiSupported && androidVersionOk && !codecFound) {
                intent = new Intent(this, OpenH264DownloadAssistantActivity.class);
            } else {*/
            intent = new Intent(this, DialerActivity.class);
            // }
        }
        startActivity(intent);
    }

    void showPhoneNumberDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.phone_number_info_title))
                .setMessage(
                        getString(R.string.phone_number_link_info_content)
                                + "\n"
                                + getString(
                                        R.string.phone_number_link_info_content_already_account))
                .show();
    }

    void showAccountAlreadyExistsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.account_already_exist))
                .setMessage(getString(R.string.assistant_phone_number_unavailable))
                .show();
    }

    void showGenericErrorDialog(AccountCreator.Status status) {
        String message;

        switch (status) {
                // TODO handle other possible status
            case PhoneNumberInvalid:
                message = getString(R.string.phone_number_invalid);
                break;
            case WrongActivationCode:
                message = getString(R.string.activation_code_invalid);
                break;
            case PhoneNumberOverused:
                message = getString(R.string.phone_number_overuse);
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

    void showCountryPickerDialog() {
        if (mCountryPicker == null) {
            mCountryPicker = new CountryPicker(this, this);
        }
        mCountryPickerDialog =
                new AlertDialog.Builder(this).setView(mCountryPicker.getView()).show();
    }

    DialPlan getDialPlanForCurrentCountry() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            String countryIso = tm.getNetworkCountryIso();
            return getDialPlanFromCountryCode(countryIso);
        } catch (Exception e) {
            Log.e("[Assistant] " + e);
        }
        return null;
    }

    String getDevicePhoneNumber() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            return tm.getLine1Number();
        } catch (Exception e) {
            Log.e("[Assistant] " + e);
        }
        return null;
    }

    DialPlan getDialPlanFromPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return null;

        for (DialPlan c : Factory.instance().getDialPlans()) {
            if (prefix.equalsIgnoreCase(c.getCountryCallingCode())) return c;
        }
        return null;
    }

    private DialPlan getDialPlanFromCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) return null;

        for (DialPlan c : Factory.instance().getDialPlans()) {
            if (countryCode.equalsIgnoreCase(c.getIsoCountryCode())) return c;
        }
        return null;
    }

    int arePhoneNumberAndPrefixOk(EditText prefixEditText, EditText phoneNumberEditText) {
        String prefix = prefixEditText.getText().toString();
        if (prefix.startsWith("+")) {
            prefix = prefix.substring(1);
        }

        String phoneNumber = phoneNumberEditText.getText().toString();
        return mAccountCreator.setPhoneNumber(phoneNumber, prefix);
    }

    String getErrorFromPhoneNumberStatus(int status) {
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

    String getErrorFromUsernameStatus(AccountCreator.UsernameStatus status) {
        switch (status) {
            case Invalid:
                return getString(R.string.username_invalid_size);
            case InvalidCharacters:
                return getString(R.string.invalid_characters);
            case TooLong:
                return getString(R.string.username_too_long);
            case TooShort:
                return getString(R.string.username_too_short);
        }
        return null;
    }
}
