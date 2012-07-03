package org.linphone.compatibility;

import android.view.Display;

public class API4Compatibility {

	public static int getRotation(Display display) {
		return display.getOrientation();
	}
}
