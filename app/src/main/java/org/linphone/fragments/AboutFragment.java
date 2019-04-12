package org.linphone.fragments;
/*
AboutFragment.java
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

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.linphone.BuildConfig;
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.CoreListenerStub;
import org.linphone.settings.LinphonePreferences;

public class AboutFragment extends Fragment implements OnClickListener {
    private View mSendLogButton = null;
    private View mResetLogButton = null;
    private CoreListenerStub mListener;
    private ProgressDialog mProgress;
    private boolean mUploadInProgress;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about, container, false);

        TextView aboutVersion = view.findViewById(R.id.about_android_version);
        TextView aboutLiblinphoneVersion = view.findViewById(R.id.about_liblinphone_sdk_version);
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
                        BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"));

        TextView privacyPolicy = view.findViewById(R.id.privacy_policy_link);
        privacyPolicy.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.about_privacy_policy_link)));
                        startActivity(browserIntent);
                    }
                });

        TextView license = view.findViewById(R.id.about_text);
        license.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent browserIntent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(getString(R.string.about_license_link)));
                        startActivity(browserIntent);
                    }
                });

        mSendLogButton = view.findViewById(R.id.send_log);
        mSendLogButton.setOnClickListener(this);
        mSendLogButton.setVisibility(
                LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        mResetLogButton = view.findViewById(R.id.reset_log);
        mResetLogButton.setOnClickListener(this);
        mResetLogButton.setVisibility(
                LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onLogCollectionUploadProgressIndication(
                            Core lc, int offset, int total) {}

                    @Override
                    public void onLogCollectionUploadStateChanged(
                            Core lc, LogCollectionUploadState state, String info) {
                        if (state == LogCollectionUploadState.InProgress) {
                            displayUploadLogsInProgress();
                        } else if (state == LogCollectionUploadState.Delivered
                                || state == LogCollectionUploadState.NotDelivered) {
                            mUploadInProgress = false;
                            if (mProgress != null) mProgress.dismiss();
                        }
                    }
                };

        return view;
    }

    private void displayUploadLogsInProgress() {
        if (mUploadInProgress) {
            return;
        }
        mUploadInProgress = true;

        mProgress = ProgressDialog.show(LinphoneActivity.instance(), null, null);
        Drawable d =
                new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.light_grey_color));
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

    @Override
    public void onPause() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        super.onPause();
    }

    @Override
    public void onResume() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.ABOUT);
        }

        super.onResume();
    }

    @Override
    public void onClick(View v) {
        if (LinphoneActivity.isInstanciated()) {
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            if (v == mSendLogButton) {
                if (lc != null) {
                    lc.uploadLogCollection();
                }
            } else if (v == mResetLogButton) {
                if (lc != null) {
                    lc.resetLogCollection();
                }
            }
        }
    }
}
