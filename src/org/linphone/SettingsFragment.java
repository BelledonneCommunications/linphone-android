package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.ui.PreferencesListFragment;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

public class SettingsFragment extends PreferencesListFragment implements EcCalibrationListener {
	public SettingsFragment() {
		super(R.xml.preferences);
	}
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		// Init the settings page interface
		initSettings();
		hideSettings();
		setListeners();
	}
	
	// Inits the values or the listener on some settings
	private void initSettings() {
		initMediaEncryptionPreference((ListPreference) findPreference(getString(R.string.pref_media_encryption_key)));
		initializeTransportPreferences((ListPreference) findPreference(getString(R.string.pref_transport_key)));
		
		// Add action on About button
		findPreference(getString(R.string.menu_about_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (LinphoneActivity.isInstanciated()) {
					LinphoneActivity.instance().displayAbout();
					return true;
				}
				return false;
			}
		});
		
		// Disable sip port choice if port is random
		findPreference(getString(R.string.pref_sip_port_key)).setEnabled(!((CheckBoxPreference)findPreference(getString(R.string.pref_transport_use_random_ports_key))).isChecked());
	}
	
	// Read the values set in resources and hides the settings accordingly
	private void hideSettings() {
		if (!getResources().getBoolean(R.bool.display_about_in_settings)) {
			findPreference(getString(R.string.menu_about_key)).setLayoutResource(R.layout.hidden);
		}
	}
	
	private void initMediaEncryptionPreference(ListPreference pref) {
		LinphoneCore lc = null;
		try {
			lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		} catch (Exception e) {}
		
		List<CharSequence> mencEntries = new ArrayList<CharSequence>();
		List<CharSequence> mencEntryValues = new ArrayList<CharSequence>();
		mencEntries.add(getString(R.string.media_encryption_none));
		mencEntryValues.add(getString(R.string.pref_media_encryption_key_none));
		
		if (lc == null || getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			setListPreferenceValues(pref, mencEntries, mencEntryValues);
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
			setListPreferenceValues(pref, mencEntries, mencEntryValues);
		}
	}
	
	private void initializeTransportPreferences(ListPreference pref) {
		List<CharSequence> mencEntries = new ArrayList<CharSequence>();
		List<CharSequence> mencEntryValues = new ArrayList<CharSequence>();
		mencEntries.add(getString(R.string.pref_transport_udp));
		mencEntryValues.add(getString(R.string.pref_transport_udp_key));
		mencEntries.add(getString(R.string.pref_transport_tcp));
		mencEntryValues.add(getString(R.string.pref_transport_tcp_key));
		
		if (!getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			mencEntries.add(getString(R.string.pref_transport_tls));
			mencEntryValues.add(getString(R.string.pref_transport_tls_key));
		}
		setListPreferenceValues(pref, mencEntries, mencEntryValues);
	}
	
	private static void setListPreferenceValues(ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
		CharSequence[] contents = new CharSequence[entries.size()];
		entries.toArray(contents);
		pref.setEntries(contents);
		contents = new CharSequence[values.size()];
		values.toArray(contents);
		pref.setEntryValues(contents);
	}
	
	private void setListeners() {
		
	}
	
	@Override
	public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS);
		}
	}
}
