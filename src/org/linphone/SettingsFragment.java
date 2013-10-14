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
import org.linphone.setup.SetupActivity;
import org.linphone.ui.PreferencesListFragment;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class SettingsFragment extends PreferencesListFragment implements EcCalibrationListener {
	private static final int WIZARD_INTENT = 1;
	private LinphonePreferences mPrefs;
	
	public SettingsFragment() {
		super(R.xml.preferences);
		mPrefs = LinphonePreferences.instance();
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
		//initAccounts(); Init accounts on Resume instead of on Create to update the account list when coming back from wizard
		
		initNetworkSettings();
		initializePreferredVideoSizePreferences((ListPreference) findPreference(getString(R.string.pref_preferred_video_size_key)));
		
		initAdvancedSettings();
		
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
		findPreference(getString(R.string.setup_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(LinphoneService.instance(), SetupActivity.class);
	        	startActivityForResult(intent, WIZARD_INTENT);
	        	return true;
			}
		});
		
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

	// Sets listener for each preference to update the matching value in linphonecore
	private void setListeners() {
		setNetworkPreferencesListener();
		setAdvancedPreferencesListener();
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
	
	private void setPreferenceDefaultValueAndSummary(int pref, String value) {
		findPreference(getString(pref)).setDefaultValue(value);
		findPreference(getString(pref)).setSummary(value);
	}
	
	private void initAccounts() {
		PreferenceCategory accounts = (PreferenceCategory) findPreference(getString(R.string.pref_sipaccounts_key));
		accounts.removeAll();
		
		// Get already configured extra accounts
		int defaultAccountID = mPrefs.getDefaultAccountIndex();
		int nbAccounts = mPrefs.getAccountCount();
		for (int i = 0; i < nbAccounts; i++) {
			final int accountId = i;
			// For each, add menus to configure it
			Preference account = new Preference(LinphoneService.instance());
			String username = mPrefs.getAccountUsername(accountId);
			String domain = mPrefs.getAccountDomain(accountId);
			
			if (username == null) {
				account.setTitle(getString(R.string.pref_sipaccount));
			} else {
				account.setTitle(username + "@" + domain);
			}
			
			if (defaultAccountID == i) {
				account.setSummary(R.string.pref_default_account);
			}
			
			account.setOnPreferenceClickListener(new OnPreferenceClickListener() 
			{
				public boolean onPreferenceClick(Preference preference) {
					LinphoneActivity.instance().displayAccountSettings(accountId);
					return false;
				}
			});
			accounts.addPreference(account);
		}
	}
	
	private void initMediaEncryptionPreference(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		entries.add(getString(R.string.media_encryption_none));
		values.add(getString(R.string.pref_media_encryption_key_none));

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null || getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			setListPreferenceValues(pref, entries, values);
			return;
		}
		
		boolean hasZrtp = lc.mediaEncryptionSupported(MediaEncryption.ZRTP);
		boolean hasSrtp = lc.mediaEncryptionSupported(MediaEncryption.SRTP);
		if (!hasSrtp && !hasZrtp) {
			pref.setEnabled(false);
		} else {
			if (hasSrtp){
				entries.add(getString(R.string.media_encryption_srtp));
				values.add(getString(R.string.pref_media_encryption_key_srtp));
			}
			if (hasZrtp){
				entries.add(getString(R.string.media_encryption_zrtp));
				values.add(getString(R.string.pref_media_encryption_key_zrtp));
			}
			setListPreferenceValues(pref, entries, values);
		}
		
		MediaEncryption value = mPrefs.getMediaEncryption();
		pref.setSummary(value.toString());
		
		String key = getString(R.string.pref_media_encryption_key_none);
		if (value.toString().equals(getString(R.string.media_encryption_srtp)))
			key = getString(R.string.pref_media_encryption_key_srtp);
		else if (value.toString().equals(getString(R.string.media_encryption_zrtp)))
			key = getString(R.string.pref_media_encryption_key_zrtp);
		pref.setDefaultValue(key);
	}
	
	private void initializeTransportPreferences(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		entries.add(getString(R.string.pref_transport_udp));
		values.add(getString(R.string.pref_transport_udp_key));
		entries.add(getString(R.string.pref_transport_tcp));
		values.add(getString(R.string.pref_transport_tcp_key));
		
		if (!getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			entries.add(getString(R.string.pref_transport_tls));
			values.add(getString(R.string.pref_transport_tls_key));
		}
		setListPreferenceValues(pref, entries, values);
		String value = mPrefs.getTransport();
		pref.setSummary(value);
		String key = getString(R.string.pref_transport_udp_key);
		if (value.equals(getString(R.string.pref_transport_tcp)))
			key = getString(R.string.pref_transport_tcp_key);
		else if (value.equals(getString(R.string.pref_transport_tls)))
			key = getString(R.string.pref_transport_tls_key);
		pref.setDefaultValue(key);
	}

	private void initializePreferredVideoSizePreferences(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		if (Version.isHDVideoCapable()) {
			entries.add(getString(R.string.pref_preferred_video_size_hd));
			values.add(getString(R.string.pref_preferred_video_size_hd_key));
		}
		entries.add(getString(R.string.pref_preferred_video_size_vga));
		values.add(getString(R.string.pref_preferred_video_size_vga_key));
		entries.add(getString(R.string.pref_preferred_video_size_qvga));
		values.add(getString(R.string.pref_preferred_video_size_qvga_key));

		setListPreferenceValues(pref, entries, values);
	}
	
	private static void setListPreferenceValues(ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
		CharSequence[] contents = new CharSequence[entries.size()];
		entries.toArray(contents);
		pref.setEntries(contents);
		contents = new CharSequence[values.size()];
		values.toArray(contents);
		pref.setEntryValues(contents);
	}
	
	private void initNetworkSettings() {
		initMediaEncryptionPreference((ListPreference) findPreference(getString(R.string.pref_media_encryption_key)));
		initializeTransportPreferences((ListPreference) findPreference(getString(R.string.pref_transport_key)));
		
		((CheckBoxPreference) findPreference(getString(R.string.pref_wifi_only_key))).setChecked(mPrefs.isWifiOnlyEnabled());
		
		// Disable UPnP if ICE si enabled, or disable ICE if UPnP is enabled
		CheckBoxPreference ice = (CheckBoxPreference) findPreference(getString(R.string.pref_ice_enable_key));
		CheckBoxPreference upnp = (CheckBoxPreference) findPreference(getString(R.string.pref_upnp_enable_key));
		ice.setChecked(mPrefs.isIceEnabled());
		if (mPrefs.isIceEnabled()) {
			upnp.setEnabled(false);
		} else {
			upnp.setChecked(mPrefs.isUpnpEnabled());
			if (mPrefs.isUpnpEnabled()) {
				ice.setEnabled(false);
			}
		}

		CheckBoxPreference randomPort = (CheckBoxPreference) findPreference(getString(R.string.pref_transport_use_random_ports_key));
		randomPort.setChecked(mPrefs.isUsingRandomPort());
		
		// Disable sip port choice if port is random
		Preference sipPort = findPreference(getString(R.string.pref_sip_port_key));
		sipPort.setEnabled(!randomPort.isChecked());
		sipPort.setSummary(mPrefs.getSipPort());
		sipPort.setDefaultValue(mPrefs.getSipPort());
		
		Preference stun = findPreference(getString(R.string.pref_stun_server_key));
		stun.setSummary(mPrefs.getStunServer());
		stun.setDefaultValue(mPrefs.getStunServer());
		
		((CheckBoxPreference) findPreference(getString(R.string.pref_push_notification_key))).setChecked(mPrefs.isPushNotificationEnabled());
		((CheckBoxPreference) findPreference(getString(R.string.pref_ipv6_key))).setChecked(mPrefs.isUsingIpv6());
	}
	
	private void setNetworkPreferencesListener() {
		findPreference(getString(R.string.pref_wifi_only_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setWifiOnlyEnabled((Boolean) newValue);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_stun_server_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setStunServer(newValue.toString());
				preference.setSummary(newValue.toString());
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_ice_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				CheckBoxPreference upnp = (CheckBoxPreference) findPreference(getString(R.string.pref_upnp_enable_key));
				boolean value = (Boolean) newValue;
				upnp.setChecked(false);
				upnp.setEnabled(!value);
				mPrefs.setIceEnabled((Boolean) newValue);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_upnp_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				CheckBoxPreference ice = (CheckBoxPreference) findPreference(getString(R.string.pref_ice_enable_key));
				boolean value = (Boolean) newValue;
				ice.setChecked(false);
				ice.setEnabled(!value);
				mPrefs.setUpnpEnabled(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_transport_use_random_ports_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean randomPort = (Boolean) newValue;
				mPrefs.useRandomPort((Boolean) newValue);
				findPreference(getString(R.string.pref_sip_port_key)).setEnabled(!randomPort);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_sip_port_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setSipPort((Integer) newValue);
				preference.setSummary(newValue.toString());
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_transport_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setTransport(newValue.toString());
				preference.setSummary(mPrefs.getTransport());
				return true;
			}
		});	
		
		findPreference(getString(R.string.pref_media_encryption_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = newValue.toString();
				MediaEncryption menc = MediaEncryption.None;
				if (value.equals(getString(R.string.pref_media_encryption_key_srtp)))
					menc = MediaEncryption.SRTP;
				else if (value.equals(getString(R.string.pref_media_encryption_key_zrtp)))
					menc = MediaEncryption.ZRTP;
				mPrefs.setMediaEncryption(menc);
				
				preference.setSummary(mPrefs.getMediaEncryption().toString());
				return true;
			}
		});	
		
		findPreference(getString(R.string.pref_push_notification_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setPushNotificationEnabled((Boolean) newValue);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_ipv6_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.useIpv6((Boolean) newValue);
				return true;
			}
		});
	}
	
	private void initAdvancedSettings() {
		((CheckBoxPreference)findPreference(getString(R.string.pref_debug_key))).setChecked(mPrefs.isDebugEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_background_mode_key))).setChecked(mPrefs.isBackgroundModeEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_animation_enable_key))).setChecked(mPrefs.areAnimationsEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_autostart_key))).setChecked(mPrefs.isAutoStartEnabled());
		setPreferenceDefaultValueAndSummary(R.string.pref_image_sharing_server_key, mPrefs.getSharingPictureServerUrl());
		setPreferenceDefaultValueAndSummary(R.string.pref_remote_provisioning_key, mPrefs.getRemoteProvisioningUrl());
		setPreferenceDefaultValueAndSummary(R.string.pref_display_name_key, mPrefs.getDefaultDisplayName());
		setPreferenceDefaultValueAndSummary(R.string.pref_user_name_key, mPrefs.getDefaultUsername());
	}
	
	private void setAdvancedPreferencesListener() {
		findPreference(getString(R.string.pref_debug_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.setDebugEnabled(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_background_mode_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.setBackgroundModeEnabled(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_animation_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.setAnimationsEnabled(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_autostart_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.setAutoStart(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_image_sharing_server_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setSharingPictureServerUrl(value);
				preference.setSummary(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_remote_provisioning_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setRemoteProvisioningUrl(value);
				preference.setSummary(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_display_name_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setDefaultDisplayName(value);
				preference.setSummary(value);
				return true;
			}
		});
		
		findPreference(getString(R.string.pref_user_name_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setDefaultUsername(value);
				preference.setSummary(value);
				return true;
			}
		});
	}
	
	@Override
	public void onEcCalibrationStatus(EcCalibratorStatus status, int delayMs) {
		
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		initAccounts();
		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS);
		}
	}
}
