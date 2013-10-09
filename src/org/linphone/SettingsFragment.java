package org.linphone;

import java.util.ArrayList;
import java.util.List;

import org.linphone.LinphoneManager.EcCalibrationListener;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.Hacks;
import org.linphone.ui.PreferencesListFragment;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;

public class SettingsFragment extends PreferencesListFragment implements EcCalibrationListener {
	public SettingsFragment() {
		super(R.xml.preferences);
	}
	
	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		
		// Init the settings page interface
		initSettings();
		setListeners();
		hideSettings();
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

		if (getResources().getBoolean(R.bool.disable_all_patented_codecs_for_markets)) {
			Preference prefH264 = findPreference(getString(R.string.pref_video_codec_h264_key));
			prefH264.setEnabled(false);
			prefH264.setSummary(R.string.pref_video_codec_h264_unavailable);
			
			Preference prefMPEG4 = findPreference(getString(R.string.pref_video_codec_mpeg4_key));
			prefMPEG4.setEnabled(false);
			prefMPEG4.setSummary(R.string.pref_video_codec_mpeg4_unavailable);
		} else {
			if (!Version.hasFastCpuWithAsmOptim())
			{
				// Android without neon doesn't support H264
				Log.w("CPU does not have asm optimisations available, disabling H264");
				findPreference(getString(R.string.pref_video_codec_h264_key)).setEnabled(false);
				findPreference(getString(R.string.pref_video_codec_h264_key)).setDefaultValue(false);
			}
		}
	}
	
	private void setListeners() {
		
	}
	
	// Read the values set in resources and hides the settings accordingly
	private void hideSettings() {
		if (!getResources().getBoolean(R.bool.display_about_in_settings)) {
			hidePreference(R.string.menu_about_key);
		}
		
		if (getResources().getBoolean(R.bool.hide_accounts)) {	
			emptyAndHidePreference(R.string.pref_sipaccounts_key);
		}
		
		if (getResources().getBoolean(R.bool.hide_wizard)) {
			hidePreference(R.string.setup_key);
		}
		
		if (getResources().getBoolean(R.bool.disable_animations)) {
			uncheckAndHidePreference(R.string.pref_animation_enable_key);
		}
		
		if (!getResources().getBoolean(R.bool.enable_linphone_friends)) {
			emptyAndHidePreference(R.string.pref_linphone_friend_key);
		}

		if (getResources().getBoolean(R.bool.disable_chat)) {
			findPreference(getString(R.string.pref_image_sharing_server_key)).setLayoutResource(R.layout.hidden);
		}
		
		if (!getResources().getBoolean(R.bool.enable_push_id)) {
			hidePreference(R.string.pref_push_notification_key);
		}

		if (!Version.isVideoCapable()) {
			uncheckAndHidePreference(R.string.pref_video_enable_key);
		} else {
			if (!AndroidCameraConfiguration.hasFrontCamera()) {
				uncheckAndHidePreference(R.string.pref_video_use_front_camera_key);
			}
		}
		
		if (Hacks.hasBuiltInEchoCanceller()) {
			uncheckAndHidePreference(R.string.pref_echo_cancellation_key);
			hidePreference(R.string.pref_echo_canceller_calibration_key);
		}
		
		if (!LinphoneManager.getLc().isTunnelAvailable()) {
			emptyAndHidePreference(R.string.pref_tunnel_key);
		}
		
		if (getResources().getBoolean(R.bool.hide_camera_settings)) {
			emptyAndHidePreference(R.string.pref_video_key);
			hidePreference(R.string.pref_video_enable_key);
		}
		
		if (getResources().getBoolean(R.bool.disable_every_log)) {
			uncheckAndHidePreference(R.string.pref_debug_key);
		}
		
		if (!LinphoneManager.getLc().upnpAvailable()) {
			uncheckAndHidePreference(R.string.pref_upnp_enable_key);
		}
	}
	
	private void uncheckAndHidePreference(int preferenceKey) {
		Preference preference = findPreference(getString(preferenceKey));
		if (!(preference instanceof CheckBoxPreference))
			return;
		
		CheckBoxPreference checkBoxPreference = (CheckBoxPreference) preference;
		checkBoxPreference.setChecked(false);
		hidePreference(checkBoxPreference);
	}
	
	private void emptyAndHidePreference(int preferenceKey) {
		Preference preference = findPreference(getString(preferenceKey));
		if (preference instanceof PreferenceCategory)
			emptyAndHidePreferenceCategory(preferenceKey);
		else if (preference instanceof PreferenceScreen)
			emptyAndHidePreferenceScreen(preferenceKey);
	}
	
	private void emptyAndHidePreferenceCategory(int preferenceKey) {
		Preference preference = findPreference(getString(preferenceKey));
		if (!(preference instanceof PreferenceCategory))
			return;
		
		PreferenceCategory preferenceCategory = (PreferenceCategory) preference;
		preferenceCategory.removeAll();
		hidePreference(preferenceCategory);
	}
	
	private void emptyAndHidePreferenceScreen(int preferenceKey) {
		Preference preference = findPreference(getString(preferenceKey));
		if (!(preference instanceof PreferenceScreen))
			return;
		
		PreferenceScreen preferenceScreen = (PreferenceScreen) preference;
		preferenceScreen.removeAll();
		hidePreference(preferenceScreen);
	}
	
	private void hidePreference(int preferenceKey) {
		hidePreference(findPreference(getString(preferenceKey)));
	}
	
	private void hidePreference(Preference preference) {
		preference.setLayoutResource(R.layout.hidden);
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
