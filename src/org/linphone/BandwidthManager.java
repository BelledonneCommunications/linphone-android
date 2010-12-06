/*
BandwithManager.java
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
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.VideoSize;

import android.hardware.Camera.Size;

public class BandwidthManager {

	public static final int HIGH_RESOLUTION = 0;
	public static final int LOW_RESOLUTION = 1;
	public static final int LOW_BANDWIDTH = 2;
	private static final boolean portraitMode = true; // FIXME: preference?

	private static final int[][] bandwidthes = {{256,256}, {128,128}, {80,80}};
	private static BandwidthManager instance;
	
	private int currentProfile = HIGH_RESOLUTION;
	public int getCurrentProfile() {return currentProfile;}

	public static final synchronized BandwidthManager getInstance() {
		if (instance == null) instance = new BandwidthManager();
		return instance;
	}


	private BandwidthManager() {
		// FIXME register a listener on NetworkManager to get notified of network state
		// FIXME register a listener on Preference to get notified of change in video enable value
		
		// FIXME initially get those values
	}

	private boolean userRestriction;
	public boolean isUserRestriction() {return userRestriction;}
	public void setUserRestriction(boolean limit) {
		userRestriction = limit;
		computeNewProfile();
	}
	private boolean videoEnabledInSettings = true;
	
	
	private void computeNewProfile() {
		int newProfile = userRestriction ? LOW_RESOLUTION : HIGH_RESOLUTION;
		if (newProfile != currentProfile) {
			currentProfile = newProfile;
			onProfileChanged(currentProfile);
		}
	}

	private void onProfileChanged(int newProfile) {
		//
		LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
		lc.setUploadBandwidth(bandwidthes[newProfile][0]);
		lc.setDownloadBandwidth(bandwidthes[newProfile][1]);

		// Setting Linphone Core Preferred Video Size
		if (newProfile != LOW_BANDWIDTH) {
			VideoSize targetVideoSize = getProfileVideoSize(newProfile);
			
			lc.setPreferredVideoSize(targetVideoSize);
			VideoSize actualVideoSize = lc.getPreferredVideoSize();
			if (!targetVideoSize.equals(actualVideoSize)) {
				lc.setPreferredVideoSize(VideoSize.createStandard(VideoSize.QCIF, portraitMode));
			}
		}

		if (lc.isIncall()) {
			LinphoneCall lCall = lc.getCurrentCall();
			LinphoneCallParams params = lCall.getCurrentParamsCopy();
			
			// Update video parm if
			if (newProfile == LOW_BANDWIDTH) {
				params.setVideoEnabled(false);
			} else {
				params.setVideoEnabled(true);
				
			}
			
			lc.updateCall(lCall, params);
		}
	}
	

	private VideoSize getProfileVideoSize(int profile) {
		switch (profile) {
		case LOW_RESOLUTION:
			return closestVideoSize(VideoSize.createStandard(VideoSize.QCIF, portraitMode));
		case HIGH_RESOLUTION:
			return closestVideoSize(VideoSize.createStandard(VideoSize.QVGA, portraitMode));
		default:
			throw new RuntimeException("profile not managed : " + profile);
		}
	}

	private VideoSize closestVideoSize(VideoSize vSize) {
		boolean invert = vSize.getHeight() > vSize.getWidth();
		int testHeight = invert?vSize.getWidth():vSize.getHeight();
		int testWidth = invert?vSize.getHeight():vSize.getWidth();

		for (Size s : AndroidCameraRecordManager.getInstance().supportedVideoSizes()) {
			if (s.height == testHeight && s.width == testWidth) {
				return vSize;
			}
		}

		return VideoSize.createStandard(VideoSize.QCIF, portraitMode);
	}
}
