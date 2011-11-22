/*
PreferenceManager.java
Copyright (C) 2011  Belledonne Communications, Grenoble, France

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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class LinphonePreferenceManager {

	private static LinphonePreferenceManager instance;
	private Context c;
	private SharedPreferences p;

	public LinphonePreferenceManager(Context context) {
		c = context.getApplicationContext();
		p = PreferenceManager.getDefaultSharedPreferences(c);
	}

	private String getString(int key) {
		return c.getString(key);
	}

	public boolean useSoftvolume() {
		return p.getBoolean(
				getString(R.string.pref_audio_soft_volume_key), false);
	}

	public boolean useAudioRoutingAPIHack() {
		return p.getBoolean(
				getString(R.string.pref_audio_hacks_use_routing_api_key), false);
	}

	public boolean useGalaxySHack() {
		return p.getBoolean(
				getString(R.string.pref_audio_hacks_use_galaxys_hack_key), false);
	}

	public int useSpecificAudioModeHack() {
		return Integer.parseInt(p.getString(getString(R.string.pref_audio_use_specific_mode_key), "0"));
	}

	public static final synchronized LinphonePreferenceManager getInstance(Context c) {
		if (instance == null) {
			instance = new LinphonePreferenceManager(c.getApplicationContext());
		}
		return instance;
	}

}
