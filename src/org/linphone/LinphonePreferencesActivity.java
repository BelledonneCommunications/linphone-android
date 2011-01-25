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



import org.linphone.core.LinphoneCoreException;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;

public class LinphonePreferencesActivity extends PreferenceActivity {
	private static final int version = Integer.parseInt(Build.VERSION.SDK);
	boolean mIsLowEndCpu = true;
	private AudioManager mAudioManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		boolean enableIlbc=false;
		if (LinphoneService.isready()) {
			// if not ilbc, we are on low end cpu.
			enableIlbc = LinphoneService.instance().getLinphoneCore().findPayloadType("iLBC", 8000)!=null?true:false;
			mIsLowEndCpu=!enableIlbc;
			if (!mIsLowEndCpu && !getPreferenceManager().getSharedPreferences().contains(getString(R.string.pref_echo_cancellation_key))) {
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_echo_cancellation_key), true).commit();
			}
			if (mIsLowEndCpu) {
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_ilbc_key), false).commit();
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_speex16_key), false).commit();
				getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(R.string.pref_codec_speex32_key), false).commit();
			}

		}

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		if (!mIsLowEndCpu) {
			getPreferenceScreen().findPreference(getString(R.string.pref_codec_ilbc_key)).setEnabled(enableIlbc);
			getPreferenceScreen().findPreference(getString(R.string.pref_codec_speex16_key)).setEnabled(enableIlbc);
			//getPreferenceScreen().findPreference(getString(R.string.pref_codec_speex32_key)).setEnabled(enableIlbc);
		}
		getPreferenceScreen().findPreference(getString(R.string.pref_echo_canceller_calibration_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startEcCalibration(preference);
				return false;
			}
		});

		// Force disable video
		if (version < 5 || !enableIlbc) {
			disableCheckbox(R.string.pref_video_enable_key);
		}
		if (getPreferenceManager().getSharedPreferences().getBoolean(DialerActivity.PREF_FIRST_LAUNCH,true)) {
			if (!mIsLowEndCpu  ) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this); 
				builder.setTitle(R.string.ec_calibration_launch_message).setCancelable(false).setPositiveButton(getString(R.string.cont), new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						startEcCalibration(getPreferenceScreen().findPreference(getString(R.string.pref_echo_canceller_calibration_key)));
					}
				}).create().show();

			} 
			getPreferenceManager().getSharedPreferences().edit().putBoolean(DialerActivity.PREF_FIRST_LAUNCH, false).commit();
		}
				
	}
	private void startEcCalibration(Preference preference) {
		try {
			while (mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL) != mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)) {
				mAudioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_RAISE, 0);
			}
			LinphoneService.getLc().startEchoCalibration(preference);
			preference.setSummary(R.string.ec_calibrating);
			preference.getEditor().putBoolean(getString(R.string.pref_echo_canceller_calibration_key), false).commit();
		} catch (LinphoneCoreException e) {
			Log.w(LinphoneService.TAG, "Cannot calibrate EC",e);
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
