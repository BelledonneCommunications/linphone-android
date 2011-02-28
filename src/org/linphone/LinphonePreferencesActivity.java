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


import static android.media.AudioManager.STREAM_VOICE_CALL;
import static org.linphone.R.string.cont;
import static org.linphone.R.string.ec_calibrating;
import static org.linphone.R.string.ec_calibration_launch_message;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_codec_speex32_key;
import static org.linphone.R.string.pref_echo_cancellation_key;
import static org.linphone.R.string.pref_echo_canceller_calibration_key;
import static org.linphone.R.string.pref_video_enable_key;

import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Version;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

public class LinphonePreferencesActivity extends PreferenceActivity {
	private boolean mIsLowEndCpu = true;
	private AudioManager mAudioManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAudioManager = ((AudioManager)getSystemService(Context.AUDIO_SERVICE));
		boolean enableIlbc=false;
		if (LinphoneService.isReady()) {
			// if not ilbc, we are on low end cpu.
			enableIlbc = LinphoneManager.getLc().findPayloadType("iLBC", 8000)!=null?true:false;
			mIsLowEndCpu=!enableIlbc;
			if (!mIsLowEndCpu && !getPreferenceManager().getSharedPreferences().contains(getString(pref_echo_cancellation_key))) {
				writeBoolean(pref_echo_cancellation_key, true);
			}
			if (mIsLowEndCpu) {
				writeBoolean(pref_codec_ilbc_key, false);
				writeBoolean(pref_codec_speex16_key, false);
				writeBoolean(pref_codec_speex32_key, false);
			}

		}

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		if (!mIsLowEndCpu) {
			findPreference(pref_codec_ilbc_key).setEnabled(enableIlbc);
			findPreference(pref_codec_speex16_key).setEnabled(enableIlbc);
			//findPreference(pref_codec_speex32_key)).setEnabled(enableIlbc);
		}
		findPreference(pref_echo_canceller_calibration_key)
		.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startEcCalibration(preference);
				return false;
			}
		});

		// Force disable video
		if (Version.sdkStrictlyBelow(5) || !enableIlbc || !LinphoneManager.getInstance().hasCamera()) {
			disableCheckbox(pref_video_enable_key);
		}
		if (getPreferenceManager().getSharedPreferences().getBoolean(DialerActivity.PREF_FIRST_LAUNCH,true)) {
			if (!mIsLowEndCpu  ) {
				AlertDialog.Builder builder = new AlertDialog.Builder(this); 
				builder.setTitle(ec_calibration_launch_message).setCancelable(false).setPositiveButton(getString(cont), new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						startEcCalibration(findPreference(pref_echo_canceller_calibration_key));
					}
				}).create().show();

			} 
			getPreferenceManager().getSharedPreferences().edit().putBoolean(DialerActivity.PREF_FIRST_LAUNCH, false).commit();
		}
				
	}
	private synchronized void startEcCalibration(Preference preference) {
		try {
			int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
			int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
			mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);

			LinphoneManager.getLc().startEchoCalibration(preference);
			
			mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
			
			preference.setSummary(ec_calibrating);
			preference.getEditor().putBoolean(getString(pref_echo_canceller_calibration_key), false).commit();
		} catch (LinphoneCoreException e) {
			Log.w(LinphoneManager.TAG, "Cannot calibrate EC",e);
		}	
	}

	private void disableCheckbox(int key) {
		writeBoolean(key, false);
		CheckBoxPreference box = (CheckBoxPreference) findPreference(key);
		box.setEnabled(false);
		box.setChecked(false);
	}
	
	private Preference findPreference(int key) {
		return getPreferenceScreen().findPreference(getString(key));
	}
	
	private void writeBoolean(int key, boolean value) {
		getPreferenceManager().getSharedPreferences().edit().putBoolean(getString(key), value).commit();
	}
	
	@Override
	protected void onPause() {
		super.onPause();

		if (!isFinishing()) return;


		try {
			LinphoneManager.getInstance().initFromConf(getApplicationContext());
		} catch (LinphoneException e) {

			if (! (e instanceof LinphoneConfigException)) {
				Log.e(LinphoneManager.TAG, "Cannot update config",e);
				return;
			}

			LinphoneActivity.instance().showPreferenceErrorDialog(e.getMessage());
		}
	}

}
