package org.linphone;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.ui.PreferencesListFragment;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

public class SettingsFragment extends PreferencesListFragment implements EcCalibrationListener {
	@Override
	public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
		
	}
	
	public SettingsFragment() {
		super(R.xml.settings);
	}
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		final LinphonePreferences prefs = LinphonePreferences.getInstance();
		int count = getPreferenceScreen().getPreferenceCount();
		for (int i = 0; i < count; i++) {
			Preference pref = getPreferenceScreen().getPreference(i);
			if (pref.hasKey()) {
				pref.setDefaultValue(prefs.get(pref.getKey()));
				pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference, Object newValue) {
						prefs.set(preference.getKey(), newValue.toString()); 
						return true;
					}
				});
			}
		}
	}
}
