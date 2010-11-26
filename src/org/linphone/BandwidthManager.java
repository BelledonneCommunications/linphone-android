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

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.VideoSize;

public class BandwidthManager {

	public static final int HIGH_RESOLUTION = 0;
	public static final int LOW_RESOLUTION = 1;
	public static final int LOW_BANDWIDTH = 2;

	private static final int[][] bandwidthes = {{512,512}, {128,128}, {80,80}};
	private static BandwidthManager instance;
	
	private int currentProfile = LOW_RESOLUTION; // FIXME first profile never defined in C part
	public int getCurrentProfile() {return currentProfile;}

	public static final synchronized BandwidthManager getInstance() {
		if (instance == null) instance = new BandwidthManager();
		return instance;
	}

	private BandwidthManager() {}

	public void changeTo(int profile) {
		LinphoneCore lc = LinphoneService.instance().getLinphoneCore();
		LinphoneCall lCall = lc.getCurrentCall();
		LinphoneCallParams params = lCall.getCurrentParamsReadOnly().copy();

		if (profile == LOW_BANDWIDTH) { // OR video disabled by settings?
//			lc.enableVideo(false, false);
			params.setVideoEnabled(false);
		} else {
			params.setVideoEnabled(true);
			VideoSize targetVideoSize;
			switch (profile) {
			case LOW_RESOLUTION:
				targetVideoSize = VideoSize.createStandard(VideoSize.HVGA);
				break;
			case HIGH_RESOLUTION:
				targetVideoSize = VideoSize.createStandard(VideoSize.CIF);
				break;
			default:
				throw new RuntimeException("profile not managed : " + profile);
			}
			
			lc.setPreferredVideoSize(targetVideoSize);
			VideoSize actualVideoSize = lc.getPreferredVideoSize();
			if (!targetVideoSize.equals(actualVideoSize)) {
				lc.setPreferredVideoSize(VideoSize.createStandard(VideoSize.QCIF));
			}
		}

		
		lc.setUploadBandwidth(bandwidthes[profile][0]);
		lc.setDownloadBandwidth(bandwidthes[profile][1]);
		
		lc.updateCall(lCall, params);
		currentProfile = profile;
	}
}
