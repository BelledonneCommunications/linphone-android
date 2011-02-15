/*
BigManager.java
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

import android.media.AudioManager;
import android.os.Build;

public class LinphoneManager {

	private static LinphoneManager instance;

	public static void routeAudioToSpeaker(AudioManager mAudioManager) {
		if (Integer.parseInt(Build.VERSION.SDK) <= 4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(true); 
		}
		LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore();
		if (lLinphoneCore.isIncall()) {
			/*disable EC*/  
			lLinphoneCore.getCurrentCall().enableEchoCancellation(false);
			lLinphoneCore.getCurrentCall().enableEchoLimiter(true);
		}
		
	}

	public static void routeAudioToReceiver(AudioManager mAudioManager) {
		if (Integer.parseInt(Build.VERSION.SDK) <=4 /*<donut*/) {
			mAudioManager.setRouting(AudioManager.MODE_NORMAL, 
			AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		} else {
			mAudioManager.setSpeakerphoneOn(false); 
		}
		
		LinphoneCore lLinphoneCore = LinphoneService.instance().getLinphoneCore();
		if (lLinphoneCore.isIncall()) {
			//Restore default value
			lLinphoneCore.getCurrentCall().enableEchoCancellation(lLinphoneCore.isEchoCancellationEnabled());
			lLinphoneCore.getCurrentCall().enableEchoLimiter(false);
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
}
