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
	private static Context c;
	private static SharedPreferences p;
	
	public LinphonePreferenceManager() {
		p = PreferenceManager.getDefaultSharedPreferences(c);
	}
	
	private String getString(int key) {
		return c.getString(key);
	}
	
	public static final synchronized LinphonePreferenceManager getInstance() {
		if (c == null) throw new RuntimeException("need a context");
		if (instance == null) instance = new LinphonePreferenceManager();
		return instance;
	}

	public static final void setContext(Context context) {
		c = context.getApplicationContext();
	}

	
	public boolean useSoftvolume() {
		return p.getBoolean(
				getString(R.string.pref_audio_soft_volume_key), false);
	}
}
