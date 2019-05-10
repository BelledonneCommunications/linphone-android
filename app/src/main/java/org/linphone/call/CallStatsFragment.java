package org.linphone.call;

/*
CallStatsFragment.java
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

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.AddressFamily;
import org.linphone.core.Call;
import org.linphone.core.CallListenerStub;
import org.linphone.core.CallParams;
import org.linphone.core.CallStats;
import org.linphone.core.Core;
import org.linphone.core.PayloadType;
import org.linphone.core.StreamType;
import org.linphone.core.tools.Log;

public class CallStatsFragment extends Fragment {
    private final Handler mHandler = new Handler();
    private Timer mTimer;
    private TimerTask mTask;

    private View mView;
    private DrawerLayout mSideMenu;
    private RelativeLayout mSideMenuContent;

    private HashMap<String, String> mEncoderTexts;
    private HashMap<String, String> mDecoderTexts;

    private Call mCall;
    private CallListenerStub mListener;

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.call_stats, container, false);
        mView = view;

        mEncoderTexts = new HashMap<>();
        mDecoderTexts = new HashMap<>();

        mListener =
                new CallListenerStub() {
                    public void onStateChanged(Call call, Call.State cstate, String message) {
                        if (cstate == Call.State.End || cstate == Call.State.Error) {
                            if (mTimer != null) {
                                Log.i(
                                        "[Call Stats] Call is terminated, stopping mCountDownTimer in charge of stats refreshing.");
                                mTimer.cancel();
                            }
                        }
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            setCall(core.getCurrentCall());
        }
    }

    @Override
    public void onPause() {
        setCall(null);
        super.onPause();
    }

    public void setDrawer(DrawerLayout drawer, RelativeLayout content) {
        mSideMenu = drawer;
        mSideMenuContent = content;

        if (getResources().getBoolean(R.bool.hide_in_call_stats)) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }
    }

    public boolean isOpened() {
        return mSideMenu != null && mSideMenu.isDrawerVisible(Gravity.LEFT);
    }

    public void closeDrawer() {
        openOrCloseSideMenu(false, false);
    }

    public void openOrCloseSideMenu(boolean open, boolean animate) {
        if (mSideMenu == null || mSideMenuContent == null) return;
        if (getResources().getBoolean(R.bool.hide_in_call_stats)) return;

        if (open) {
            mSideMenu.openDrawer(mSideMenuContent, animate);
        } else {
            mSideMenu.closeDrawer(mSideMenuContent, animate);
        }
    }

    public void setCall(Call call) {
        if (mCall != null) {
            mCall.removeListener(mListener);
        }
        mCall = call;
        init(mView);
    }

    private void init(View view) {
        if (getResources().getBoolean(R.bool.hide_in_call_stats)) return;

        if (mTimer != null && mTask != null) {
            mTimer.cancel();
            mTimer = null;
            mTask = null;
        }
        if (mCall == null) {
            return;
        }

        final TextView titleAudio = view.findViewById(R.id.call_stats_audio);
        final TextView titleVideo = view.findViewById(R.id.call_stats_video);
        final TextView codecAudio = view.findViewById(R.id.codec_audio);
        final TextView codecVideo = view.findViewById(R.id.codec_video);
        final TextView encoderAudio = view.findViewById(R.id.encoder_audio);
        final TextView decoderAudio = view.findViewById(R.id.decoder_audio);
        final TextView encoderVideo = view.findViewById(R.id.encoder_video);
        final TextView decoderVideo = view.findViewById(R.id.decoder_video);
        final TextView displayFilter = view.findViewById(R.id.display_filter);
        final TextView dlAudio = view.findViewById(R.id.downloadBandwith_audio);
        final TextView ulAudio = view.findViewById(R.id.uploadBandwith_audio);
        final TextView dlVideo = view.findViewById(R.id.downloadBandwith_video);
        final TextView ulVideo = view.findViewById(R.id.uploadBandwith_video);
        final TextView edlVideo = view.findViewById(R.id.estimatedDownloadBandwidth_video);
        final TextView iceAudio = view.findViewById(R.id.ice_audio);
        final TextView iceVideo = view.findViewById(R.id.ice_video);
        final TextView videoResolutionSent = view.findViewById(R.id.video_resolution_sent);
        final TextView videoResolutionReceived = view.findViewById(R.id.video_resolution_received);
        final TextView videoFpsSent = view.findViewById(R.id.video_fps_sent);
        final TextView videoFpsReceived = view.findViewById(R.id.video_fps_received);
        final TextView senderLossRateAudio = view.findViewById(R.id.senderLossRateAudio);
        final TextView receiverLossRateAudio = view.findViewById(R.id.receiverLossRateAudio);
        final TextView senderLossRateVideo = view.findViewById(R.id.senderLossRateVideo);
        final TextView receiverLossRateVideo = view.findViewById(R.id.receiverLossRateVideo);
        final TextView ipAudio = view.findViewById(R.id.ip_audio);
        final TextView ipVideo = view.findViewById(R.id.ip_video);
        final TextView jitterBufferAudio = view.findViewById(R.id.jitterBufferAudio);
        final View videoLayout = view.findViewById(R.id.callStatsVideo);
        final View audioLayout = view.findViewById(R.id.callStatsAudio);

        mTimer = new Timer();
        mTask =
                new TimerTask() {
                    @Override
                    public void run() {
                        if (mCall == null) {
                            mTimer.cancel();
                            return;
                        }

                        if (titleAudio == null
                                || codecAudio == null
                                || dlVideo == null
                                || edlVideo == null
                                || iceAudio == null
                                || videoResolutionSent == null
                                || videoLayout == null
                                || titleVideo == null
                                || ipVideo == null
                                || ipAudio == null
                                || codecVideo == null
                                || dlAudio == null
                                || ulAudio == null
                                || ulVideo == null
                                || iceVideo == null
                                || videoResolutionReceived == null) {
                            mTimer.cancel();
                            return;
                        }

                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mCall == null) return;

                                        if (mCall.getState() != Call.State.Released) {
                                            CallParams params = mCall.getCurrentParams();
                                            if (params != null) {
                                                CallStats audioStats =
                                                        mCall.getStats(StreamType.Audio);
                                                CallStats videoStats = null;

                                                if (params.videoEnabled())
                                                    videoStats = mCall.getStats(StreamType.Video);

                                                PayloadType payloadAudio =
                                                        params.getUsedAudioPayloadType();
                                                PayloadType payloadVideo =
                                                        params.getUsedVideoPayloadType();

                                                formatText(
                                                        displayFilter,
                                                        getString(
                                                                R.string.call_stats_display_filter),
                                                        mCall.getCore().getVideoDisplayFilter());

                                                displayMediaStats(
                                                        params,
                                                        audioStats,
                                                        payloadAudio,
                                                        audioLayout,
                                                        titleAudio,
                                                        codecAudio,
                                                        dlAudio,
                                                        ulAudio,
                                                        null,
                                                        iceAudio,
                                                        ipAudio,
                                                        senderLossRateAudio,
                                                        receiverLossRateAudio,
                                                        encoderAudio,
                                                        decoderAudio,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        false,
                                                        jitterBufferAudio);

                                                displayMediaStats(
                                                        params,
                                                        videoStats,
                                                        payloadVideo,
                                                        videoLayout,
                                                        titleVideo,
                                                        codecVideo,
                                                        dlVideo,
                                                        ulVideo,
                                                        edlVideo,
                                                        iceVideo,
                                                        ipVideo,
                                                        senderLossRateVideo,
                                                        receiverLossRateVideo,
                                                        encoderVideo,
                                                        decoderVideo,
                                                        videoResolutionSent,
                                                        videoResolutionReceived,
                                                        videoFpsSent,
                                                        videoFpsReceived,
                                                        true,
                                                        null);
                                            }
                                        }
                                    }
                                });
                    }
                };
        mCall.addListener(mListener);
        mTimer.scheduleAtFixedRate(mTask, 0, 1000);
    }

    private void formatText(TextView tv, String name, String value) {
        tv.setText(Html.fromHtml("<b>" + name + " </b>" + value));
    }

    private String getEncoderText(String mime) {
        String ret = mEncoderTexts.get(mime);
        if (ret == null) {
            org.linphone.mediastream.Factory msfactory =
                    LinphoneManager.getCore().getMediastreamerFactory();
            ret = msfactory.getEncoderText(mime);
            mEncoderTexts.put(mime, ret);
        }
        return ret;
    }

    private String getDecoderText(String mime) {
        String ret = mDecoderTexts.get(mime);
        if (ret == null) {
            org.linphone.mediastream.Factory msfactory =
                    LinphoneManager.getCore().getMediastreamerFactory();
            ret = msfactory.getDecoderText(mime);
            mDecoderTexts.put(mime, ret);
        }
        return ret;
    }

    private void displayMediaStats(
            CallParams params,
            CallStats stats,
            PayloadType media,
            View layout,
            TextView title,
            TextView codec,
            TextView dl,
            TextView ul,
            TextView edl,
            TextView ice,
            TextView ip,
            TextView senderLossRate,
            TextView receiverLossRate,
            TextView enc,
            TextView dec,
            TextView videoResolutionSent,
            TextView videoResolutionReceived,
            TextView videoFpsSent,
            TextView videoFpsReceived,
            boolean isVideo,
            TextView jitterBuffer) {
        if (stats != null) {
            String mime = null;

            layout.setVisibility(View.VISIBLE);
            title.setVisibility(TextView.VISIBLE);
            if (media != null) {
                mime = media.getMimeType();
                formatText(
                        codec,
                        getString(R.string.call_stats_codec),
                        mime + " / " + (media.getClockRate() / 1000) + "kHz");
            }
            if (mime != null) {
                formatText(enc, getString(R.string.call_stats_encoder_name), getEncoderText(mime));
                formatText(dec, getString(R.string.call_stats_decoder_name), getDecoderText(mime));
            }
            formatText(
                    dl,
                    getString(R.string.call_stats_download),
                    (int) stats.getDownloadBandwidth() + " kbits/s");
            formatText(
                    ul,
                    getString(R.string.call_stats_upload),
                    (int) stats.getUploadBandwidth() + " kbits/s");
            if (isVideo) {
                formatText(
                        edl,
                        getString(R.string.call_stats_estimated_download),
                        stats.getEstimatedDownloadBandwidth() + " kbits/s");
            }
            formatText(ice, getString(R.string.call_stats_ice), stats.getIceState().toString());
            formatText(
                    ip,
                    getString(R.string.call_stats_ip),
                    (stats.getIpFamilyOfRemote() == AddressFamily.Inet6)
                            ? "IpV6"
                            : (stats.getIpFamilyOfRemote() == AddressFamily.Inet)
                                    ? "IpV4"
                                    : "Unknown");
            formatText(
                    senderLossRate,
                    getString(R.string.call_stats_sender_loss_rate),
                    new DecimalFormat("##.##").format(stats.getSenderLossRate()) + "%");
            formatText(
                    receiverLossRate,
                    getString(R.string.call_stats_receiver_loss_rate),
                    new DecimalFormat("##.##").format(stats.getReceiverLossRate()) + "%");
            if (isVideo) {
                formatText(
                        videoResolutionSent,
                        getString(R.string.call_stats_video_resolution_sent),
                        "\u2191 " + params.getSentVideoDefinition() != null
                                ? params.getSentVideoDefinition().getName()
                                : "");
                formatText(
                        videoResolutionReceived,
                        getString(R.string.call_stats_video_resolution_received),
                        "\u2193 " + params.getReceivedVideoDefinition() != null
                                ? params.getReceivedVideoDefinition().getName()
                                : "");
                formatText(
                        videoFpsSent,
                        getString(R.string.call_stats_video_fps_sent),
                        "\u2191 " + params.getSentFramerate());
                formatText(
                        videoFpsReceived,
                        getString(R.string.call_stats_video_fps_received),
                        "\u2193 " + params.getReceivedFramerate());
            } else {
                formatText(
                        jitterBuffer,
                        getString(R.string.call_stats_jitter_buffer),
                        new DecimalFormat("##.##").format(stats.getJitterBufferSizeMs()) + " ms");
            }
        } else {
            layout.setVisibility(View.GONE);
            title.setVisibility(TextView.GONE);
        }
    }
}
