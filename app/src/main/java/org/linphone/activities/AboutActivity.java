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
package org.linphone.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.settings.LinphonePreferences;

public class AboutActivity extends MainActivity {
    private CoreListenerStub mListener;
    private ProgressDialog mProgress;
    private boolean mUploadInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnBackPressGoHome = false;
        mAlwaysHideTabBar = true;

        // Uses the fragment container layout to inflate the about view instead of using a fragment
        View aboutView = LayoutInflater.from(this).inflate(R.layout.about, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(aboutView, params);

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        TextView aboutVersion = findViewById(R.id.about_android_version);
        TextView aboutLiblinphoneVersion = findViewById(R.id.about_liblinphone_sdk_version);
        aboutLiblinphoneVersion.setText(
                String.format(
                        getString(R.string.about_liblinphone_sdk_version),
                        getString(R.string.linphone_sdk_version)
                                + " ("
                                + getString(R.string.linphone_sdk_branch)
                                + ")"));
        // We can't access a library's BuildConfig, so we have to set it as a resource
        aboutVersion.setText(
                String.format(
                        getString(R.string.about_version),
                        org.linphone.BuildConfig.VERSION_NAME
                                + " ("
                                + org.linphone.BuildConfig.BUILD_TYPE
                                + ")"));

        TextView privacyPolicy = findViewById(R.id.privacy_policy_link);
        privacyPolicy.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.about_privacy_policy_link)));
                        startActivity(browserIntent);
                    }
                });

        TextView license = findViewById(R.id.about_text);
        license.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.about_license_link)));
                        startActivity(browserIntent);
                    }
                });

        Button sendLogs = findViewById(R.id.send_log);
        sendLogs.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Core core = LinphoneManager.getCore();
                        if (core != null) {
                            core.uploadLogCollection();
                        }
                    }
                });
        sendLogs.setVisibility(
                LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        Button resetLogs = findViewById(R.id.reset_log);
        resetLogs.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Core core = LinphoneManager.getCore();
                        if (core != null) {
                            core.resetLogCollection();
                        }
                    }
                });
        resetLogs.setVisibility(
                LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onLogCollectionUploadProgressIndication(
                            Core core, int offset, int total) {}

                    @Override
                    public void onLogCollectionUploadStateChanged(
                            Core core, Core.LogCollectionUploadState state, String info) {
                        if (state == Core.LogCollectionUploadState.InProgress) {
                            displayUploadLogsInProgress();
                        } else if (state == Core.LogCollectionUploadState.Delivered
                                || state == Core.LogCollectionUploadState.NotDelivered) {
                            mUploadInProgress = false;
                            if (mProgress != null) mProgress.dismiss();
                        }
                    }
                };
    }

    @Override
    public void onResume() {
        super.onResume();

        showTopBarWithTitle(getString(R.string.about));
        if (getResources().getBoolean(R.bool.hide_bottom_bar_on_second_level_views)) {
            hideTabBar();
        }

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
        }
    }

    @Override
    public void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mListener = null;
        mProgress = null;

        super.onDestroy();
    }

    private void displayUploadLogsInProgress() {
        if (mUploadInProgress) {
            return;
        }
        mUploadInProgress = true;

        mProgress = ProgressDialog.show(this, null, null);
        Drawable d = new ColorDrawable(ContextCompat.getColor(this, R.color.light_grey_color));
        d.setAlpha(200);
        mProgress
                .getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);
        mProgress.getWindow().setBackgroundDrawable(d);
        mProgress.setContentView(R.layout.wait_layout);
        mProgress.show();
    }
}
