package org.linphone.assistant;

/*
CodecDownloaderFragment.java
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.PayloadType;
import org.linphone.core.tools.OpenH264DownloadHelper;
import org.linphone.core.tools.OpenH264DownloadHelperListener;

public class CodecDownloaderFragment extends Fragment {
    private final Handler mHandler = new Handler();
    private TextView mQuestion;
    private TextView mDownloading;
    private TextView mDownloaded;
    private Button mYes;
    private Button mNo;
    private Button mOk;
    private ProgressBar mProgressBar;
    private TextView mDownloadingInfo;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.assistant_codec_downloader, container, false);

        mQuestion = view.findViewById(R.id.question);
        mDownloading = view.findViewById(R.id.downloading);
        mDownloaded = view.findViewById(R.id.downloaded);
        mYes = view.findViewById(R.id.answerYes);
        mNo = view.findViewById(R.id.answerNo);
        mOk = view.findViewById(R.id.answerOk);
        mProgressBar = view.findViewById(R.id.progressBar);
        mDownloadingInfo = view.findViewById(R.id.downloadingInfo);

        final OpenH264DownloadHelper codecDownloader =
                LinphoneManager.getInstance().getOpenH264DownloadHelper();
        final OpenH264DownloadHelperListener codecListener =
                new OpenH264DownloadHelperListener() {

                    @Override
                    public void OnProgress(final int current, final int max) {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (current <= max) {
                                            hideAllItems();
                                            mDownloadingInfo.setText(current + " / " + max);
                                            mDownloadingInfo.setVisibility(View.VISIBLE);
                                            mDownloading.setVisibility(View.VISIBLE);
                                            mProgressBar.setMax(max);
                                            mProgressBar.setProgress(current);
                                            mProgressBar.setVisibility(View.VISIBLE);
                                        } else {
                                            hideAllItems();
                                            mDownloaded.setVisibility(View.VISIBLE);
                                            if (Build.VERSION.SDK_INT
                                                    >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                                enabledH264(true);
                                                LinphoneManager.getLc()
                                                        .reloadMsPlugins(
                                                                AssistantActivity.instance()
                                                                        .getApplicationInfo()
                                                                        .nativeLibraryDir);
                                                AssistantActivity.instance().endDownloadCodec();
                                            } else {
                                                // We need to restart due to bad android linker
                                                AssistantActivity.instance().restartApplication();
                                            }
                                        }
                                    }
                                });
                    }

                    @Override
                    public void OnError(final String error) {
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        hideAllItems();
                                        mDownloaded.setText("Sorry an error has occurred.");
                                        mDownloaded.setVisibility(View.VISIBLE);
                                        mOk.setVisibility(View.VISIBLE);
                                        enabledH264(false);
                                        AssistantActivity.instance().endDownloadCodec();
                                    }
                                });
                    }
                };

        codecDownloader.setOpenH264HelperListener(codecListener);

        mYes.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideAllItems();
                        mProgressBar.setVisibility(View.VISIBLE);
                        codecDownloader.downloadCodec();
                    }
                });

        mNo.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        enabledH264(false);
                        AssistantActivity.instance().endDownloadCodec();
                    }
                });
        hideAllItems();

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("mQuestion"))
                mQuestion.setVisibility((Integer) savedInstanceState.getSerializable("mQuestion"));
            else mQuestion.setVisibility(View.VISIBLE);

            if (savedInstanceState.containsKey("mYes"))
                mYes.setVisibility((Integer) savedInstanceState.getSerializable("mYes"));
            else mYes.setVisibility(View.VISIBLE);

            if (savedInstanceState.containsKey("mNo"))
                mNo.setVisibility((Integer) savedInstanceState.getSerializable("mNo"));
            else mNo.setVisibility(View.VISIBLE);

            if (savedInstanceState.containsKey("mDownloading"))
                mDownloading.setVisibility(
                        (Integer) savedInstanceState.getSerializable("mDownloading"));

            if (savedInstanceState.containsKey("mDownloaded"))
                mDownloaded.setVisibility(
                        (Integer) savedInstanceState.getSerializable("mDownloaded"));

            if (savedInstanceState.containsKey("context_bar"))
                mProgressBar.setVisibility(
                        (Integer) savedInstanceState.getSerializable("context_bar"));

            if (savedInstanceState.containsKey("mDownloadingInfo"))
                mDownloadingInfo.setVisibility(
                        (Integer) savedInstanceState.getSerializable("mDownloadingInfo"));

            if (savedInstanceState.containsKey("mOk"))
                mOk.setVisibility((Integer) savedInstanceState.getSerializable("mOk"));
        } else {
            mYes.setVisibility(View.VISIBLE);
            mQuestion.setVisibility(View.VISIBLE);
            mNo.setVisibility(View.VISIBLE);
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mQuestion != null) outState.putSerializable("mQuestion", mQuestion.getVisibility());
        if (mDownloading != null)
            outState.putSerializable("mDownloading", mDownloading.getVisibility());
        if (mDownloaded != null)
            outState.putSerializable("mDownloaded", mDownloaded.getVisibility());
        if (mYes != null) outState.putSerializable("mYes", mYes.getVisibility());
        if (mNo != null) outState.putSerializable("mNo", mNo.getVisibility());
        if (mOk != null) outState.putSerializable("mOk", mOk.getVisibility());
        if (mProgressBar != null)
            outState.putSerializable("context_bar", mProgressBar.getVisibility());
        if (mDownloadingInfo != null)
            outState.putSerializable("mDownloadingInfo", mDownloadingInfo.getVisibility());
        super.onSaveInstanceState(outState);
    }

    private void hideAllItems() {
        if (mQuestion != null) mQuestion.setVisibility(View.INVISIBLE);
        if (mDownloading != null) mDownloading.setVisibility(View.INVISIBLE);
        if (mDownloaded != null) mDownloaded.setVisibility(View.INVISIBLE);
        if (mYes != null) mYes.setVisibility(View.INVISIBLE);
        if (mNo != null) mNo.setVisibility(View.INVISIBLE);
        if (mOk != null) mOk.setVisibility(View.INVISIBLE);
        if (mProgressBar != null) mProgressBar.setVisibility(View.INVISIBLE);
        if (mDownloadingInfo != null) mDownloadingInfo.setVisibility(View.INVISIBLE);
    }

    private void enabledH264(boolean enable) {
        PayloadType h264 = null;
        for (PayloadType pt : LinphoneManager.getLc().getVideoPayloadTypes()) {
            if (pt.getMimeType().equals("H264")) h264 = pt;
        }

        if (h264 != null) {
            h264.enable(enable);
        }
    }
}
