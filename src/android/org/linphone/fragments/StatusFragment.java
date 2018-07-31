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
import org.linphone.call.CallActivity;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.assistant.AssistantActivity;
import org.linphone.core.Call;
import org.linphone.core.Content;
import org.linphone.core.Core;
import org.linphone.core.MediaEncryption;
import org.linphone.core.RegistrationState;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Event;
import org.linphone.core.ProxyConfig;
import org.linphone.mediastream.Log;

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

import static org.linphone.LinphoneUtils.getTextFromRegistrationStatement;

public class StatusFragment extends Fragment {
	private Handler refreshHandler = new Handler();
	private TextView statusText, voicemailCount;
	private ImageView statusLed, callQuality, menu, voicemail;
	private Runnable mCallQualityUpdater;
	private boolean isInCall, isAttached = false;
	private CoreListenerStub mListener;
	private int mDisplayedQuality = -1;

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.status, container, false);

		statusText = (TextView) view.findViewById(R.id.status_text);
		statusLed = (ImageView) view.findViewById(R.id.status_led);
		callQuality = (ImageView) view.findViewById(R.id.call_quality);
		menu = (ImageView) view.findViewById(R.id.side_menu_button);
		voicemail = (ImageView) view.findViewById(R.id.voicemail);
		voicemailCount = (TextView) view.findViewById(R.id.voicemail_count);

		// We create it once to not delay the first display
		populateSliderContent();

		mListener = new CoreListenerStub(){
			@Override
			public void onRegistrationStateChanged(final Core lc, final ProxyConfig proxy, final RegistrationState state, String smessage) {
				if (!isAttached || !LinphoneService.isReady()) {
					return;
				}

				if(lc.getProxyConfigList() == null){
					statusLed.setImageResource(R.drawable.led_disconnected);
					//statusText.setText(getString(R.string.no_account));
				} else {
					statusLed.setVisibility(View.VISIBLE);
				}

				if (lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().equals(proxy)) {
					statusLed.setImageResource(getStatusIconResource(state, true));
					//statusText.setText(getStatusIconText(state));
				} else if(lc.getDefaultProxyConfig() == null) {
					statusLed.setImageResource(getStatusIconResource(state, true));
					//statusText.setText(getStatusIconText(state));
				}

				try {
					statusLed.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							lc.refreshRegisters();
						}
					});
				} catch (IllegalStateException ise) {}
			}

			@Override
			public void onNotifyReceived(Core lc, Event ev, String eventName, Content content) {

				if(!content.getType().equals("application")) return;
				if(!content.getSubtype().equals("simple-message-summary")) return;

				if (content.getSize() == 0) return;

				int unreadCount = -1;
				String data = content.getStringBuffer();
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
		isAttached = false;
	}

	//NORMAL STATUS BAR

	private void populateSliderContent() {
		if (LinphoneManager.isInstanciated() && LinphoneManager.getLc() != null) {
			voicemailCount.setVisibility(View.GONE);

			if (isInCall && isAttached) {
				//Call call = LinphoneManager.getLc().getCurrentCall();
				//initCallStatsRefresher(call, callStats);
			} else if (!isInCall) {
				voicemailCount.setVisibility(View.VISIBLE);
			}

			if(LinphoneManager.getLc().getProxyConfigList().length == 0){
				statusLed.setImageResource(R.drawable.led_disconnected);
				//statusText.setText(getString(R.string.no_account));
			}
		}
	}

	public void resetAccountStatus(){
		if(LinphoneManager.getLc().getProxyConfigList().length == 0){
			statusLed.setImageResource(R.drawable.led_disconnected);
			//statusText.setText(getString(R.string.no_account));
		}
	}

	public void enableSideMenu(boolean enabled) {
		menu.setEnabled(enabled);
	}

	private int getStatusIconResource(RegistrationState state, boolean isDefaultAccount) {
		try {
			Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
			boolean defaultAccountConnected = (isDefaultAccount && lc != null && lc.getDefaultProxyConfig() != null && lc.getDefaultProxyConfig().getState() == RegistrationState.Ok) || !isDefaultAccount;
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

	private String getStatusIconText(ProxyConfig proxy) {
		Context context = getActivity();
		if (!isAttached && LinphoneActivity.isInstanciated())
			context = LinphoneActivity.instance();
		else if (!isAttached && LinphoneService.isReady())
			context = LinphoneService.instance();

		return getTextFromRegistrationStatement(context, proxy);
	}

	//INCALL STATUS BAR
	private void startCallQuality() {
		callQuality.setVisibility(View.VISIBLE);
		refreshHandler.postDelayed(mCallQualityUpdater = new Runnable() {
			Call mCurrentCall = LinphoneManager.getLc()
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

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
			ProxyConfig lpc = lc.getDefaultProxyConfig();
			if (lpc != null) {
				mListener.onRegistrationStateChanged(lc, lpc, lpc.getState(), null);
			}

			Call call = lc.getCurrentCall();
			if (isInCall && (call != null || lc.getConferenceSize() > 1 || lc.getCallsNb() > 0)) {
				if (call != null) {
					startCallQuality();
				}
				menu.setVisibility(View.INVISIBLE);
				callQuality.setVisibility(View.VISIBLE);

				// We are obviously connected
				if(lc.getDefaultProxyConfig() == null){
					statusLed.setImageResource(R.drawable.led_disconnected);
					//statusText.setText(getString(R.string.no_account));
				} else {
					statusLed.setImageResource(getStatusIconResource(lc.getDefaultProxyConfig().getState(),true));
					//statusText.setText(getStatusIconText(lc.getDefaultProxyConfig()));
				}
			}
		} else {
			//statusText.setVisibility(View.VISIBLE);
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
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater = null;
		}
	}
}
