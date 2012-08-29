package org.linphone;

import static org.linphone.R.string.pref_media_encryption_key;

import java.util.ArrayList;
import java.util.List;

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.ui.PreferencesListFragment;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

public class PreferencesFragment extends PreferencesListFragment {
	private ListPreference mencPref;
	
	public PreferencesFragment() {
		super(R.xml.preferences);
	}
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		mencPref = (ListPreference) findPreference(pref_media_encryption_key);
		initializeMediaEncryptionPreferences();
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

	private Preference findPreference(int key) {
		return getPreferenceManager().findPreference(getString(key));
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS);
		}
	}
}
