/*
PreferencesActivity.java
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
import static org.linphone.R.string.pref_codec_amr_key;
import static org.linphone.R.string.pref_codec_amrwb_key;
import static org.linphone.R.string.pref_codec_ilbc_key;
import static org.linphone.R.string.pref_codec_speex16_key;
import static org.linphone.R.string.pref_echo_cancellation_key;
import static org.linphone.R.string.pref_echo_canceller_calibration_key;
import static org.linphone.R.string.pref_echo_limiter_key;
import static org.linphone.R.string.pref_media_encryption_key;
import static org.linphone.R.string.pref_video_enable_key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.LinphoneManager.LinphoneConfigException;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.setup.SetupActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class PreferencesActivity extends LinphonePreferencesActivity implements EcCalibrationListener {
	private Handler mHandler = new Handler();
	private CheckBoxPreference ecCalibratePref;
	private CheckBoxPreference elPref;
	private CheckBoxPreference ecPref;
	private ListPreference mencPref;
	private int nbAccounts = 1;
	private PreferenceCategory accounts;
	
	private static final int ADD_SIP_ACCOUNT = 0x666;
	private static final int ACCOUNTS_SETTINGS_ID = 1;
	private static final int WIZARD_SETTINGS_ID = 2;
	private static final int CAMERA_SETTINGS_ID = 6;
	private static final int EXIT_SETTINGS_ID = 0;

	private SharedPreferences prefs() {
		return getPreferenceManager().getSharedPreferences();
	}

	private CheckBoxPreference findCheckbox(int key) {
		return (CheckBoxPreference) findPreference(getString(key));
	}

	private void detectAudioCodec(int id, String mime, int rate, boolean hide) {
		boolean enable = LinphoneService.isReady() && LinphoneManager.getLc().findPayloadType(mime, rate)!=null;
		Preference cb = findPreference(id);
		cb.setEnabled(enable);
		if (hide && !enable) {
			cb.setLayoutResource(R.layout.hidden);
		}
	}

	private void detectVideoCodec(int id, String mime) {
		findPreference(id).setEnabled(LinphoneManager.getInstance().detectVideoCodec(mime));
	}

	private void createDynamicAccountsPreferences() {
		accounts = (PreferenceCategory) getPreferenceScreen().getPreference(ACCOUNTS_SETTINGS_ID);
		accounts.removeAll();
		
		// Get already configured extra accounts
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		nbAccounts = prefs.getInt(getString(R.string.pref_extra_accounts), 0);
		for (int i = 0; i < nbAccounts; i++) {
			// For each, add menus to configure it
			addExtraAccountPreferencesButton(accounts, i, false);
		}
	}
	
	public int getNbAccountsExtra() {
		return nbAccounts;
	}
	
	private void addExitButton() {
		Preference me = (Preference) getPreferenceScreen().getPreference(EXIT_SETTINGS_ID);
		
		me.setOnPreferenceClickListener(new OnPreferenceClickListener() 
		{
			public boolean onPreferenceClick(Preference preference) {
				Intent result = new Intent();
				result.putExtra("Exit", true);
				setResult(Activity.RESULT_FIRST_USER, result);
				finish();
				return true;
			}
		});
	}
	
	private void addExtraAccountPreferencesButton(PreferenceCategory parent, final int n, boolean isNewAccount) {
		SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
		if (isNewAccount) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(getString(R.string.pref_extra_accounts), n+1);
			editor.commit();
		}
		
		Preference me = new Preference(PreferencesActivity.this);
		String keyUsername = getString(R.string.pref_username_key);
		String keyDomain = getString(R.string.pref_domain_key);
		if (n > 0) {
			keyUsername += n + "";
			keyDomain += n + "";
		}
		if (prefs.getString(keyUsername, null) == null) {
			me.setTitle(getString(R.string.pref_sipaccount));
		} else {
			me.setTitle(prefs.getString(keyUsername, "") + "@" + prefs.getString(keyDomain, ""));
		}
		
		me.setOnPreferenceClickListener(new OnPreferenceClickListener() 
		{
			public boolean onPreferenceClick(Preference preference) {
				Intent i = new Intent();
				i.putExtra("Account", n);
				i.setClass(PreferencesActivity.this, AccountPreferencesActivity.class);
				startActivityForResult(i, ADD_SIP_ACCOUNT);
				return false;
			}
		});
		
		for (LinphoneProxyConfig lpc : LinphoneManager.getLc().getProxyConfigList()) {
			if (lpc.getIdentity().contains(prefs.getString(keyUsername, "")) && lpc.getIdentity().contains(prefs.getString(keyDomain, ""))) {
				while ((lpc.getState() == RegistrationState.RegistrationProgress || lpc.getState() == RegistrationState.RegistrationNone) && (LinphoneManager.getLc().isNetworkReachable()))
				{ };
				
				if (lpc.getState() == LinphoneCore.RegistrationState.RegistrationOk) {
					me.setWidgetLayoutResource(R.layout.preference_led_connected);
				} else {
					me.setWidgetLayoutResource(R.layout.preference_led_not_connected);
				}
			}
		}
		
		parent.addPreference(me);
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == ADD_SIP_ACCOUNT) {
			//Verify if last created account is filled
			SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
			int n = prefs.getInt(getString(R.string.pref_extra_accounts), 1);
			String keyUsername = getString(R.string.pref_username_key) + (n-1 == 0 ? "" : Integer.toString(n-1));
			
			if (prefs.getString(keyUsername, "").equals("")) {
				//If not, we suppress it to not display a blank field
				SharedPreferences.Editor editor = prefs.edit();
				editor.putInt(getString(R.string.pref_extra_accounts), n-1);
				editor.commit();
			}
			
			try {
				LinphoneManager.getInstance().initAccounts();
			} catch (Exception e) {
				e.printStackTrace();
			}
			createDynamicAccountsPreferences();
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}
	
	private void addWizardPreferenceButton() {
		Preference wizard = (Preference) getPreferenceScreen().getPreference(WIZARD_SETTINGS_ID);
		wizard.setOnPreferenceClickListener(new OnPreferenceClickListener() {
	        public boolean onPreferenceClick(Preference preference) {
	        	Intent intent = new Intent(getApplicationContext(), SetupActivity.class);
	        	finish();
	        	startActivity(intent);
	        	return true;
	        }
        });
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Load the preferences from an XML resource		
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		addExitButton();

		if (!getResources().getBoolean(R.bool.hide_accounts)) {
			createDynamicAccountsPreferences();
		} else {			
			// Hide category
			PreferenceCategory accounts = (PreferenceCategory) getPreferenceScreen().getPreference(ACCOUNTS_SETTINGS_ID);
			accounts.removeAll();
			accounts.setLayoutResource(R.layout.hidden);
		}
		
		if (getResources().getBoolean(R.bool.hide_wizard)) {
			Preference wizard = (Preference) getPreferenceScreen().getPreference(WIZARD_SETTINGS_ID);
			wizard.setLayoutResource(R.layout.hidden);
		} else {
			addWizardPreferenceButton();
		}
		
		addTransportChecboxesListener();
		
		ecCalibratePref = (CheckBoxPreference) findPreference(pref_echo_canceller_calibration_key);
		ecCalibratePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				startEcCalibration();
				return false;
			}
		});
		ecPref = (CheckBoxPreference) findPreference(pref_echo_cancellation_key);
		elPref = (CheckBoxPreference) findPreference(pref_echo_limiter_key);
		mencPref = (ListPreference) findPreference(pref_media_encryption_key);

		boolean fastCpu = Version.isArmv7();
		if (fastCpu) {
			detectAudioCodec(pref_codec_ilbc_key, "iLBC", 8000, false);
			findPreference(pref_codec_speex16_key).setEnabled(true);
			//findPreference(pref_codec_speex32_key)).setEnabled(enableIlbc);
		}
		findPreference(pref_echo_limiter_key).setEnabled(true);

		initializeMediaEncryptionPreferences();
	
		detectAudioCodec(pref_codec_amr_key,"AMR", 8000, false);
        detectAudioCodec(pref_codec_amrwb_key,"AMR-WB", 16000, false);
		//detectAudioCodec(R.string.pref_codec_silk8_key,"SILK",8000, true);
		//detectAudioCodec(R.string.pref_codec_silk12_key,"SILK",12000, true);
		detectAudioCodec(R.string.pref_codec_silk16_key,"SILK",16000, true);
		detectAudioCodec(R.string.pref_codec_silk24_key,"SILK",24000, true);
		detectAudioCodec(R.string.pref_codec_g729_key,"G729",8000, true);
		
		// No video
		if (!Version.isVideoCapable()) {
			uncheckAndDisableCheckbox(pref_video_enable_key);
		} else if (!AndroidCameraConfiguration.hasFrontCamera()) {
			uncheckDisableAndHideCheckbox(R.string.pref_video_use_front_camera_key);
		}

		if (prefs().getBoolean(LinphoneActivity.PREF_FIRST_LAUNCH,true)) {
			doOnFirstLaunch();
		}
		if (Hacks.hasBuiltInEchoCanceller()) {
			uncheckDisableAndHideCheckbox(R.string.pref_echo_limiter_key);
			uncheckDisableAndHideCheckbox(R.string.pref_echo_cancellation_key);
			uncheckDisableAndHideCheckbox(R.string.pref_echo_canceller_calibration_key);
		}


		detectVideoCodec(R.string.pref_video_codec_h264_key, "H264");
		if (!Version.hasNeon())
		{
			// Android without neon doesn't support H264
			findPreference(R.string.pref_video_codec_h264_key).setEnabled(false);
			findPreference(R.string.pref_video_codec_h264_key).setDefaultValue(false);
		}
		
		addEchoPrefsListener();
		
		if (Hacks.needSoftvolume()) checkAndDisableCheckbox(R.string.pref_audio_soft_volume_key);

		if (!LinphoneManager.getLc().isTunnelAvailable()){
			hidePreferenceCategory(R.string.pref_tunnel_key);
		}
		
		if (getResources().getBoolean(R.bool.hide_camera_settings)) {
			PreferenceScreen screen = getPreferenceScreen();
			PreferenceCategory videoSettings = (PreferenceCategory) screen.getPreference(CAMERA_SETTINGS_ID);
			videoSettings.removeAll();
			videoSettings.setLayoutResource(R.layout.hidden);
			
			CheckBoxPreference enableVideo = (CheckBoxPreference) findPreference(R.string.pref_video_enable_key);
			enableVideo.setLayoutResource(R.layout.hidden);
		}
	}

	private void hidePreferenceCategory(int key) {
		PreferenceCategory p = (PreferenceCategory) findPreference(key);
		p.removeAll();
		p.setLayoutResource(R.layout.hidden);
	}

	private void doOnFirstLaunch() {
		manageCheckbox(R.string.pref_echo_limiter_key, !Hacks.hasBuiltInEchoCanceller(), true, false);
		prefs().edit().putBoolean(LinphoneActivity.PREF_FIRST_LAUNCH, false).commit();
	}

	private void initializeMediaEncryptionPreferences() {
		LinphoneCore lc=LinphoneManager.getLc();
		boolean hasZrtp=lc.mediaEncryptionSupported(MediaEncryption.ZRTP);
		boolean hasSrtp=lc.mediaEncryptionSupported(MediaEncryption.SRTP);
		if (!hasSrtp && !hasZrtp){
			mencPref.setEnabled(false);
		}else{
			List<CharSequence> mencEntries=new ArrayList<CharSequence>();
			List<CharSequence> mencEntryValues=new ArrayList<CharSequence>();
			mencEntries.add(getString(R.string.media_encryption_none));
			mencEntryValues.add(getString(R.string.pref_media_encryption_key_none));
			if (hasSrtp){
				mencEntries.add(getString(R.string.media_encryption_srtp));
				mencEntryValues.add(getString(R.string.pref_media_encryption_key_srtp));
			}
			if (hasZrtp){
				mencEntries.add(getString(R.string.media_encryption_zrtp));
				mencEntryValues.add(getString(R.string.pref_media_encryption_key_zrtp));
			}
			CharSequence[] contents=new CharSequence[mencEntries.size()];
			mencEntries.toArray(contents);
			mencPref.setEntries(contents);
			contents=new CharSequence[mencEntryValues.size()];
			mencEntryValues.toArray(contents);
			mencPref.setEntryValues(contents);
			mencPref.setDefaultValue(getString(R.string.media_encryption_none));
			//mencPref.setValueIndex(mencPref.findIndexOfValue(getString(R.string.media_encryption_none)));
		}
	}

	private void addEchoPrefsListener(){
		OnPreferenceChangeListener ec_listener=new OnPreferenceChangeListener(){
			public boolean onPreferenceChange(Preference arg0, Object newValue) {
				Boolean val=(Boolean)newValue;
				if (val){
					elPref.setChecked(!val);
				}
				return true;
			}
		};
		OnPreferenceChangeListener el_listener=new OnPreferenceChangeListener(){
			public boolean onPreferenceChange(Preference arg0, Object newValue) {
				Boolean val=(Boolean)newValue;
				if (val){
					ecPref.setChecked(!val);
				}
				return true;
			}
		};
		ecPref.setOnPreferenceChangeListener(ec_listener);
		elPref.setOnPreferenceChangeListener(el_listener);
	}

	private void addTransportChecboxesListener() {

		final List<CheckBoxPreference> checkboxes = Arrays.asList(
				findCheckbox(R.string.pref_transport_udp_key)
				,findCheckbox(R.string.pref_transport_tcp_key)
				,findCheckbox(R.string.pref_transport_tls_key)
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

			ecCalibratePref.setSummary(ec_calibrating);
			ecCalibratePref.getEditor().putBoolean(getString(pref_echo_canceller_calibration_key), false).commit();
		} catch (LinphoneCoreException e) {
			Log.w(e, "Cannot calibrate EC");
		}	
	}

	public void onEcCalibrationStatus(final EcCalibratorStatus status, final int delayMs) {

		mHandler.post(new Runnable() {
			public void run() {
				if (status == EcCalibratorStatus.Done) {
					ecCalibratePref.setSummary(String.format(getString(R.string.ec_calibrated), delayMs));
					ecCalibratePref.setChecked(true);

				} else if (status == EcCalibratorStatus.Failed) {
					ecCalibratePref.setSummary(R.string.failed);
					ecCalibratePref.setChecked(false);
					elPref.setChecked(true);
					ecPref.setChecked(false);
				}
			}
		});
	}

	private void uncheckDisableAndHideCheckbox(int key) { 
		manageCheckbox(key, false, false, true);
	}

	private void uncheckAndDisableCheckbox(int key) {
		manageCheckbox(key, false, false, false);
	}
	private void checkAndDisableCheckbox(int key) {
		manageCheckbox(key, true, false, false);
	}
	private void manageCheckbox(int key, boolean value, boolean enabled, boolean hidden) {
		CheckBoxPreference box = (CheckBoxPreference) findPreference(key);
		box.setEnabled(enabled);
		box.setChecked(value);
		writeBoolean(key, value);
		if (hidden) box.setLayoutResource(R.layout.hidden);
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

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		
		if (lc != null && (lc.isInComingInvitePending() || lc.isIncall())) {
			Log.w("Call in progress => settings not applied");
			return;
		}

		try {
			LinphoneManager.getInstance().initFromConf();
			lc.setVideoPolicy(LinphoneManager.getInstance().isAutoInitiateVideoCalls(), LinphoneManager.getInstance().isAutoAcceptCamera());
		} catch (LinphoneException e) {
			if (! (e instanceof LinphoneConfigException)) {
				Log.e(e, "Cannot update config");
				return;
			}

			LinphoneActivity.instance().showPreferenceErrorDialog(e.getMessage());
		}
	}
}
