package org.linphone.compatibility;

import android.view.Display;

public class API8Compatibility {

	public static int getRotation(Display display) {
		return display.getRotation();
	}
}
