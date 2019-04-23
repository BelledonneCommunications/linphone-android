package org.linphone.fragments;
/*
StatusFragment.java
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

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
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
import org.linphone.LinphoneActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.assistant.AssistantActivity;
import org.linphone.call.CallActivity;
import org.linphone.call.CallIncomingActivity;
import org.linphone.call.CallOutgoingActivity;
import org.linphone.core.Call;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Event;
import org.linphone.core.MediaEncryption;
import org.linphone.core.ProxyConfig;
import org.linphone.core.RegistrationState;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;

public class StatusFragment extends Fragment {
    private final Handler mRefreshHandler = new Handler();
    private TextView mStatusText, mVoicemailCount;
    private ImageView mStatusLed, mCallQuality, mEncryption, mMenu, mVoicemail;
    private Runnable mCallQualityUpdater;
    private boolean mIsInCall, mIsAttached = false;
    private CoreListenerStub mListener;
    private Dialog mZrtpDialog = null;
    private int mDisplayedQuality = -1;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.status, container, false);

        mStatusText = view.findViewById(R.id.status_text);
        mStatusLed = view.findViewById(R.id.status_led);
        mCallQuality = view.findViewById(R.id.call_quality);
        mEncryption = view.findViewById(R.id.encryption);
        mMenu = view.findViewById(R.id.side_menu_button);
        mVoicemail = view.findViewById(R.id.voicemail);
        mVoicemailCount = view.findViewById(R.id.voicemail_count);

        // We create it once to not delay the first display
        populateSliderContent();

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onRegistrationStateChanged(
                            final Core lc,
                            final ProxyConfig proxy,
                            final RegistrationState state,
                            String smessage) {
                        if (!mIsAttached || !LinphoneService.isReady()) {
                            return;
                        }

                        if (lc.getProxyConfigList() == null) {
                            mStatusLed.setImageResource(R.drawable.led_disconnected);
                            mStatusText.setText(getString(R.string.no_account));
                        } else {
                            mStatusLed.setVisibility(View.VISIBLE);
                        }

                        if (lc.getDefaultProxyConfig() != null
                                && lc.getDefaultProxyConfig().equals(proxy)) {
                            mStatusLed.setImageResource(getStatusIconResource(state));
                            mStatusText.setText(getStatusIconText(state));
                        } else if (lc.getDefaultProxyConfig() == null) {
                            mStatusLed.setImageResource(getStatusIconResource(state));
                            mStatusText.setText(getStatusIconText(state));
                        }

                        try {
                            mStatusText.setOnClickListener(
                                    new OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            Core core =
                                                    LinphoneManager
                                                            .getLcIfManagerNotDestroyedOrNull();
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
                    public void onNotifyReceived(
                            Core lc, Event ev, String eventName, Content content) {

                        if (!content.getType().equals("application")) return;
                        if (!content.getSubtype().equals("simple-message-summary")) return;

                        if (content.getSize() == 0) return;

                        int unreadCount = 0;
                        String data = content.getStringBuffer().toLowerCase();
                        String[] voiceMail = data.split("voice-message: ");
                        if (voiceMail.length >= 2) {
                            final String[] intToParse = voiceMail[1].split("/", 0);
                            try {
                                unreadCount = Integer.parseInt(intToParse[0]);
                            } catch (NumberFormatException nfe) {

                            }
                            if (unreadCount > 0) {
                                mVoicemailCount.setText(String.valueOf(unreadCount));
                                mVoicemail.setVisibility(View.VISIBLE);
                                mVoicemailCount.setVisibility(View.VISIBLE);
                            } else {
                                mVoicemail.setVisibility(View.GONE);
                                mVoicemailCount.setVisibility(View.GONE);
                            }
                        }
                    }
                };

        mIsAttached = true;
        Activity activity = getActivity();

        if (activity instanceof LinphoneActivity) {
            ((LinphoneActivity) activity).updateStatusFragment(this);
        } else if (activity instanceof CallActivity) {
            ((CallActivity) activity).updateStatusFragment(this);
        } else if (activity instanceof AssistantActivity) {
            ((AssistantActivity) activity).updateStatusFragment(this);
        }
        mIsInCall =
                activity instanceof CallActivity
                        || activity instanceof CallIncomingActivity
                        || activity instanceof CallOutgoingActivity;

        return view;
    }

    public void setCoreListener() {
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);

            ProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(lc, lpc, lpc.getState(), null);
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mIsAttached = false;
    }

    // NORMAL STATUS BAR

    private void populateSliderContent() {
        if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null) {
            mVoicemailCount.setVisibility(View.GONE);

            if (mIsInCall && mIsAttached) {
                // Call call = LinphoneManager.getLc().getCurrentCall();
                // initCallStatsRefresher(call, callStats);
            } else if (!mIsInCall) {
                mVoicemailCount.setVisibility(View.VISIBLE);
            }

            if (LinphoneManager.getLc().getProxyConfigList().length == 0) {
                mStatusLed.setImageResource(R.drawable.led_disconnected);
                mStatusText.setText(getString(R.string.no_account));
            }
        }
    }

    public void resetAccountStatus() {
        if (LinphoneManager.getLc().getProxyConfigList().length == 0) {
            mStatusLed.setImageResource(R.drawable.led_disconnected);
            mStatusText.setText(getString(R.string.no_account));
        }
    }

    public void enableSideMenu(boolean enabled) {
        mMenu.setEnabled(enabled);
    }

    private int getStatusIconResource(RegistrationState state) {
        try {
            Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
            boolean defaultAccountConnected =
                    (lc != null
                            && lc.getDefaultProxyConfig() != null
                            && lc.getDefaultProxyConfig().getState() == RegistrationState.Ok);
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
        if (!mIsAttached && LinphoneActivity.isInstanciated())
            context = LinphoneActivity.instance();
        else if (!mIsAttached && LinphoneService.isReady()) context = LinphoneService.instance();

        try {
            if (state == RegistrationState.Ok
                    && LinphoneManager.getLcIfManagerNotDestroyedOrNull()
                                    .getDefaultProxyConfig()
                                    .getState()
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

    // INCALL STATUS BAR
    private void startCallQuality() {
        mCallQuality.setVisibility(View.VISIBLE);
        mRefreshHandler.postDelayed(
                mCallQualityUpdater =
                        new Runnable() {
                            final Call mCurrentCall = LinphoneManager.getLc().getCurrentCall();

                            public void run() {
                                if (mCurrentCall == null) {
                                    mCallQualityUpdater = null;
                                    return;
                                }
                                float newQuality = mCurrentCall.getCurrentQuality();
                                updateQualityOfSignalIcon(newQuality);

                                if (mIsInCall) {
                                    mRefreshHandler.postDelayed(this, 1000);
                                } else mCallQualityUpdater = null;
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

    @Override
    public void onResume() {
        super.onResume();

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            ProxyConfig lpc = lc.getDefaultProxyConfig();
            if (lpc != null) {
                mListener.onRegistrationStateChanged(lc, lpc, lpc.getState(), null);
            }

            Call call = lc.getCurrentCall();
            if (mIsInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
                if (call != null) {
                    startCallQuality();
                    refreshStatusItems(call);
                }
                mMenu.setVisibility(View.INVISIBLE);
                mCallQuality.setVisibility(View.VISIBLE);

                // We are obviously connected
                if (lc.getDefaultProxyConfig() == null) {
                    mStatusLed.setImageResource(R.drawable.led_disconnected);
                    mStatusText.setText(getString(R.string.no_account));
                } else {
                    mStatusLed.setImageResource(
                            getStatusIconResource(lc.getDefaultProxyConfig().getState()));
                    mStatusText.setText(getStatusIconText(lc.getDefaultProxyConfig().getState()));
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

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }

        if (mCallQualityUpdater != null) {
            mRefreshHandler.removeCallbacks(mCallQualityUpdater);
            mCallQualityUpdater = null;
        }
    }

    public void refreshStatusItems(final Call call) {
        if (call != null) {
            mVoicemailCount.setVisibility(View.GONE);
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
            Log.w("Can't display ZRTP popup, no Activity");
            return;
        }

        if (mZrtpDialog == null || !mZrtpDialog.isShowing()) {
            String token = call.getAuthenticationToken();

            if (token == null) {
                Log.w("Can't display ZRTP popup, no token !");
                return;
            }
            if (token.length() < 4) {
                Log.w("Can't display ZRTP popup, token is invalid (" + token + ")");
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
}
