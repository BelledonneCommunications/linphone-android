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

import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.linphone.LinphoneContext;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.MediaEncryption;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.LinphoneUtils;

public class CallStatusBarFragment extends Fragment {
    private TextView mStatusText;
    private ImageView mStatusLed, mCallQuality, mEncryption;
    private Runnable mCallQualityUpdater;
    private CoreListenerStub mListener;
    private Dialog mZrtpDialog = null;
    private int mDisplayedQuality = -1;
    private StatsClikedListener mStatsListener;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.call_status_bar, container, false);

        mStatusText = view.findViewById(R.id.status_text);
        mStatusLed = view.findViewById(R.id.status_led);
        mCallQuality = view.findViewById(R.id.call_quality);
        mEncryption = view.findViewById(R.id.encryption);

        mStatsListener = null;
        mCallQuality.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mStatsListener != null) {
                            mStatsListener.onStatsClicked();
                        }
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onRegistrationStateChanged(
                            final Core core,
                            final ProxyConfig proxy,
                            final RegistrationState state,
                            String message) {
                        if (core.getProxyConfigList() == null) {
                            mStatusLed.setImageResource(R.drawable.led_disconnected);
                            mStatusText.setText(getString(R.string.no_account));
                        } else {
                            mStatusLed.setVisibility(View.VISIBLE);
                        }

                        if (core.getDefaultProxyConfig() != null
                                && core.getDefaultProxyConfig().equals(proxy)) {
                            mStatusLed.setImageResource(getStatusIconResource(state));
                            mStatusText.setText(getStatusIconText(state));
                        } else if (core.getDefaultProxyConfig() == null) {
                            mStatusLed.setImageResource(getStatusIconResource(state));
                            mStatusText.setText(getStatusIconText(state));
                        }

                        try {
                            mStatusText.setOnClickListener(
                                    new OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Core core = LinphoneManager.getCore();
                                            if (core != null) {
                                                core.refreshRegisters();
                                            }
                                        }
                                    });
                        } catch (IllegalStateException ise) {
                            Log.e(ise);
                        }
                    }

                    @Override
                    public void onCallStateChanged(
                            Core core, Call call, Call.State state, String message) {
                        if (state == Call.State.Resuming || state == Call.State.StreamsRunning) {
                            refreshStatusItems(call);
                        }
                    }

                    @Override
                    public void onCallEncryptionChanged(
                            Core core, Call call, boolean on, String authenticationToken) {
                        if (call.getCurrentParams()
                                        .getMediaEncryption()
                                        .equals(MediaEncryption.ZRTP)
                                && !call.getAuthenticationTokenVerified()) {
                            showZRTPDialog(call);
                        }
                        refreshStatusItems(call);
                    }
                };

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);
            ProxyConfig lpc = core.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(core, lpc, lpc.getState(), null);
            }

            Call call = core.getCurrentCall();
            if (call != null || core.getConferenceSize() > 1 || core.getCallsNb() > 0) {
                if (call != null) {
                    startCallQuality();
                    refreshStatusItems(call);

                    if (!call.getAuthenticationTokenVerified()) {
                        showZRTPDialog(call);
                    }
                }

                // We are obviously connected
                if (core.getDefaultProxyConfig() == null) {
                    mStatusLed.setImageResource(R.drawable.led_disconnected);
                    mStatusText.setText(getString(R.string.no_account));
                } else {
                    mStatusLed.setImageResource(
                            getStatusIconResource(core.getDefaultProxyConfig().getState()));
                    mStatusText.setText(getStatusIconText(core.getDefaultProxyConfig().getState()));
                }
            }
        } else {
            mStatusText.setVisibility(View.VISIBLE);
            mEncryption.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (LinphoneContext.isReady()) {
            Core core = LinphoneManager.getCore();
            if (core != null) {
                core.removeListener(mListener);
            }
        }

        if (mCallQualityUpdater != null) {
            LinphoneUtils.removeFromUIThreadDispatcher(mCallQualityUpdater);
            mCallQualityUpdater = null;
        }
    }

    public void setStatsListener(StatsClikedListener listener) {
        mStatsListener = listener;
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            Core core = LinphoneManager.getCore();
            boolean defaultAccountConnected =
                    (core != null
                            && core.getDefaultProxyConfig() != null
                            && core.getDefaultProxyConfig().getState() == RegistrationState.Ok);
            if (state == RegistrationState.Ok && defaultAccountConnected) {
                return R.drawable.led_connected;
            } else if (state == RegistrationState.Progress) {
                return R.drawable.led_inprogress;
            } else if (state == RegistrationState.Failed) {
                return R.drawable.led_error;
            } else {
                return R.drawable.led_disconnected;
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return R.drawable.led_disconnected;
    }

    private String getStatusIconText(RegistrationState state) {
        Context context = getActivity();
        try {
            if (state == RegistrationState.Ok
                    && LinphoneManager.getCore().getDefaultProxyConfig().getState()
                            == RegistrationState.Ok) {
                return context.getString(R.string.status_connected);
            } else if (state == RegistrationState.Progress) {
                return context.getString(R.string.status_in_progress);
            } else if (state == RegistrationState.Failed) {
                return context.getString(R.string.status_error);
            } else {
                return context.getString(R.string.status_not_connected);
            }
        } catch (Exception e) {
            Log.e(e);
        }

        return context.getString(R.string.status_not_connected);
    }

    private void startCallQuality() {
        LinphoneUtils.dispatchOnUIThreadAfter(
                mCallQualityUpdater =
                        new Runnable() {
                            final Call mCurrentCall = LinphoneManager.getCore().getCurrentCall();

                            public void run() {
                                if (mCurrentCall == null) {
                                    mCallQualityUpdater = null;
                                    return;
                                }
                                float newQuality = mCurrentCall.getCurrentQuality();
                                updateQualityOfSignalIcon(newQuality);

                                LinphoneUtils.dispatchOnUIThreadAfter(this, 1000);
                            }
                        },
                1000);
    }

    private void updateQualityOfSignalIcon(float quality) {
        int iQuality = (int) quality;

        if (iQuality == mDisplayedQuality) return;
        if (quality >= 4) // Good Quality
        {
            mCallQuality.setImageResource(R.drawable.call_quality_indicator_4);
        } else if (quality >= 3) // Average quality
        {
            mCallQuality.setImageResource(R.drawable.call_quality_indicator_3);
        } else if (quality >= 2) // Low quality
        {
            mCallQuality.setImageResource(R.drawable.call_quality_indicator_2);
        } else if (quality >= 1) // Very low quality
        {
            mCallQuality.setImageResource(R.drawable.call_quality_indicator_1);
        } else // Worst quality
        {
            mCallQuality.setImageResource(R.drawable.call_quality_indicator_0);
        }
        mDisplayedQuality = iQuality;
    }

    public void refreshStatusItems(final Call call) {
        if (call != null) {
            if (call.getDir() == Call.Dir.Incoming
                    && call.getState() == Call.State.IncomingReceived
                    && LinphonePreferences.instance().isMediaEncryptionMandatory()) {
                // If the incoming call view is displayed while encryption is mandatory,
                // we can safely show the security_ok icon
                mEncryption.setImageResource(R.drawable.security_ok);
                mEncryption.setVisibility(View.VISIBLE);
                return;
            }

            MediaEncryption mediaEncryption = call.getCurrentParams().getMediaEncryption();
            mEncryption.setVisibility(View.VISIBLE);

            if (mediaEncryption == MediaEncryption.SRTP
                    || (mediaEncryption == MediaEncryption.ZRTP
                            && call.getAuthenticationTokenVerified())
                    || mediaEncryption == MediaEncryption.DTLS) {
                mEncryption.setImageResource(R.drawable.security_ok);
            } else if (mediaEncryption == MediaEncryption.ZRTP
                    && !call.getAuthenticationTokenVerified()) {
                mEncryption.setImageResource(R.drawable.security_pending);
            } else {
                mEncryption.setImageResource(R.drawable.security_ko);
                // Do not show the unsecure icon if user doesn't want to do call mEncryption
                if (LinphonePreferences.instance().getMediaEncryption() == MediaEncryption.None) {
                    mEncryption.setVisibility(View.GONE);
                }
            }

            if (mediaEncryption == MediaEncryption.ZRTP) {
                mEncryption.setOnClickListener(
                        new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showZRTPDialog(call);
                            }
                        });
            } else {
                mEncryption.setOnClickListener(null);
            }
        }
    }

    public void showZRTPDialog(final Call call) {
        if (getActivity() == null) {
            Log.w("[Status Fragment] Can't display ZRTP popup, no Activity");
            return;
        }

        if (mZrtpDialog == null || !mZrtpDialog.isShowing()) {
            String token = call.getAuthenticationToken();

            if (token == null) {
                Log.w("[Status Fragment] Can't display ZRTP popup, no token !");
                return;
            }
            if (token.length() < 4) {
                Log.w(
                        "[Status Fragment] Can't display ZRTP popup, token is invalid ("
                                + token
                                + ")");
                return;
            }

            mZrtpDialog = new Dialog(getActivity());
            mZrtpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mZrtpDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            mZrtpDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            mZrtpDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Drawable d =
                    new ColorDrawable(
                            ContextCompat.getColor(getActivity(), R.color.dark_grey_color));
            d.setAlpha(200);
            mZrtpDialog.setContentView(R.layout.dialog);
            mZrtpDialog
                    .getWindow()
                    .setLayout(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT);
            mZrtpDialog.getWindow().setBackgroundDrawable(d);
            String zrtpToRead, zrtpToListen;

            if (call.getDir().equals(Call.Dir.Incoming)) {
                zrtpToRead = token.substring(0, 2);
                zrtpToListen = token.substring(2);
            } else {
                zrtpToListen = token.substring(0, 2);
                zrtpToRead = token.substring(2);
            }

            TextView localSas = mZrtpDialog.findViewById(R.id.zrtp_sas_local);
            localSas.setText(zrtpToRead.toUpperCase());
            TextView remoteSas = mZrtpDialog.findViewById(R.id.zrtp_sas_remote);
            remoteSas.setText(zrtpToListen.toUpperCase());
            TextView message = mZrtpDialog.findViewById(R.id.dialog_message);
            message.setVisibility(View.GONE);
            mZrtpDialog.findViewById(R.id.dialog_zrtp_layout).setVisibility(View.VISIBLE);

            TextView title = mZrtpDialog.findViewById(R.id.dialog_title);
            title.setText(getString(R.string.zrtp_dialog_title));
            title.setVisibility(View.VISIBLE);

            Button delete = mZrtpDialog.findViewById(R.id.dialog_delete_button);
            delete.setText(R.string.deny);
            Button cancel = mZrtpDialog.findViewById(R.id.dialog_cancel_button);
            cancel.setVisibility(View.GONE);
            Button accept = mZrtpDialog.findViewById(R.id.dialog_ok_button);
            accept.setVisibility(View.VISIBLE);
            accept.setText(R.string.accept);

            ImageView icon = mZrtpDialog.findViewById(R.id.dialog_icon);
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(R.drawable.security_2_indicator);

            delete.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (call != null) {
                                LinphoneManager.getInstance().lastCallSasRejected(true);
                                call.setAuthenticationTokenVerified(false);
                                if (mEncryption != null) {
                                    mEncryption.setImageResource(R.drawable.security_ko);
                                }
                            }
                            mZrtpDialog.dismiss();
                        }
                    });

            accept.setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            call.setAuthenticationTokenVerified(true);
                            if (mEncryption != null) {
                                mEncryption.setImageResource(R.drawable.security_ok);
                            }
                            mZrtpDialog.dismiss();
                        }
                    });
            mZrtpDialog.show();
        }
    }

    public interface StatsClikedListener {
        void onStatsClicked();
    }
}
