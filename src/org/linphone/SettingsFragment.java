package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.mediastream.Log;
import org.linphone.ui.PreferencesListFragment;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;

public class SettingsFragment extends PreferencesListFragment implements EcCalibrationListener {
	private LinphonePreferences mPrefs;
	
	@Override
	public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
		
	}
	
	public SettingsFragment() {
		super(R.xml.settings);
	}
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		PreferenceScreen screen = getPreferenceScreen();
		
		mPrefs = LinphonePreferences.getInstance();
		mPrefs.load();
		
		// Init some settings
		initMediaEncryptionPreference((ListPreference) screen.findPreference(getString(R.string.lpconfig_sip_media_enc_key)));

		// Sets default values and value change listener for each of them
		setListenerForPreference(screen);
	}
	
	private void setListenerForPreference(Preference pref) {
		// FIXME: first display doesn't match the linphonerc values
		
		if (pref.hasKey()) {
			pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Log.w("New value for preference key " + preference.getKey() + ":" + newValue.toString());
					mPrefs.set(preference.getKey(), newValue.toString()); 
					return true;
				}
			});
		} else {
			if (pref instanceof PreferenceCategory) {
				PreferenceCategory cat = (PreferenceCategory) pref;
				int count = cat.getPreferenceCount();
				for (int i = 0; i < count; i++) {
					Preference p = cat.getPreference(i);
					setListenerForPreference(p);
				}
			} else if (pref instanceof PreferenceScreen) {
				PreferenceScreen screen = (PreferenceScreen) pref;
				int count = screen.getPreferenceCount();
				for (int i = 0; i < count; i++) {
					Preference p = screen.getPreference(i);
					setListenerForPreference(p);
				}
			}
		}
	}
	
	private void initMediaEncryptionPreference(ListPreference pref) {
		LinphoneCore lc = null;
		try {
			lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		} catch (Exception e) {}
		
		List<CharSequence> mencEntries=new ArrayList<CharSequence>();
		List<CharSequence> mencEntryValues=new ArrayList<CharSequence>();
		mencEntries.add(getString(R.string.media_encryption_none));
		mencEntryValues.add(getString(R.string.pref_media_encryption_key_none));
		
		if (lc == null || getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			CharSequence[] contents = new CharSequence[mencEntries.size()];
			mencEntries.toArray(contents);
			pref.setEntries(contents);
			contents = new CharSequence[mencEntryValues.size()];
			mencEntryValues.toArray(contents);
			pref.setEntryValues(contents);
			return;
		}
		
		boolean hasZrtp = lc.mediaEncryptionSupported(MediaEncryption.ZRTP);
		boolean hasSrtp = lc.mediaEncryptionSupported(MediaEncryption.SRTP);
		if (!hasSrtp && !hasZrtp) {
			pref.setEnabled(false);
		} else {
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
			pref.setEntries(contents);
			contents=new CharSequence[mencEntryValues.size()];
			mencEntryValues.toArray(contents);
			pref.setEntryValues(contents);
		}
	}
	
	@Override
	public void onDestroy() {
		LinphonePreferences.getInstance().save();
		super.onDestroy();
	}
}
