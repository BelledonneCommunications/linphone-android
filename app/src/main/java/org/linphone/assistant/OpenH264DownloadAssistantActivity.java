package org.linphone.assistant;

/*
OpenH264DownloadAssistantActivity.java
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

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Core;
import org.linphone.core.PayloadType;
import org.linphone.core.tools.Log;
import org.linphone.core.tools.OpenH264DownloadHelper;
import org.linphone.core.tools.OpenH264DownloadHelperListener;
import org.linphone.settings.LinphonePreferences;

public class OpenH264DownloadAssistantActivity extends AssistantActivity {
    private TextView mYes, mNo;
    private ProgressBar mProgress;

    private OpenH264DownloadHelper mHelper;
    private OpenH264DownloadHelperListener mListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.assistant_openh264_codec_download);
        mHelper = LinphoneManager.getInstance().getOpenH264DownloadHelper();
        LinphonePreferences.instance().setOpenH264CodecDownloadEnabled(false);

        mProgress = findViewById(R.id.progress_bar);

        mYes = findViewById(R.id.answer_yes);
        mYes.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mYes.setEnabled(false);
                        mNo.setEnabled(false);
                        Log.e("[OpenH264 Downloader] Start download");
                        mProgress.setVisibility(View.VISIBLE);
                        mHelper.downloadCodec();
                    }
                });

        mNo = findViewById(R.id.answer_no);
        mNo.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mYes.setEnabled(false);
                        mNo.setEnabled(false);
                        Log.e("[OpenH264 Downloader] Download refused");
                        goToLinphoneActivity();
                    }
                });

        mListener =
                new OpenH264DownloadHelperListener() {
                    @Override
                    public void OnProgress(int current, int max) {
                        if (current < max) {
                            mProgress.setMax(max);
                            mProgress.setProgress(current);
                        } else {
                            Core core = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
                            if (core != null) {
                                core.reloadMsPlugins(getApplicationInfo().nativeLibraryDir);
                                enableH264();
                            }

                            goToLinphoneActivity();
                        }
                    }

                    @Override
                    public void OnError(String s) {
                        Log.e("[OpenH264 Downloader] " + s);
                        mYes.setEnabled(true);
                        mNo.setEnabled(true);
                    }
                };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHelper.setOpenH264HelperListener(mListener);
    }

    @Override
    protected void onPause() {
        mHelper.setOpenH264HelperListener(null);
        super.onPause();
    }

    private void enableH264() {
        for (PayloadType pt : LinphoneManager.getLc().getVideoPayloadTypes()) {
            if (pt.getMimeType().equals("H264")) {
                pt.enable(true);
                break;
            }
        }
    }
}
