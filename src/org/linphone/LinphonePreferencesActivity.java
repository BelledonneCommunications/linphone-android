/*
LinphonePreferencesActivity.java
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



import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.util.Log;

public class LinphonePreferencesActivity extends PreferenceActivity {
	private static final int version = Integer.parseInt(Build.VERSION.SDK);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean enableIlbc=false;
		if (LinphoneService.isready()) {
			// if not ilbc, we are on low end cpu.
			enableIlbc = LinphoneService.instance().getLinphoneCore().findPayloadType("iLBC", 8000)!=null?true:false;
			if (enableIlbc && !getPreferenceManager().getSharedPreferences().contains(getString(R.string.pref_echo_cancellation_key))) {
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_echo_cancellation_key), true).commit();
			}
			if (!enableIlbc) {
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_ilbc_key), false).commit();
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_speex16_key), false).commit();
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_speex32_key), false).commit();
			}

		}

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		if (enableIlbc) {
			getPreferenceScreen().findPreference(getString(R.string.pref_codec_ilbc_key)).setEnabled(enableIlbc);
			getPreferenceScreen().findPreference(getString(R.string.pref_codec_speex16_key)).setEnabled(enableIlbc);
			//getPreferenceScreen().findPreference(getString(R.string.pref_codec_speex32_key)).setEnabled(enableIlbc);
		}

		// Force disable video
		if (version < 5 || !enableIlbc) {
			disableCheckbox(R.string.pref_video_enable_key);
		}
	}

	private void disableCheckbox(int key) {
		getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(key), false).commit();
		CheckBoxPreference box = (CheckBoxPreference) getPreferenceScreen().findPreference(getString(key));
		box.setEnabled(false);
		box.setChecked(false);
	}
	
	
	@Override
	protected void onPause() {
		super.onPause();
		if (isFinishing()) {
			try {
				LinphoneActivity.instance().initFromConf();
			} catch (LinphoneException e) {
				Log.e(LinphoneService.TAG, "cannot update config",e);
			}
		}
	}

}
