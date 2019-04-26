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
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AccountCreator;
import org.linphone.core.Core;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;
import org.linphone.core.ProxyConfig;
import org.linphone.core.tools.Log;
import org.linphone.main.DialerActivity;
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
            core.loadConfigFromXml(LinphoneManager.getInstance().getDefaultDynamicConfigFile());
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
        Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        boolean useLinphoneDefaultValues =
                getString(R.string.default_domain).equals(mAccountCreator.getDomain());
        if (useLinphoneDefaultValues) {
            core.loadConfigFromXml(LinphoneManager.getInstance().getLinphoneDynamicConfigFile());
        }

        ProxyConfig proxyConfig = mAccountCreator.configure();

        if (useLinphoneDefaultValues) {
            // Restore default values
            core.loadConfigFromXml(LinphoneManager.getInstance().getDefaultDynamicConfigFile());
        }

        if (proxyConfig == null) {
            Log.e("[Assistant] Account creator couldn't create proxy config");
            // TODO: display error message
        } else {
            LinphonePreferences.instance().firstLaunchSuccessful();
            goToLinphoneActivity();
        }
    }

    protected void goToLinphoneActivity() {
        boolean needsEchoCalibration = LinphoneManager.getLc().isEchoCancellerCalibrationRequired();
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

    protected String getDevicePhoneNumber() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            return tm.getLine1Number();
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
}
