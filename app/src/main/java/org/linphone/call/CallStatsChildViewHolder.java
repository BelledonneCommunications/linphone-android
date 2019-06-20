package org.linphone.call;

/*
CallStatsChildViewHolder.java
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

import android.content.Context;
import android.os.Handler;
import android.text.Html;
import android.view.View;
import android.widget.TextView;
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
import org.linphone.core.PayloadType;
import org.linphone.core.StreamType;

public class CallStatsChildViewHolder {
    private Timer mTimer;
    private Call mCall;
    private final Handler mHandler = new Handler();
    private CallListenerStub mListener;
    private HashMap<String, String> mEncoderTexts;
    private HashMap<String, String> mDecoderTexts;
    private Context mContext;
    private CallStatsAdapter mAdapter;

    private TextView titleAudio;
    private TextView titleVideo;
    private TextView codecAudio;
    private TextView codecVideo;
    private TextView encoderAudio;
    private TextView decoderAudio;
    private TextView encoderVideo;
    private TextView decoderVideo;
    private TextView displayFilter;
    private TextView dlAudio;
    private TextView ulAudio;
    private TextView dlVideo;
    private TextView ulVideo;
    private TextView edlVideo;
    private TextView iceAudio;
    private TextView iceVideo;
    private TextView videoResolutionSent;
    private TextView videoResolutionReceived;
    private TextView videoFpsSent;
    private TextView videoFpsReceived;
    private TextView senderLossRateAudio;
    private TextView receiverLossRateAudio;
    private TextView senderLossRateVideo;
    private TextView receiverLossRateVideo;
    private TextView ipAudio;
    private TextView ipVideo;
    private TextView jitterBufferAudio;
    private View videoLayout;
    private View audioLayout;

    public CallStatsChildViewHolder(View view, Context context) {
        mContext = context;

        mEncoderTexts = new HashMap<>();
        mDecoderTexts = new HashMap<>();
        mAdapter = new CallStatsAdapter(mContext);

        titleAudio = view.findViewById(R.id.call_stats_audio);
        titleVideo = view.findViewById(R.id.call_stats_video);
        codecAudio = view.findViewById(R.id.codec_audio);
        codecVideo = view.findViewById(R.id.codec_video);
        encoderAudio = view.findViewById(R.id.encoder_audio);
        decoderAudio = view.findViewById(R.id.decoder_audio);
        encoderVideo = view.findViewById(R.id.encoder_video);
        decoderVideo = view.findViewById(R.id.decoder_video);
        displayFilter = view.findViewById(R.id.display_filter);
        dlAudio = view.findViewById(R.id.downloadBandwith_audio);
        ulAudio = view.findViewById(R.id.uploadBandwith_audio);
        dlVideo = view.findViewById(R.id.downloadBandwith_video);
        ulVideo = view.findViewById(R.id.uploadBandwith_video);
        edlVideo = view.findViewById(R.id.estimatedDownloadBandwidth_video);
        iceAudio = view.findViewById(R.id.ice_audio);
        iceVideo = view.findViewById(R.id.ice_video);
        videoResolutionSent = view.findViewById(R.id.video_resolution_sent);
        videoResolutionReceived = view.findViewById(R.id.video_resolution_received);
        videoFpsSent = view.findViewById(R.id.video_fps_sent);
        videoFpsReceived = view.findViewById(R.id.video_fps_received);
        senderLossRateAudio = view.findViewById(R.id.senderLossRateAudio);
        receiverLossRateAudio = view.findViewById(R.id.receiverLossRateAudio);
        senderLossRateVideo = view.findViewById(R.id.senderLossRateVideo);
        receiverLossRateVideo = view.findViewById(R.id.receiverLossRateVideo);
        ipAudio = view.findViewById(R.id.ip_audio);
        ipVideo = view.findViewById(R.id.ip_video);
        jitterBufferAudio = view.findViewById(R.id.jitterBufferAudio);
        videoLayout = view.findViewById(R.id.callStatsVideo);
        audioLayout = view.findViewById(R.id.callStatsAudio);

        mListener =
                new CallListenerStub() {
                    public void onStateChanged(Call call, Call.State cstate, String message) {
                        if (cstate == Call.State.End || cstate == Call.State.Error) {
                            if (mTimer != null) {
                                // Refresh the call list
                                mAdapter.refresh();
                                org.linphone.core.tools.Log.i(
                                        "[Call Stats] Call is terminated, stopping mCountDownTimer in charge of stats refreshing.");
                                mTimer.cancel();
                            }
                        }
                    }
                };
    }

    public void setCall(Call call) {
        if (mCall != null) {
            mCall.removeListener(mListener);
        }
        mCall = call;
        mCall.addListener(mListener);

        init();
    }

    private void init() {
        TimerTask mTask;

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
                                        if (mCall == null) {
                                            return;
                                        }

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
                                                        mContext.getString(
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
        mTimer.scheduleAtFixedRate(mTask, 0, 1000);
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
                        mContext.getString(R.string.call_stats_codec),
                        mime + " / " + (media.getClockRate() / 1000) + "kHz");
            }
            if (mime != null) {

                formatText(
                        enc,
                        mContext.getString(R.string.call_stats_encoder_name),
                        getEncoderText(mime));
                formatText(
                        dec,
                        mContext.getString(R.string.call_stats_decoder_name),
                        getDecoderText(mime));
            }
            formatText(
                    dl,
                    mContext.getString(R.string.call_stats_download),
                    (int) stats.getDownloadBandwidth() + " kbits/s");
            formatText(
                    ul,
                    mContext.getString(R.string.call_stats_upload),
                    (int) stats.getUploadBandwidth() + " kbits/s");
            if (isVideo) {
                formatText(
                        edl,
                        mContext.getString(R.string.call_stats_estimated_download),
                        stats.getEstimatedDownloadBandwidth() + " kbits/s");
            }
            formatText(
                    ice,
                    mContext.getString(R.string.call_stats_ice),
                    stats.getIceState().toString());
            formatText(
                    ip,
                    mContext.getString(R.string.call_stats_ip),
                    (stats.getIpFamilyOfRemote() == AddressFamily.Inet6)
                            ? "IpV6"
                            : (stats.getIpFamilyOfRemote() == AddressFamily.Inet)
                                    ? "IpV4"
                                    : "Unknown");

            formatText(
                    senderLossRate,
                    mContext.getString(R.string.call_stats_sender_loss_rate),
                    new DecimalFormat("##.##").format(stats.getSenderLossRate()) + "%");
            formatText(
                    receiverLossRate,
                    mContext.getString(R.string.call_stats_receiver_loss_rate),
                    new DecimalFormat("##.##").format(stats.getReceiverLossRate()) + "%");

            if (isVideo) {
                formatText(
                        videoResolutionSent,
                        mContext.getString(R.string.call_stats_video_resolution_sent),
                        "\u2191 " + params.getSentVideoDefinition() != null
                                ? params.getSentVideoDefinition().getName()
                                : "");
                formatText(
                        videoResolutionReceived,
                        mContext.getString(R.string.call_stats_video_resolution_received),
                        "\u2193 " + params.getReceivedVideoDefinition() != null
                                ? params.getReceivedVideoDefinition().getName()
                                : "");
                formatText(
                        videoFpsSent,
                        mContext.getString(R.string.call_stats_video_fps_sent),
                        "\u2191 " + params.getSentFramerate());
                formatText(
                        videoFpsReceived,
                        mContext.getString(R.string.call_stats_video_fps_received),
                        "\u2193 " + params.getReceivedFramerate());
            } else {
                formatText(
                        jitterBuffer,
                        mContext.getString(R.string.call_stats_jitter_buffer),
                        new DecimalFormat("##.##").format(stats.getJitterBufferSizeMs()) + " ms");
            }
        } else {
            layout.setVisibility(View.GONE);
            title.setVisibility(TextView.GONE);
        }
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

    private void formatText(TextView tv, String name, String value) {
        tv.setText(Html.fromHtml("<b>" + name + " </b>" + value));
    }
}
