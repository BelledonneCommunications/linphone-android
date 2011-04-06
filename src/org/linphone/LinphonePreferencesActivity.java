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


import static org.linphone.R.string.ec_calibrating;
import static org.linphone.R.string.ec_calibration_launch_message;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_echo_canceller_calibration_key;
import static org.linphone.R.string.pref_video_enable_key;

import java.util.Arrays;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.Version;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.video.AndroidCameraRecordManager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import android.widget.Toast;

public class LinphonePreferencesActivity extends PreferenceActivity implements EcCalibrationListener {
	private Handler mHandler = new Handler();
	private CheckBoxPreference ecPref;

	private SharedPreferences prefs() {
		return getPreferenceManager().getSharedPreferences();
	}

	private CheckBoxPreference findCheckbox(int key) {
		return (CheckBoxPreference) findPreference(getString(key));
	}

	private void detectAudioCodec(int id, String mime, int rate) {
		boolean enable = LinphoneService.isReady() && LinphoneManager.getLc().findPayloadType(mime, rate)!=null;
		findPreference(id).setEnabled(enable);
	}
	private void detectVideoCodec(int id, String mime) {
		findPreference(id).setEnabled(LinphoneManager.getInstance().detectVideoCodec(mime));
	}
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

		addTransportChecboxesListener();
		
		ecPref = (CheckBoxPreference) findPreference(pref_echo_canceller_calibration_key);
		ecPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startEcCalibration();
				return false;
			}
		});

		boolean fastCpu = Version.isArmv7();
		if (fastCpu) {
			detectAudioCodec(pref_codec_ilbc_key, "iLBC", 8000);
			findPreference(pref_codec_speex16_key).setEnabled(true);
			//findPreference(pref_codec_speex32_key)).setEnabled(enableIlbc);
		}

		// No video
		if (Version.sdkStrictlyBelow(5) || !fastCpu || !LinphoneManager.getInstance().hasCamera()) {
			disableCheckbox(pref_video_enable_key);
		}
		if (prefs().getBoolean(LinphoneActivity.PREF_FIRST_LAUNCH,true)) {
			if (fastCpu) {
				Toast.makeText(this, getString(ec_calibration_launch_message), Toast.LENGTH_LONG).show();
				startEcCalibration();
			}

			prefs().edit().putBoolean(LinphoneActivity.PREF_FIRST_LAUNCH, false).commit();
		}

		detectVideoCodec(R.string.pref_video_codec_h264_key, "H264");

		if (!AndroidCameraRecordManager.getInstance().hasFrontCamera()) {
			disableAndHideCheckbox(R.string.pref_video_use_front_camera_key);
		}
	}
	

	private void addTransportChecboxesListener() {

		final List<CheckBoxPreference> checkboxes = Arrays.asList(
				findCheckbox(R.string.pref_transport_udp_key)
				,findCheckbox(R.string.pref_transport_tcp_key)
//				,findCheckbox(R.string.pref_transport_tls_key)
				);
		

		OnPreferenceChangeListener changedListener = new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((Boolean) newValue) {
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						p.setChecked(false);
					}
					return true;
				} else {
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						if (p.isChecked()) return true;
					}
					return false;
				}
			}
		};
		
		OnPreferenceClickListener clickListener = new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				// Forbid no protocol selection
				
				if (((CheckBoxPreference) preference).isChecked()) {
					// Trying to unckeck
					for (CheckBoxPreference p : checkboxes) {
						if (p == preference) continue;
						if (p.isChecked()) return false;
					}
					/*Toast.makeText(LinphonePreferencesActivity.this,
							getString(R.string.at_least_a_protocol),
							Toast.LENGTH_SHORT).show();*/
					return true;
				}
				return false;
			}
		};

		for (CheckBoxPreference c : checkboxes) {
			c.setOnPreferenceChangeListener(changedListener);
			c.setOnPreferenceClickListener(clickListener);
		}
	}

	private synchronized void startEcCalibration() {
		try {
			LinphoneManager.getInstance().startEcCalibration(this);

			ecPref.setSummary(ec_calibrating);
			ecPref.getEditor().putBoolean(getString(pref_echo_canceller_calibration_key), false).commit();
		} catch (LinphoneCoreException e) {
			Log.w(LinphoneManager.TAG, "Cannot calibrate EC",e);
		}	
	}

	public void onEcCalibrationStatus(final EcCalibratorStatus status, final int delayMs) {

		mHandler.post(new Runnable() {
			public void run() {
				if (status == EcCalibratorStatus.Done) {
					ecPref.setSummary(String.format(getString(R.string.ec_calibrated), delayMs));
					ecPref.setChecked(true);

				} else if (status == EcCalibratorStatus.Failed) {
					ecPref.setSummary(R.string.failed);
					ecPref.setChecked(false);
				}
			}
		});
	}

	private void disableAndHideCheckbox(int key) { 
		writeBoolean(key, false);
		CheckBoxPreference box = (CheckBoxPreference) findPreference(key);
		box.setEnabled(false);
		box.setChecked(false);
		box.setLayoutResource(R.layout.hidden);
	}

	private void disableCheckbox(int key) {
		writeBoolean(key, false);
		CheckBoxPreference box = (CheckBoxPreference) findPreference(key);
		box.setEnabled(false);
		box.setChecked(false);
	}

	private Preference findPreference(int key) {
		return getPreferenceManager().findPreference(getString(key));
	}

	private void writeBoolean(int key, boolean value) {
		prefs().edit().putBoolean(getString(key), value).commit();
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
