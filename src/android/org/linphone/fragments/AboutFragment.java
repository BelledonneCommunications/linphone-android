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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.BuildConfig;
import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.core.Core;
import org.linphone.core.Core.LogCollectionUploadState;
import org.linphone.core.CoreListenerStub;
import org.linphone.mediastream.Log;

public class AboutFragment extends Fragment implements OnClickListener {
    View sendLogButton = null;
    View resetLogButton = null;
    ImageView cancel;
    CoreListenerStub mListener;
    private ProgressDialog progress;
    private boolean uploadInProgress;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about, container, false);

        TextView aboutVersion = view.findViewById(R.id.about_android_version);
        TextView aboutLiblinphoneVersion = view.findViewById(R.id.about_liblinphone_version);
        aboutLiblinphoneVersion.setText(String.format(getString(R.string.about_liblinphone_version), LinphoneManager.getLc().getVersion()));
        aboutVersion.setText(String.format(getString(R.string.about_version), BuildConfig.VERSION_NAME));

        cancel = view.findViewById(R.id.cancel);
        cancel.setOnClickListener(this);

        sendLogButton = view.findViewById(R.id.send_log);
        sendLogButton.setOnClickListener(this);
        sendLogButton.setVisibility(LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        resetLogButton = view.findViewById(R.id.reset_log);
        resetLogButton.setOnClickListener(this);
        resetLogButton.setVisibility(LinphonePreferences.instance().isDebugEnabled() ? View.VISIBLE : View.GONE);

        mListener = new CoreListenerStub() {
            @Override
            public void onLogCollectionUploadProgressIndication(Core lc, int offset, int total) {
            }

            @Override
            public void onLogCollectionUploadStateChanged(Core lc, LogCollectionUploadState state, String info) {
                if (state == LogCollectionUploadState.InProgress) {
                    displayUploadLogsInProgress();
                } else if (state == LogCollectionUploadState.Delivered || state == LogCollectionUploadState.NotDelivered) {
                    uploadInProgress = false;
                    if (progress != null) progress.dismiss();
                    if (state == LogCollectionUploadState.Delivered) {
                        sendLogs(LinphoneService.instance().getApplicationContext(), info);
                    }
                }
            }
        };

        return view;
    }

    private void displayUploadLogsInProgress() {
        if (uploadInProgress) {
            return;
        }
        uploadInProgress = true;

        progress = ProgressDialog.show(LinphoneActivity.instance(), null, null);
        Drawable d = new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.colorE));
        d.setAlpha(200);
        progress.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        progress.getWindow().setBackgroundDrawable(d);
        progress.setContentView(R.layout.progress_dialog);
        progress.show();
    }

    private void sendLogs(Context context, String info) {
        final String appName = context.getString(R.string.app_name);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_EMAIL, new String[]{context.getString(R.string.about_bugreport_email)});
        i.putExtra(Intent.EXTRA_SUBJECT, appName + " Logs");
        i.putExtra(Intent.EXTRA_TEXT, info);
        i.setType("application/zip");

        try {
            startActivity(Intent.createChooser(i, "Send mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Log.e(ex);
        }
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
            if (v == sendLogButton) {
                if (lc != null) {
                    lc.uploadLogCollection();
                }
            } else if (v == resetLogButton) {
                if (lc != null) {
                    lc.resetLogCollection();
                }
            } else if (v == cancel) {
                LinphoneActivity.instance().goToDialerFragment();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
