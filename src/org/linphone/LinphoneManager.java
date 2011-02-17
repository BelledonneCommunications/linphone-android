/*
LinphoneManager.java
Copyright (C) 2010  Belledonne Communications, Grenoble, France

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
package org.linphone;

import org.linphone.core.AndroidCameraRecordManager;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import android.view.WindowManager;

public class LinphoneManager {

	private static LinphoneManager instance;
	private AudioManager mAudioManager;
	private NewOutgoingCallUiListener newOutgoingCallUiListener;
	private SharedPreferences mPref;
	private Resources mR;
	private WindowManager mWindowManager;

	private LinphoneManager() {}

	public void routeAudioToSpeaker() {
		if (Integer.parseInt(Build.VERSION.SDK) <= 4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(true); 
		}
		LinphoneCore lc = LinphoneService.getLc();
		if (lc.isIncall()) {
			/*disable EC*/  
			lc.getCurrentCall().enableEchoCancellation(false);
			lc.getCurrentCall().enableEchoLimiter(true);
		}
		
	}

	public void routeAudioToReceiver() {
		if (Integer.parseInt(Build.VERSION.SDK) <=4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(false); 
		}
		
		LinphoneCore lc = LinphoneService.getLc();
		if (lc.isIncall()) {
			//Restore default value
			lc.getCurrentCall().enableEchoCancellation(lc.isEchoCancellationEnabled());
			lc.getCurrentCall().enableEchoLimiter(false);
		}
	}
	
	public synchronized static final LinphoneManager getInstance() {
		if (instance == null) instance = new LinphoneManager();
		return instance;
	}
	
	public static final LinphoneCore getLc() {
		return LinphoneService.getLc();
	}


	public void setUsefullStuff(AudioManager audioManager, SharedPreferences pref, WindowManager windowManager, Resources r) {
		mAudioManager = audioManager;
		mPref = pref;
		mR = r;
		mWindowManager = windowManager;
	}

	public boolean isSpeakerOn() {
		return (Integer.parseInt(Build.VERSION.SDK) <=4 && mAudioManager.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) 
		|| Integer.parseInt(Build.VERSION.SDK) >4 &&mAudioManager.isSpeakerphoneOn();
	}

	
	public void newOutgoingCall(AddressType address) {
		String to = address.getText().toString();
		if (to.contains(OutgoingCallReceiver.TAG)) {
			to = to.replace(OutgoingCallReceiver.TAG, "");
			address.setText(to);
		}

		LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore(); 
		if (lLinphoneCore.isIncall()) {
			newOutgoingCallUiListener.onAlreadyInCall();
			return;
		}
		LinphoneAddress lAddress;
		try {
			lAddress = lLinphoneCore.interpretUrl(to);
		} catch (LinphoneCoreException e) {
			newOutgoingCallUiListener.onWrongDestinationAddress();
			return;
		}
		lAddress.setDisplayName(address.getDisplayedName());

		try {
			
			boolean prefVideoEnable = mPref.getBoolean(mR.getString(R.string.pref_video_enable_key), false);
			boolean prefInitiateWithVideo = mPref.getBoolean(mR.getString(R.string.pref_video_initiate_call_with_video_key), false);
			resetCameraFromPreferences();
			CallManager.getInstance().inviteAddress(lAddress, prefVideoEnable && prefInitiateWithVideo);

		} catch (LinphoneCoreException e) {
			newOutgoingCallUiListener.onCannotGetCallParameters();
			return;
		}
	}

	
	public void resetCameraFromPreferences() {
		boolean useFrontCam = mPref.getBoolean(mR.getString(R.string.pref_video_use_front_camera_key), false);
		AndroidCameraRecordManager.getInstance().setUseFrontCamera(useFrontCam);
		final int phoneOrientation = 90 * mWindowManager.getDefaultDisplay().getOrientation();
		AndroidCameraRecordManager.getInstance().setPhoneOrientation(phoneOrientation);
	}

	public void setNewOutgoingCallUiListener(NewOutgoingCallUiListener l) {
		this.newOutgoingCallUiListener = l;
	}

	public static interface AddressType {
		void setText(CharSequence s);
		CharSequence getText();
		void setDisplayedName(String s);
		String getDisplayedName();
	}


	public static interface NewOutgoingCallUiListener {
		public void onWrongDestinationAddress();
		public void onCannotGetCallParameters();
		public void onAlreadyInCall();
	}


	public void sendStaticImage(boolean send) {
		LinphoneCore lc =  LinphoneService.getLc();
		if (lc.isIncall()) {
			lc.getCurrentCall().enableCamera(!send);
		}
	}

}
