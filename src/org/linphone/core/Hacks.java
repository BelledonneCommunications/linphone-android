/*
Hacks.java
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
package org.linphone.core;

import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

public final class Hacks {

	private Hacks() {}

	public static boolean isGalaxyS() {
		return isGT9000() || isSC02B();
	}


	public static boolean isGalaxySOrTabWithFrontCamera() {
		return isGalaxySOrTab() && !isGalaxySOrTabWithoutFrontCamera();
	}

	public static boolean isGalaxySOrTab() {
		return isGalaxyS() || isGalaxyTab();
	}

	public static boolean isGalaxyTab() {
		return isGTP1000();
	}

	private static boolean isGT9000() {return Build.DEVICE.startsWith("GT-I9000");}
	private static boolean isSC02B() {return Build.DEVICE.startsWith("SC-02B");}
	private static boolean isGTP1000() {return Build.DEVICE.startsWith("GT-P1000");}
	private static boolean isGalaxySOrTabWithoutFrontCamera() {
		return isSC02B();
	}

/*	private static final boolean log(final String msg) {
		Log.d("Linphone", msg);
		return true;
	}*/

	/* Not working as now
	 * Calling from Galaxy S to PC is "usable" even with no hack; other side is not even with this one*/
	public static void galaxySSwitchToCallStreamUnMuteLowerVolume(AudioManager am) {
		// Switch to call audio channel (Galaxy S)
		am.setSpeakerphoneOn(false);
		sleep(200);

		// Lower volume
		am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 1, 0);

		// Another way to select call channel
		am.setMode(AudioManager.MODE_NORMAL);
		sleep(200);

		// Mic is muted if not doing this
		am.setMicrophoneMute(true);
		sleep(200);
		am.setMicrophoneMute(false);
		sleep(200);
	}

	private static final void sleep(int time) {
		try  {
			Thread.sleep(time);
		} catch(InterruptedException ie){}
	}

	public static void dumpDeviceInformation() {
		StringBuilder sb = new StringBuilder(" ==== Phone information dump ====\n");
		sb.append("DEVICE=").append(Build.DEVICE).append("\n");
		sb.append("MODEL=").append(Build.MODEL).append("\n");
		//MANUFACTURER doesn't exist in android 1.5.
		//sb.append("MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
		sb.append("SDK=").append(Build.VERSION.SDK);

		Log.i(Version.TAG, sb.toString());
	}

	public static boolean needSoftvolume() {
		return isGalaxySOrTab();
	}

	public static boolean needRoutingAPI() {
		return Version.sdkStrictlyBelow(5) /*<donut*/;
	}

	public static boolean needGalaxySAudioHack() {
		return isGalaxySOrTab() && !isSC02B();
	}

	public static boolean needPausingCallForSpeakers() {
//		return false;
		return isGalaxySOrTab() && !isSC02B();
	}
}
