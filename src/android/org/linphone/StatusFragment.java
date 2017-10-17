package org.linphone;
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
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.linphone.assistant.AssistantActivity;
import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

public class StatusFragment extends Fragment {
	private Handler refreshHandler = new Handler();
	private TextView statusText, voicemailCount;
	private ImageView statusLed, callQuality, encryption, menu, voicemail;
	private Runnable mCallQualityUpdater;
	private boolean isInCall, isAttached = false, isZrtpAsk;
	private LinphoneCoreListenerBase mListener;
	private Dialog ZRTPdialog = null;
	private int mDisplayedQuality = -1;

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.status, container, false);

		statusText = (TextView) view.findViewById(R.id.status_text);
		statusLed = (ImageView) view.findViewById(R.id.status_led);
		callQuality = (ImageView) view.findViewById(R.id.call_quality);
		encryption = (ImageView) view.findViewById(R.id.encryption);
		menu = (ImageView) view.findViewById(R.id.side_menu_button);
		voicemail = (ImageView) view.findViewById(R.id.voicemail);
		voicemailCount = (TextView) view.findViewById(R.id.voicemail_count);

		// We create it once to not delay the first display
		populateSliderContent();

		mListener = new LinphoneCoreListenerBase(){
			@Override
			public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig proxy, final LinphoneCore.RegistrationState state, String smessage) {
				if (!isAttached || !LinphoneService.isReady()) {
					return;
				}

				if(lc.getProxyConfigList() == null){
					statusLed.setImageResource(R.drawable.led_disconnected);
					statusText.setText(getString(R.string.no_account));
				} else {
					statusLed.setVisibility(View.VISIBLE);
				}

				if (lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().equals(proxy)) {
					statusLed.setImageResource(getStatusIconResource(state, true));
					statusText.setText(getStatusIconText(state));
				} else if(lc.getDefaultProxyConfig() == null) {
					statusLed.setImageResource(getStatusIconResource(state, true));
					statusText.setText(getStatusIconText(state));
				}

				try {
					statusText.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							lc.refreshRegisters();
						}
					});
				} catch (IllegalStateException ise) {}
			}

			@Override
			public void notifyReceived(LinphoneCore lc, LinphoneEvent ev, String eventName, LinphoneContent content) {

				if(!content.getType().equals("application")) return;
				if(!content.getSubtype().equals("simple-message-summary")) return;

				if (content.getData() == null) return;

				int unreadCount = -1;
				String data = content.getDataAsString();
				String[] voiceMail = data.split("voice-message: ");
				final String[] intToParse = voiceMail[1].split("/",0);

				unreadCount = Integer.parseInt(intToParse[0]);
				if (unreadCount > 0) {
					voicemailCount.setText(unreadCount);
					voicemail.setVisibility(View.VISIBLE);
					voicemailCount.setVisibility(View.VISIBLE);
				} else {
					voicemail.setVisibility(View.GONE);
					voicemailCount.setVisibility(View.GONE);
				}
			}

		};

		isAttached = true;
		Activity activity = getActivity();

		if (activity instanceof LinphoneActivity) {
			((LinphoneActivity) activity).updateStatusFragment(this);
			isInCall = false;
		} else if (activity instanceof CallActivity) {
			((CallActivity) activity).updateStatusFragment(this);
			isInCall = true;
		} else if (activity instanceof AssistantActivity) {
			((AssistantActivity) activity).updateStatusFragment(this);
			isInCall = false;
		}

        return view;
    }

	public void setLinphoneCoreListener() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);

			LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				mListener.registrationState(lc, lpc, lpc.getState(), null);
			}
		}
	}

	@Override
	public void onDetach() {
		super.onDetach();
		isAttached = false;
	}

	//NORMAL STATUS BAR

	private void populateSliderContent() {
		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null) {
			voicemailCount.setVisibility(View.GONE);

			if (isInCall && isAttached) {
				//LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
				//initCallStatsRefresher(call, callStats);
			} else if (!isInCall) {
				voicemailCount.setVisibility(View.VISIBLE);
			}

			if(LinphoneManager.getLc().getProxyConfigList().length == 0){
				statusLed.setImageResource(R.drawable.led_disconnected);
				statusText.setText(getString(R.string.no_account));
			}
		}
	}

	public void resetAccountStatus(){
		if(LinphoneManager.getLc().getProxyConfigList().length == 0){
			statusLed.setImageResource(R.drawable.led_disconnected);
			statusText.setText(getString(R.string.no_account));
		}
	}

	public void enableSideMenu(boolean enabled) {
		menu.setEnabled(enabled);
	}

	private int getStatusIconResource(LinphoneCore.RegistrationState state, boolean isDefaultAccount) {
		try {
			LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			boolean defaultAccountConnected = (isDefaultAccount && lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().isRegistered()) || !isDefaultAccount;
			if (state == RegistrationState.RegistrationOk && defaultAccountConnected) {
				return R.drawable.led_connected;
			} else if (state == RegistrationState.RegistrationProgress) {
				return R.drawable.led_inprogress;
			} else if (state == RegistrationState.RegistrationFailed) {
				return R.drawable.led_error;
			} else {
				return R.drawable.led_disconnected;
			}
		} catch (Exception e) {
			Log.e(e);
		}

		return R.drawable.led_disconnected;
	}

	private String getStatusIconText(LinphoneCore.RegistrationState state) {
		Context context = getActivity();
		if (!isAttached && LinphoneActivity.isInstanciated())
			context = LinphoneActivity.instance();
		else if (!isAttached && LinphoneService.isReady())
			context = LinphoneService.instance();

		try {
			if (state == RegistrationState.RegistrationOk && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getDefaultProxyConfig().isRegistered()) {
				return context.getString(R.string.status_connected);
			} else if (state == RegistrationState.RegistrationProgress) {
				return context.getString(R.string.status_in_progress);
			} else if (state == RegistrationState.RegistrationFailed) {
				return context.getString(R.string.status_error);
			} else {
				return context.getString(R.string.status_not_connected);
			}
		} catch (Exception e) {
			Log.e(e);
		}

		return context.getString(R.string.status_not_connected);
	}

	//INCALL STATUS BAR
	private void startCallQuality() {
		callQuality.setVisibility(View.VISIBLE);
		refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
			LinphoneCall mCurrentCall = LinphoneManager.getLc()
					.getCurrentCall();

			public void run() {
				if (mCurrentCall == null) {
					mCallQualityUpdater = null;
					return;
				}
				float newQuality = mCurrentCall.getCurrentQuality();
				updateQualityOfSignalIcon(newQuality);

				if (isInCall) {
					refreshHandler.postDelayed(this, 1000);
				} else
					mCallQualityUpdater = null;
			}
		}, 1000);
	}

	void updateQualityOfSignalIcon(float quality) {
		int iQuality = (int) quality;

		if (iQuality == mDisplayedQuality) return;
		if (quality >= 4) // Good Quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_4);
		} else if (quality >= 3) // Average quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_3);
		} else if (quality >= 2) // Low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_2);
		} else if (quality >= 1) // Very low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_1);
		} else // Worst quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_0);
		}
		mDisplayedQuality = iQuality;
	}

	@Override
	public void onResume() {
		super.onResume();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
			LinphoneProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				mListener.registrationState(lc, lpc, lpc.getState(), null);
			}

			LinphoneCall call = lc.getCurrentCall();
			if (isInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
				if (call != null) {
					startCallQuality();
					refreshStatusItems(call, call.getCurrentParams().getVideoEnabled());
				}
				menu.setVisibility(View.INVISIBLE);
				encryption.setVisibility(View.VISIBLE);
				callQuality.setVisibility(View.VISIBLE);

				// We are obviously connected
				if(lc.getDefaultProxyConfig() == null){
					statusLed.setImageResource(R.drawable.led_disconnected);
					statusText.setText(getString(R.string.no_account));
				} else {
					statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState(),true));
					statusText.setText(getStatusIconText(lc.getDefaultProxyConfig().getState()));
				}
			}
		} else {
			statusText.setVisibility(View.VISIBLE);
			encryption.setVisibility(View.GONE);
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}

		if (mCallQualityUpdater != null) {
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater = null;
		}
	}

	public void refreshStatusItems(final LinphoneCall call, boolean isVideoEnabled) {
		if (call != null) {
			voicemailCount.setVisibility(View.GONE);
			MediaEncryption mediaEncryption = call.getCurrentParams().getMediaEncryption();

			if (isVideoEnabled) {
				//background.setVisibility(View.GONE);
			} else {
				//background.setVisibility(View.VISIBLE);
			}

			if (mediaEncryption == MediaEncryption.SRTP || (mediaEncryption == MediaEncryption.ZRTP && call.isAuthenticationTokenVerified()) || mediaEncryption == MediaEncryption.DTLS) {
				encryption.setImageResource(R.drawable.security_ok);
			} else if (mediaEncryption == MediaEncryption.ZRTP && !call.isAuthenticationTokenVerified()) {
				encryption.setImageResource(R.drawable.security_pending);
			} else {
				encryption.setImageResource(R.drawable.security_ko);
			}

			if (mediaEncryption == MediaEncryption.ZRTP) {
				encryption.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						showZRTPDialog(call);
					}
				});
			} else {
				encryption.setOnClickListener(null);
			}
		}
	}

	public void showZRTPDialog(final LinphoneCall call) {
		if (getActivity() == null) {
			Log.w("Can't display ZRTP popup, no Activity");
			return;
		}

		if(ZRTPdialog == null || !ZRTPdialog.isShowing()) {
			String token = call.getAuthenticationToken();

			if (token == null){
				Log.w("Can't display ZRTP popup, no token !");
				return;
			}
			if (token.length()<4){
				Log.w("Can't display ZRTP popup, token is invalid ("+token+")");
				return;
			}

			ZRTPdialog = new Dialog(getActivity());
			ZRTPdialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
			Drawable d = new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.colorC));
			d.setAlpha(200);
			ZRTPdialog.setContentView(R.layout.dialog);
			ZRTPdialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
			ZRTPdialog.getWindow().setBackgroundDrawable(d);
			String zrtpToRead, zrtpToListen;
			isZrtpAsk = true;

			if (call.getDirection().equals(CallDirection.Incoming)) {
				zrtpToRead = token.substring(0,2);
				zrtpToListen = token.substring(2);
			} else {
				zrtpToListen = token.substring(0,2);
				zrtpToRead = token.substring(2);
			}

			// Obiane specific dev : display sas notif only if screen locked
			KeyguardManager myKM = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
			if( myKM.inKeyguardRestrictedInputMode()) {
				//Screen is locked
				LinphoneService.instance().displaySasNotification(call.getAuthenticationToken());
			}
			TextView customText = (TextView) ZRTPdialog.findViewById(R.id.customText);
			String newText = getString(R.string.zrtp_dialog1).replace("%s", zrtpToRead)
					+ getString(R.string.zrtp_dialog2).replace("%s", zrtpToListen);
			customText.setText(newText);
			Button delete = (Button) ZRTPdialog.findViewById(R.id.delete_button);
			delete.setText(R.string.accept);
			Button cancel = (Button) ZRTPdialog.findViewById(R.id.cancel);
			cancel.setText(R.string.deny);

			delete.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					call.setAuthenticationTokenVerified(true);
					if (encryption != null) {
						encryption.setImageResource(R.drawable.security_ok);
					}
					isZrtpAsk = false;
					ZRTPdialog.dismiss();
					LinphoneService.instance().removeSasNotification();
				}
			});

			cancel.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					if (call != null) {
						call.setAuthenticationTokenVerified(false);
						if (encryption != null) {
							encryption.setImageResource(R.drawable.security_ko);
						}
					}
					isZrtpAsk = false;
					ZRTPdialog.dismiss();
					LinphoneService.instance().removeSasNotification();
				}
			});
			ZRTPdialog.show();
		}
	}

	public boolean getisZrtpAsk() {
		return isZrtpAsk;
	}

	public void setisZrtpAsk(boolean bool) {
		isZrtpAsk = bool;
	}
}
