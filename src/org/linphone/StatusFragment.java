package org.linphone;
/*
StatusFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author Sylvain Berfini
 */
public class StatusFragment extends Fragment {
	private static StatusFragment instance;
	private Handler mHandler = new Handler();
	private Handler refreshHandler = new Handler();
	private TextView statusText;
	private ImageView statusLed, callQuality, encryption;
	private Runnable mCallQualityUpdater;
	private boolean isInCall, isAttached = false;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, 
        Bundle savedInstanceState) {
		instance = this;
		View view = inflater.inflate(R.layout.status, container, false);
		
		statusText = (TextView) view.findViewById(R.id.statusText);
		statusLed = (ImageView) view.findViewById(R.id.statusLed);
		callQuality = (ImageView) view.findViewById(R.id.callQuality);
		encryption = (ImageView) view.findViewById(R.id.encryption);
		
        return view;
    }

	/**
	 * @return null if not ready yet
	 */
	public static StatusFragment instance() { 
		return instance;
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		isAttached = true;
		
		if (activity instanceof LinphoneActivity) {
			((LinphoneActivity) activity).updateStatusFragment(this);
			isInCall = false;
		} else if (activity instanceof InCallActivity) {
			((InCallActivity) activity).updateStatusFragment(this);
			isInCall = true;
		}
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		isAttached = false;
	}
	
	public void registrationStateChanged(final RegistrationState state) {
		if (!isAttached)
			return;
		
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				try {
					if (state == RegistrationState.RegistrationOk && LinphoneManager.getLc().getDefaultProxyConfig().isRegistered()) {
						statusLed.setImageResource(R.drawable.led_connected);
						statusText.setText(getString(R.string.status_connected));
					} else if (state == RegistrationState.RegistrationProgress) {
						statusLed.setImageResource(R.drawable.led_inprogress);
						statusText.setText(getString(R.string.status_in_progress));
					} else if (state == RegistrationState.RegistrationFailed) {
						statusLed.setImageResource(R.drawable.led_error);
						statusText.setText(getString(R.string.status_error));
					} else {
						statusLed.setImageResource(R.drawable.led_disconnected);
						statusText.setText(getString(R.string.status_not_connected));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
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
				
				int oldQuality = 0;
				float newQuality = mCurrentCall.getCurrentQuality();
				if ((int) newQuality != oldQuality) {
					updateQualityOfSignalIcon(newQuality);
				}
				
				if (isInCall) {
					refreshHandler.postDelayed(this, 1000);
				} else
					mCallQualityUpdater = null;
			}
		}, 1000);
	}
	
	void updateQualityOfSignalIcon(float quality) {
		if (quality >= 4) // Good Quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_3);
		} else if (quality >= 3) // Average quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_2);
		} else if (quality >= 2) // Low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_1);
		} else if (quality >= 1) // Very low quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_1);
		} else // Worst quality
		{
			callQuality.setImageResource(
					R.drawable.call_quality_indicator_0);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (isInCall) {
			startCallQuality();
			refreshEncryptionIcon();
			
			// We are obviously connected
			statusLed.setImageResource(R.drawable.led_connected);
			statusText.setText(getString(R.string.status_connected));
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mCallQualityUpdater != null) {
			refreshHandler.removeCallbacks(mCallQualityUpdater);
			mCallQualityUpdater = null;
		}
	}

	public void refreshEncryptionIcon() {
		LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
		if (call != null && encryption != null) {
			MediaEncryption mediaEncryption = call.getCurrentParamsCopy().getMediaEncryption();
			
			encryption.setVisibility(View.VISIBLE);
			
			if (mediaEncryption == MediaEncryption.SRTP || (mediaEncryption == MediaEncryption.ZRTP && call.isAuthenticationTokenVerified())) {
				encryption.setImageResource(R.drawable.security_ok);
			} else if (mediaEncryption == MediaEncryption.ZRTP && !call.isAuthenticationTokenVerified()) {
				encryption.setImageResource(R.drawable.security_pending);
			} else {
				encryption.setImageResource(R.drawable.security_ko);
			}
		}
	}
}
