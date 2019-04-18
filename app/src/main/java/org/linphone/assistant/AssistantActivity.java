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
import android.telephony.TelephonyManager;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import org.linphone.R;
import org.linphone.core.DialPlan;
import org.linphone.core.Factory;
import org.linphone.core.tools.Log;
import org.linphone.utils.ThemableActivity;

public abstract class AssistantActivity extends ThemableActivity
        implements CountryPicker.CountryPickedListener {
    protected View mTopBar, mStatusBar;
    protected ImageView mBack;
    private AlertDialog mCountryPickerDialog;

    private CountryPicker mCountryPicker;

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

    @Override
    public void onCountryClicked(DialPlan dialPlan) {
        if (mCountryPickerDialog != null) {
            mCountryPickerDialog.dismiss();
            mCountryPickerDialog = null;
        }
    }
}
