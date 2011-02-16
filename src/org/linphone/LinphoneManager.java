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

import org.linphone.core.LinphoneCore;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;

public class LinphoneManager {

	private static LinphoneManager instance;
	private AudioManager mAudioManager;


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

	public static void startLinphone() {
		
	}

	public void setAudioManager(AudioManager manager) {
		mAudioManager = manager;
		
	}

	public boolean isSpeakerOn() {
		return (Integer.parseInt(Build.VERSION.SDK) <=4 && mAudioManager.getRouting(AudioManager.MODE_NORMAL) == AudioManager.ROUTE_SPEAKER) 
		|| Integer.parseInt(Build.VERSION.SDK) >4 &&mAudioManager.isSpeakerphoneOn();
	}



}
