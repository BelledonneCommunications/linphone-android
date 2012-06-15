package org.linphone.compatibility;

import org.linphone.mediastream.Version;

import android.view.Display;

public class Compatibility {

	public static int getRotation(Display display) {
		if (Version.sdkStrictlyBelow(8)) {
			return API4Compatibility.getRotation(display);
		} else {
			return API8Compatibility.getRotation(display);
		}
	}
	
}
