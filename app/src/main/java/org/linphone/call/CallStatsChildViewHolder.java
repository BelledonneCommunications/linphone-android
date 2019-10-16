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
package org.linphone.call;

import android.content.Context;
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
import org.linphone.utils.LinphoneUtils;

public class CallStatsChildViewHolder {
    private Timer mTimer;
    private Call mCall;
    private CallListenerStub mListener;
    private HashMap<String, String> mEncoderTexts;
    private HashMap<String, String> mDecoderTexts;
    private Context mContext;

    private TextView mTitleAudio;
    private TextView mTitleVideo;
    private TextView mCodecAudio;
    private TextView mCodecVideo;
    private TextView mEncoderAudio;
    private TextView mDecoderAudio;
    private TextView mEncoderVideo;
    private TextView mDecoderVideo;
    private TextView mAudioCaptureFilter;
    private TextView mAudioPlayerFilter;
    private TextView mVideoCaptureFilter;
    private TextView mVideoDisplayFilter;
    private TextView mDlAudio;
    private TextView mUlAudio;
    private TextView mDlVideo;
    private TextView mUlVideo;
    private TextView mEdlVideo;
    private TextView mIceAudio;
    private TextView mIceVideo;
    private TextView mVideoResolutionSent;
    private TextView mVideoResolutionReceived;
    private TextView mVideoFpsSent;
    private TextView mVideoFpsReceived;
    private TextView mSenderLossRateAudio;
    private TextView mReceiverLossRateAudio;
    private TextView mSenderLossRateVideo;
    private TextView mReceiverLossRateVideo;
    private TextView mIpAudio;
    private TextView mIpVideo;
    private TextView mJitterBufferAudio;
    private View mVideoLayout;
    private View mAudioLayout;

    public CallStatsChildViewHolder(View view, Context context) {
        mContext = context;

        mEncoderTexts = new HashMap<>();
        mDecoderTexts = new HashMap<>();

        mTitleAudio = view.findViewById(R.id.call_stats_audio);
        mTitleVideo = view.findViewById(R.id.call_stats_video);
        mCodecAudio = view.findViewById(R.id.codec_audio);
        mCodecVideo = view.findViewById(R.id.codec_video);
        mEncoderAudio = view.findViewById(R.id.encoder_audio);
        mDecoderAudio = view.findViewById(R.id.decoder_audio);
        mEncoderVideo = view.findViewById(R.id.encoder_video);
        mDecoderVideo = view.findViewById(R.id.decoder_video);
        mAudioCaptureFilter = view.findViewById(R.id.audio_capture_filter);
        mAudioPlayerFilter = view.findViewById(R.id.audio_player_filter);
        mVideoCaptureFilter = view.findViewById(R.id.video_capture_device);
        mVideoDisplayFilter = view.findViewById(R.id.display_filter);
        mDlAudio = view.findViewById(R.id.downloadBandwith_audio);
        mUlAudio = view.findViewById(R.id.uploadBandwith_audio);
        mDlVideo = view.findViewById(R.id.downloadBandwith_video);
        mUlVideo = view.findViewById(R.id.uploadBandwith_video);
        mEdlVideo = view.findViewById(R.id.estimatedDownloadBandwidth_video);
        mIceAudio = view.findViewById(R.id.ice_audio);
        mIceVideo = view.findViewById(R.id.ice_video);
        mVideoResolutionSent = view.findViewById(R.id.video_resolution_sent);
        mVideoResolutionReceived = view.findViewById(R.id.video_resolution_received);
        mVideoFpsSent = view.findViewById(R.id.video_fps_sent);
        mVideoFpsReceived = view.findViewById(R.id.video_fps_received);
        mSenderLossRateAudio = view.findViewById(R.id.senderLossRateAudio);
        mReceiverLossRateAudio = view.findViewById(R.id.receiverLossRateAudio);
        mSenderLossRateVideo = view.findViewById(R.id.senderLossRateVideo);
        mReceiverLossRateVideo = view.findViewById(R.id.receiverLossRateVideo);
        mIpAudio = view.findViewById(R.id.ip_audio);
        mIpVideo = view.findViewById(R.id.ip_video);
        mJitterBufferAudio = view.findViewById(R.id.jitterBufferAudio);
        mVideoLayout = view.findViewById(R.id.callStatsVideo);
        mAudioLayout = view.findViewById(R.id.callStatsAudio);

        mListener =
                new CallListenerStub() {
                    public void onStateChanged(Call call, Call.State cstate, String message) {
                        if (cstate == Call.State.End || cstate == Call.State.Error) {
                            if (mTimer != null) {
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

                        if (mTitleAudio == null
                                || mCodecAudio == null
                                || mDlVideo == null
                                || mEdlVideo == null
                                || mIceAudio == null
                                || mVideoResolutionSent == null
                                || mVideoLayout == null
                                || mTitleVideo == null
                                || mIpVideo == null
                                || mIpAudio == null
                                || mCodecVideo == null
                                || mDlAudio == null
                                || mUlAudio == null
                                || mUlVideo == null
                                || mIceVideo == null
                                || mVideoResolutionReceived == null) {
                            mTimer.cancel();
                            return;
                        }

                        LinphoneUtils.dispatchOnUIThread(
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
                                                        mAudioPlayerFilter,
                                                        mContext.getString(
                                                                R.string.call_stats_player_filter),
                                                        mCall.getCore().getPlaybackDevice());

                                                formatText(
                                                        mAudioCaptureFilter,
                                                        mContext.getString(
                                                                R.string.call_stats_capture_filter),
                                                        mCall.getCore().getCaptureDevice());

                                                formatText(
                                                        mVideoDisplayFilter,
                                                        mContext.getString(
                                                                R.string.call_stats_display_filter),
                                                        mCall.getCore().getVideoDisplayFilter());

                                                formatText(
                                                        mVideoCaptureFilter,
                                                        mContext.getString(
                                                                R.string.call_stats_capture_filter),
                                                        mCall.getCore().getVideoDevice());

                                                displayMediaStats(
                                                        params,
                                                        audioStats,
                                                        payloadAudio,
                                                        mAudioLayout,
                                                        mTitleAudio,
                                                        mCodecAudio,
                                                        mDlAudio,
                                                        mUlAudio,
                                                        null,
                                                        mIceAudio,
                                                        mIpAudio,
                                                        mSenderLossRateAudio,
                                                        mReceiverLossRateAudio,
                                                        mEncoderAudio,
                                                        mDecoderAudio,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        false,
                                                        mJitterBufferAudio);

                                                displayMediaStats(
                                                        params,
                                                        videoStats,
                                                        payloadVideo,
                                                        mVideoLayout,
                                                        mTitleVideo,
                                                        mCodecVideo,
                                                        mDlVideo,
                                                        mUlVideo,
                                                        mEdlVideo,
                                                        mIceVideo,
                                                        mIpVideo,
                                                        mSenderLossRateVideo,
                                                        mReceiverLossRateVideo,
                                                        mEncoderVideo,
                                                        mDecoderVideo,
                                                        mVideoResolutionSent,
                                                        mVideoResolutionReceived,
                                                        mVideoFpsSent,
                                                        mVideoFpsReceived,
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
