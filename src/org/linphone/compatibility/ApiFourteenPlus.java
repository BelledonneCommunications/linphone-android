package org.linphone.compatibility;

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.AudioManager;
import android.preference.Preference;
import android.preference.TwoStatePreference;
import android.view.View;

/*
ApiFourteenPlus.java
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
/**
 * @author Sylvain Berfini
 */
@TargetApi(14)
public class ApiFourteenPlus {

	public static void setPreferenceChecked(Preference preference, boolean checked) {
		((TwoStatePreference) preference).setChecked(checked);
	}
	
	public static boolean isPreferenceChecked(Preference preference) {
		return ((TwoStatePreference) preference).isChecked();
	}
	
	public static void hideNavigationBar(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
	}
	
	public static void showNavigationBar(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
	}

	public static String getAudioManagerEventForBluetoothConnectionStateChangedEvent() {
		return AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED;
	}
}
