package org.linphone;

/*
SettingsFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

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
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.LinphoneLimeState;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.tools.OpenH264DownloadHelper;
import org.linphone.ui.LedPreference;
import org.linphone.ui.PreferencesListFragment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;

public class SettingsFragment extends PreferencesListFragment {
	private LinphonePreferences mPrefs;
	private Handler mHandler = new Handler();
	private LinphoneCoreListenerBase mListener;
	private PreferenceScreen currentPreferenceScreen;
	private Preference.OnPreferenceClickListener prefClickListener = new Preference.OnPreferenceClickListener() {
		@Override
		public boolean onPreferenceClick(Preference preference) {
			currentPreferenceScreen = (PreferenceScreen) preference;
			return false;
		}
	};

	public SettingsFragment() {
		super(R.xml.preferences);
	}

	public void closePreferenceScreen() {
		if (currentPreferenceScreen != null && currentPreferenceScreen.getDialog() != null) {
			currentPreferenceScreen.getDialog().dismiss();
		    currentPreferenceScreen = null;
		}
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		mPrefs = LinphonePreferences.instance();
		removePreviousPreferencesFile(); // Required when updating the preferences order

		mListener = new LinphoneCoreListenerBase() {
			@Override
			public void ecCalibrationStatus(LinphoneCore lc, final EcCalibratorStatus status, final int delayMs, Object data) {
				LinphoneManager.getInstance().routeAudioToReceiver();

				CheckBoxPreference echoCancellation = (CheckBoxPreference) findPreference(getString(R.string.pref_echo_cancellation_key));
				Preference echoCancellerCalibration = findPreference(getString(R.string.pref_echo_canceller_calibration_key));

				if (status == EcCalibratorStatus.DoneNoEcho) {
					echoCancellerCalibration.setSummary(R.string.no_echo);
					echoCancellation.setChecked(false);
					LinphonePreferences.instance().setEchoCancellation(false);
					((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
					Log.i("Set audio mode on 'Normal'");
				} else if (status == EcCalibratorStatus.Done) {
					echoCancellerCalibration.setSummary(String.format(getString(R.string.ec_calibrated), delayMs));
					echoCancellation.setChecked(true);
					LinphonePreferences.instance().setEchoCancellation(true);
					((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
					Log.i("Set audio mode on 'Normal'");
				} else if (status == EcCalibratorStatus.Failed) {
					echoCancellerCalibration.setSummary(R.string.failed);
					echoCancellation.setChecked(true);
					LinphonePreferences.instance().setEchoCancellation(true);
					((AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);
					Log.i("Set audio mode on 'Normal'");
				}
			}
		};

		initSettings();
		setListeners();
		hideSettings();
	}

	private void removePreviousPreferencesFile() {
		SharedPreferences.Editor editor = getPreferenceManager().getSharedPreferences().edit();
        editor.clear();
        editor.apply();

		File dir = new File(getActivity().getFilesDir().getAbsolutePath() + "shared_prefs");
		LinphoneUtils.recursiveFileRemoval(dir);
	}

	// Inits the values or the listener on some settings
	private void initSettings() {
		initTunnelSettings();
		initAudioSettings();
		initVideoSettings();
		initCallSettings();
		initChatSettings();
		initNetworkSettings();
		initAdvancedSettings();

		findPreference(getString(R.string.pref_add_account_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				int nbAccounts = mPrefs.getAccountCount();
				LinphoneActivity.instance().displayAccountSettings(nbAccounts);
				return true;
			}
		});
	}

	// Sets listener for each preference to update the matching value in linphonecore
	private void setListeners() {
		findPreference(getString(R.string.pref_tunnel_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_audio_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_video_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_call_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_chat_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_network_key)).setOnPreferenceClickListener(prefClickListener);
		findPreference(getString(R.string.pref_advanced_key)).setOnPreferenceClickListener(prefClickListener);


		setTunnelPreferencesListener();
		setAudioPreferencesListener();
		setVideoPreferencesListener();
		setCallPreferencesListener();
		setChatPreferencesListener();
		setNetworkPreferencesListener();
		setAdvancedPreferencesListener();
	}

	// Read the values set in resources and hides the settings accordingly
	private void hideSettings() {
		if (getResources().getBoolean(R.bool.hide_accounts)) {
			emptyAndHidePreference(R.string.pref_sipaccounts_key);
		}

		if(!getResources().getBoolean(R.bool.replace_assistant_with_old_interface)){
			hidePreference(R.string.pref_add_account_key);
		}

		if (getResources().getBoolean(R.bool.disable_chat)) {
			findPreference(getString(R.string.pref_image_sharing_server_key)).setLayoutResource(R.layout.hidden);
		}

		if (!getResources().getBoolean(R.bool.enable_push_id)) {
			hidePreference(R.string.pref_push_notification_key);
		}

		if (!Version.isVideoCapable() || !LinphoneManager.getLcIfManagerNotDestroyedOrNull().isVideoSupported()) {
			emptyAndHidePreference(R.string.pref_video_key);
		} else {
			if (!AndroidCameraConfiguration.hasFrontCamera()) {
				uncheckAndHidePreference(R.string.pref_video_use_front_camera_key);
			}
		}

		if (!LinphoneManager.getLc().isTunnelAvailable()) {
			emptyAndHidePreference(R.string.pref_tunnel_key);
		}

		if (getResources().getBoolean(R.bool.hide_camera_settings)) {
			emptyAndHidePreference(R.string.pref_video_key);
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
		if (value != null) {
			EditTextPreference etPref = (EditTextPreference) findPreference(getString(pref));
			if (etPref != null) {
				etPref.setText(value);
				etPref.setSummary(value);
			}
		}
	}

	private void initTunnelSettings() {
		if (!LinphoneManager.isInstanciated() || !LinphoneManager.getLc().isTunnelAvailable()) {
			return;
		}

		setPreferenceDefaultValueAndSummary(R.string.pref_tunnel_host_key, mPrefs.getTunnelHost());
		setPreferenceDefaultValueAndSummary(R.string.pref_tunnel_port_key, String.valueOf(mPrefs.getTunnelPort()));
		ListPreference tunnelModePref = (ListPreference) findPreference(getString(R.string.pref_tunnel_mode_key));
		String tunnelMode = mPrefs.getTunnelMode();
		tunnelModePref.setSummary(tunnelMode);
		tunnelModePref.setValue(tunnelMode);
	}

	private void setTunnelPreferencesListener() {
		findPreference(getString(R.string.pref_tunnel_host_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String host = newValue.toString();
				mPrefs.setTunnelHost(host);
				preference.setSummary(host);
				return true;
			}
		});
		findPreference(getString(R.string.pref_tunnel_port_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				try {
					int port = Integer.parseInt(newValue.toString());
					mPrefs.setTunnelPort(port);
					preference.setSummary(String.valueOf(port));
					return true;
				} catch (NumberFormatException nfe) {
					return false;
				}
			}
		});
		findPreference(getString(R.string.pref_tunnel_mode_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String mode = newValue.toString();
				mPrefs.setTunnelMode(mode);
				preference.setSummary(mode);
				return true;
			}
		});
	}

	private void initAccounts() {
		if (!getResources().getBoolean(R.bool.hide_accounts)) {
			PreferenceCategory accounts = (PreferenceCategory) findPreference(getString(R.string.pref_sipaccounts_key));
			accounts.removeAll();

			// Get already configured extra accounts
			int defaultAccountID = mPrefs.getDefaultAccountIndex();
			int nbAccounts = mPrefs.getAccountCount();
			for (int i = 0; i < nbAccounts; i++) {
				final int accountId = i;
				// For each, add menus to configure it
				String username = mPrefs.getAccountUsername(accountId);
				String domain = mPrefs.getAccountDomain(accountId);
				LedPreference account = new LedPreference(getActivity());

				if (username == null) {
					account.setTitle(getString(R.string.pref_sipaccount));
				} else {
					account.setTitle(username + "@" + domain);
				}

				if (defaultAccountID == i) {
					account.setSummary(R.string.default_account_flag);
				}

				account.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						LinphoneActivity.instance().displayAccountSettings(accountId);
						return false;
					}
				});
				updateAccountLed(account, username, domain, mPrefs.isAccountEnabled(i));
				accounts.addPreference(account);
			}
		}
	}

	private void updateAccountLed(final LedPreference me, final String username, final String domain, boolean enabled) {
		if (!enabled) {
			me.setLed(R.drawable.led_disconnected);
			return;
		}

		if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
			for (LinphoneProxyConfig lpc : LinphoneManager.getLc().getProxyConfigList()) {
				LinphoneAddress addr = lpc.getAddress();
				if (addr.getUserName().equals(username) && addr.getDomain().equals(domain)) {
					if (lpc.getState() == LinphoneCore.RegistrationState.RegistrationOk) {
						me.setLed(R.drawable.led_connected);
					} else if (lpc.getState() == LinphoneCore.RegistrationState.RegistrationFailed) {
						me.setLed(R.drawable.led_error);
					} else if (lpc.getState() == LinphoneCore.RegistrationState.RegistrationProgress) {
						me.setLed(R.drawable.led_inprogress);
						mHandler.postDelayed(new Runnable() {
							@Override
							public void run() {
								updateAccountLed(me, username, domain, true);
							}
						}, 500);
					} else {
						me.setLed(R.drawable.led_disconnected);
					}
					break;
				}
			}
		}
	}

	private void initMediaEncryptionPreference(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		entries.add(getString(R.string.pref_none));
		values.add(getString(R.string.pref_media_encryption_key_none));

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null || getResources().getBoolean(R.bool.disable_all_security_features_for_markets)) {
			setListPreferenceValues(pref, entries, values);
			return;
		}

		boolean hasZrtp = lc.mediaEncryptionSupported(MediaEncryption.ZRTP);
		boolean hasSrtp = lc.mediaEncryptionSupported(MediaEncryption.SRTP);
		boolean hasDtls = lc.mediaEncryptionSupported(MediaEncryption.DTLS);

		if (!hasSrtp && !hasZrtp && !hasDtls) {
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
			if (hasDtls){
				entries.add(getString(R.string.media_encryption_dtls));
				values.add(getString(R.string.pref_media_encryption_key_dtls));

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
		else if (value.toString().equals(getString(R.string.media_encryption_dtls)))
			key = getString(R.string.pref_media_encryption_key_dtls);
		pref.setValue(key);
	}

	private void initializePreferredVideoSizePreferences(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		for (String name : LinphoneManager.getLc().getSupportedVideoSizes()) {
			entries.add(name);
			values.add(name);
		}

		setListPreferenceValues(pref, entries, values);

		String value = mPrefs.getPreferredVideoSize();
		pref.setSummary(value);
		pref.setValue(value);
	}

	private void initializePreferredVideoFpsPreferences(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		entries.add(getString(R.string.pref_none));
		values.add("0");
		for (int i = 5; i <= 30; i += 5) {
			String str = Integer.toString(i);
			entries.add(str);
			values.add(str);
		}
		setListPreferenceValues(pref, entries, values);
		String value = Integer.toString(mPrefs.getPreferredVideoFps());
		if (value.equals("0")) {
			value = getString(R.string.pref_none);
		}
		pref.setSummary(value);
		pref.setValue(value);
	}

	private void initLimeEncryptionPreference(ListPreference pref) {
		List<CharSequence> entries = new ArrayList<CharSequence>();
		List<CharSequence> values = new ArrayList<CharSequence>();
		entries.add(getString(R.string.lime_encryption_entry_disabled));
		values.add(LinphoneLimeState.Disabled.toString());

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null || !lc.isLimeEncryptionAvailable()) {
			setListPreferenceValues(pref, entries, values);
			pref.setEnabled(false);
			return;
		}

		entries.add(getString(R.string.lime_encryption_entry_mandatory));
		values.add(LinphoneLimeState.Mandatory.toString());
		entries.add(getString(R.string.lime_encryption_entry_preferred));
		values.add(LinphoneLimeState.Preferred.toString());
		setListPreferenceValues(pref, entries, values);

		LinphoneLimeState lime = mPrefs.getLimeEncryption();
		if (lime == LinphoneLimeState.Disabled) {
			pref.setSummary(getString(R.string.lime_encryption_entry_disabled));
		} else if (lime == LinphoneLimeState.Mandatory) {
			pref.setSummary(getString(R.string.lime_encryption_entry_mandatory));
		} else if (lime == LinphoneLimeState.Preferred) {
			pref.setSummary(getString(R.string.lime_encryption_entry_preferred));
		}
		pref.setValue(lime.toString());
	}

	private static void setListPreferenceValues(ListPreference pref, List<CharSequence> entries, List<CharSequence> values) {
		CharSequence[] contents = new CharSequence[entries.size()];
		entries.toArray(contents);
		pref.setEntries(contents);
		contents = new CharSequence[values.size()];
		values.toArray(contents);
		pref.setEntryValues(contents);
	}

	private void initAudioSettings() {
		PreferenceCategory codecs = (PreferenceCategory) findPreference(getString(R.string.pref_codecs_key));
		codecs.removeAll();

		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		for (final PayloadType pt : lc.getAudioCodecs()) {
			CheckBoxPreference codec = new CheckBoxPreference(getActivity());
			codec.setTitle(pt.getMime());
			/* Special case */
			if (pt.getMime().equals("mpeg4-generic")) {
				if (android.os.Build.VERSION.SDK_INT < 16) {
					/* Make sure AAC is disabled */
					try {
						lc.enablePayloadType(pt, false);
					} catch (LinphoneCoreException e) {
						Log.e(e);
					}
					continue;
				} else {
					codec.setTitle("AAC-ELD");
				}
			}

			codec.setSummary(pt.getRate() + " Hz");
			codec.setChecked(lc.isPayloadTypeEnabled(pt));

			codec.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean enable = (Boolean) newValue;
					try {
						LinphoneManager.getLcIfManagerNotDestroyedOrNull().enablePayloadType(pt, enable);
					} catch (LinphoneCoreException e) {
						Log.e(e);
					}
					return true;
				}
			});

			codecs.addPreference(codec);
		}

		CheckBoxPreference echoCancellation = (CheckBoxPreference) findPreference(getString(R.string.pref_echo_cancellation_key));
		echoCancellation.setChecked(mPrefs.isEchoCancellationEnabled());

		if (mPrefs.isEchoCancellationEnabled()) {
			Preference echoCalibration = findPreference(getString(R.string.pref_echo_canceller_calibration_key));
			echoCalibration.setSummary(String.format(getString(R.string.ec_calibrated), mPrefs.getEchoCalibration()));
		}

		CheckBoxPreference adaptiveRateControl = (CheckBoxPreference) findPreference(getString(R.string.pref_adaptive_rate_control_key));
		adaptiveRateControl.setChecked(mPrefs.isAdaptiveRateControlEnabled());

		ListPreference bitrateLimit = (ListPreference) findPreference(getString(R.string.pref_codec_bitrate_limit_key));
		bitrateLimit.setSummary(String.valueOf(mPrefs.getCodecBitrateLimit()));
		bitrateLimit.setValue(String.valueOf(mPrefs.getCodecBitrateLimit()));
	}

	private void setAudioPreferencesListener() {
		findPreference(getString(R.string.pref_echo_cancellation_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enabled = (Boolean) newValue;
				mPrefs.setEchoCancellation(enabled);
				return true;
			}
		});

		findPreference(getString(R.string.pref_adaptive_rate_control_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enabled = (Boolean) newValue;
				mPrefs.enableAdaptiveRateControl(enabled);
				return true;
			}
		});

		findPreference(getString(R.string.pref_codec_bitrate_limit_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setCodecBitrateLimit(Integer.parseInt(newValue.toString()));
				LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
				int bitrate = Integer.parseInt(newValue.toString());

				for (final PayloadType pt : lc.getAudioCodecs()) {
					if (lc.payloadTypeIsVbr(pt)) {
						lc.setPayloadTypeBitrate(pt, bitrate);
					}
				}

				preference.setSummary(String.valueOf(mPrefs.getCodecBitrateLimit()));
				return true;
			}
		});

		findPreference(getString(R.string.pref_echo_canceller_calibration_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				synchronized (SettingsFragment.this) {
					preference.setSummary(R.string.ec_calibrating);

					int recordAudio = getActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getActivity().getPackageName());
					if (recordAudio == PackageManager.PERMISSION_GRANTED) {
						startEchoCancellerCalibration();
					} else {
						LinphoneActivity.instance().checkAndRequestRecordAudioPermissionForEchoCanceller();
					}
				}
				return true;
			}
		});

		findPreference(getString(R.string.pref_echo_tester_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				synchronized (SettingsFragment.this) {
					int recordAudio = getActivity().getPackageManager().checkPermission(Manifest.permission.RECORD_AUDIO, getActivity().getPackageName());
					if (recordAudio == PackageManager.PERMISSION_GRANTED) {
						if (LinphoneManager.getInstance().getEchoTesterStatus())
							stopEchoTester();
						else
							startEchoTester();
					} else {
						LinphoneActivity.instance().checkAndRequestRecordAudioPermissionsForEchoTester();
					}
				}
				return true;
			}
		});
	}

	public void startEchoTester() {
		Preference preference = findPreference(getString(R.string.pref_echo_tester_key));
		try {
			if (LinphoneManager.getInstance().startEchoTester() > 0) {
				preference.setSummary("Is running");
			}
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public void stopEchoTester() {
		Preference preference = findPreference(getString(R.string.pref_echo_tester_key));
		try {
			if (LinphoneManager.getInstance().stopEchoTester() > 0) {
				preference.setSummary("Is stopped");
			}
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public void startEchoCancellerCalibration() {
		try {
			if (LinphoneManager.getInstance().getEchoTesterStatus())
				stopEchoTester();
			LinphoneManager.getInstance().startEcCalibration(mListener);
		} catch (LinphoneCoreException e) {
			Log.e(e);
		}
	}

	public void echoCalibrationFail() {
		Preference echoCancellerCalibration = findPreference(getString(R.string.pref_echo_canceller_calibration_key));
		echoCancellerCalibration.setSummary(R.string.failed);
	}

	private void initOpenH264AlertDialog(final OpenH264DownloadHelper mCodecDownloader, final CheckBoxPreference codec) {
		mCodecDownloader.setOpenH264HelperListener(LinphoneManager.getInstance().getOpenH264HelperListener());
		mCodecDownloader.setUserData(0, LinphoneManager.getInstance().getContext());
		mCodecDownloader.setUserData(1, codec);

		AlertDialog.Builder builder = new AlertDialog.Builder(LinphoneManager.getInstance().getContext());
		builder.setCancelable(false);
		builder.setMessage("Do you agree to download " + mCodecDownloader.getLicenseMessage()).setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == DialogInterface.BUTTON_POSITIVE)
					mCodecDownloader.downloadCodec();
			}
		});
		builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if (which == DialogInterface.BUTTON_NEGATIVE) {
					// Disable H264
					codec.setChecked(false);
				}
			}
		}).show();
	}

	private void initVideoSettings() {
		initializePreferredVideoSizePreferences((ListPreference) findPreference(getString(R.string.pref_preferred_video_size_key)));
		initializePreferredVideoFpsPreferences((ListPreference) findPreference(getString(R.string.pref_preferred_video_fps_key)));
		EditTextPreference bandwidth = (EditTextPreference) findPreference(getString(R.string.pref_bandwidth_limit_key));
		bandwidth.setText(Integer.toString(mPrefs.getBandwidthLimit()));
		bandwidth.setSummary(bandwidth.getText());
		updateVideoPreferencesAccordingToPreset();

		ListPreference videoPresetPref = (ListPreference) findPreference(getString(R.string.pref_video_preset_key));
		videoPresetPref.setSummary(mPrefs.getVideoPreset());
		videoPresetPref.setValue(mPrefs.getVideoPreset());

		PreferenceCategory codecs = (PreferenceCategory) findPreference(getString(R.string.pref_video_codecs_key));
		codecs.removeAll();

		final LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();

		final OpenH264DownloadHelper mCodecDownloader = LinphoneManager.getInstance().getOpenH264DownloadHelper();
		boolean h264IsHere = false;
		for (final PayloadType pt : lc.getVideoCodecs()) {
			final CheckBoxPreference codec = new CheckBoxPreference(getActivity());
			codec.setTitle(pt.getMime());

			if (!pt.getMime().equals("VP8")) {
				if (getResources().getBoolean(R.bool.disable_all_patented_codecs_for_markets)) {
					continue;
				} else {
					if (!Version.hasFastCpuWithAsmOptim() && pt.getMime().equals("H264")) {
						// Android without neon doesn't support H264
						Log.w("CPU does not have asm optimisations available, disabling H264");
						continue;
					}
				}
			}
			if (lc.downloadOpenH264Enabled()) {
				if (pt.getMime().equals("H264")) {
					h264IsHere = true;
					if (mCodecDownloader.isCodecFound()) {
						codec.setSummary(mCodecDownloader.getLicenseMessage());
						codec.setTitle("OpenH264");
					}
				}
			}
			codec.setChecked(lc.isPayloadTypeEnabled(pt));

			codec.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean enable = (Boolean) newValue;
					try {
						if (lc.downloadOpenH264Enabled()) {
							if (enable && Version.getCpuAbis().contains("armeabi-v7a") && !Version.getCpuAbis().contains("x86")
									&& pt.getMime().equals("H264") && !mCodecDownloader.isCodecFound()) {
								initOpenH264AlertDialog(mCodecDownloader, codec);
							}
						}
						LinphoneManager.getLcIfManagerNotDestroyedOrNull().enablePayloadType(pt, enable);
					} catch (LinphoneCoreException e) {
						Log.e(e);
					}
					return true;
				}
			});

			codecs.addPreference(codec);
		}
		// Adding OpenH264 button on device < 5.1
		if (lc.downloadOpenH264Enabled() && !h264IsHere) {
			final CheckBoxPreference codec = new CheckBoxPreference(getActivity());
			codec.setTitle("OpenH264");
			codec.setSummary(mCodecDownloader.getLicenseMessage());
			//codec.setEnabled(false);
			codec.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean enable = (Boolean) newValue;
					if (lc.downloadOpenH264Enabled()) {
						if (enable && Version.getCpuAbis().contains("armeabi-v7a") && !Version.getCpuAbis().contains("x86")
								&& !mCodecDownloader.isCodecFound()) {
							initOpenH264AlertDialog(mCodecDownloader, codec);
						}
					}
					return true;
				}
			});
			codecs.addPreference(codec);
		}
		((CheckBoxPreference) findPreference(getString(R.string.pref_video_enable_key))).setChecked(mPrefs.isVideoEnabled());
		((CheckBoxPreference) findPreference(getString(R.string.pref_video_use_front_camera_key))).setChecked(mPrefs.useFrontCam());
		((CheckBoxPreference) findPreference(getString(R.string.pref_video_initiate_call_with_video_key))).setChecked(mPrefs.shouldInitiateVideoCall());
		((CheckBoxPreference) findPreference(getString(R.string.pref_video_automatically_accept_video_key))).setChecked(mPrefs.shouldAutomaticallyAcceptVideoRequests());
		((CheckBoxPreference) findPreference(getString(R.string.pref_overlay_key))).setChecked(mPrefs.isOverlayEnabled());
	}

	private void updateVideoPreferencesAccordingToPreset() {
		if (mPrefs.getVideoPreset().equals("custom")) {
			findPreference(getString(R.string.pref_preferred_video_fps_key)).setEnabled(true);
			findPreference(getString(R.string.pref_bandwidth_limit_key)).setEnabled(true);
		} else {
			findPreference(getString(R.string.pref_preferred_video_fps_key)).setEnabled(false);
			findPreference(getString(R.string.pref_bandwidth_limit_key)).setEnabled(false);
		}
		findPreference(getString(R.string.pref_video_preset_key)).setSummary(mPrefs.getVideoPreset());
		int fps = mPrefs.getPreferredVideoFps();
		String fpsStr = Integer.toString(fps);
		if (fpsStr.equals("0")) {
			fpsStr = getString(R.string.pref_none);
		}
		findPreference(getString(R.string.pref_preferred_video_fps_key)).setSummary(fpsStr);
		findPreference(getString(R.string.pref_bandwidth_limit_key)).setSummary(Integer.toString(mPrefs.getBandwidthLimit()));
	}

	private void setVideoPreferencesListener() {
		findPreference(getString(R.string.pref_video_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enable = (Boolean) newValue;
				mPrefs.enableVideo(enable);
				return true;
			}
		});

		findPreference(getString(R.string.pref_video_use_front_camera_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enable = (Boolean) newValue;
				mPrefs.setFrontCamAsDefault(enable);
				return true;
			}
		});

		findPreference(getString(R.string.pref_video_initiate_call_with_video_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enable = (Boolean) newValue;
				mPrefs.setInitiateVideoCall(enable);
				return true;
			}
		});

		findPreference(getString(R.string.pref_video_automatically_accept_video_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enable = (Boolean) newValue;
				mPrefs.setAutomaticallyAcceptVideoRequests(enable);
				return true;
			}
		});

		findPreference(getString(R.string.pref_video_preset_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setVideoPreset(newValue.toString());
				preference.setSummary(mPrefs.getVideoPreset());
				updateVideoPreferencesAccordingToPreset();
				return true;
			}
		});
		findPreference(getString(R.string.pref_preferred_video_size_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setPreferredVideoSize(newValue.toString());
				preference.setSummary(mPrefs.getPreferredVideoSize());
				updateVideoPreferencesAccordingToPreset();
				return true;
			}
		});

		findPreference(getString(R.string.pref_preferred_video_fps_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setPreferredVideoFps(Integer.parseInt(newValue.toString()));
				updateVideoPreferencesAccordingToPreset();
				return true;
			}
		});

		findPreference(getString(R.string.pref_bandwidth_limit_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setBandwidthLimit(Integer.parseInt(newValue.toString()));
				preference.setSummary(newValue.toString());
				return true;
			}
		});

		findPreference(getString(R.string.pref_overlay_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean enable = (Boolean) newValue;
				if (enable) {
					if (LinphoneActivity.instance().checkAndRequestOverlayPermission()) {
						mPrefs.enableOverlay(true);
					}
				} else {
					mPrefs.enableOverlay(false);
				}
				return true;
			}
		});
	}

	private void initCallSettings() {
		CheckBoxPreference deviceRingtone = (CheckBoxPreference) findPreference(getString(R.string.pref_device_ringtone_key));
		CheckBoxPreference autoAnswer = (CheckBoxPreference) findPreference(getString(R.string.pref_auto_answer_key));
		CheckBoxPreference rfc2833 = (CheckBoxPreference) findPreference(getString(R.string.pref_rfc2833_dtmf_key));
		CheckBoxPreference sipInfo = (CheckBoxPreference) findPreference(getString(R.string.pref_sipinfo_dtmf_key));
		CheckBoxPreference dialerCall = (CheckBoxPreference) findPreference(getString(R.string.pref_dialer_call_key));
		EditTextPreference incTimeout = (EditTextPreference) findPreference(getString(R.string.pref_incoming_call_timeout_key));
		EditTextPreference autoAnswerTime = (EditTextPreference) findPreference(getString(R.string.pref_auto_answer_time_key));


		rfc2833.setChecked(mPrefs.useRfc2833Dtmfs());
		sipInfo.setChecked(mPrefs.useSipInfoDtmfs());
		deviceRingtone.setChecked(mPrefs.isDeviceRingtoneEnabled());
		autoAnswer.setChecked(mPrefs.isAutoAnswerEnabled());
		incTimeout.setText(String.valueOf(mPrefs.getIncTimeout()));
		incTimeout.setSummary(String.valueOf(mPrefs.getIncTimeout()));
		autoAnswerTime.setText(String.valueOf(mPrefs.getAutoAnswerTime()));
		autoAnswerTime.setSummary(String.valueOf(mPrefs.getAutoAnswerTime()));
		if (mPrefs.isAutoAnswerEnabled()) {
			autoAnswerTime.setEnabled(true);
		} else {
			autoAnswerTime.setEnabled(false);
		}

		setPreferenceDefaultValueAndSummary(R.string.pref_voice_mail_key, mPrefs.getVoiceMailUri());
		dialerCall.setChecked(mPrefs.getNativeDialerCall());
	}

	public void enableDeviceRingtone(boolean enabled) {
		LinphonePreferences.instance().enableDeviceRingtone(enabled);
		LinphoneManager.getInstance().enableDeviceRingtone(enabled);
		((CheckBoxPreference)findPreference(getString(R.string.pref_device_ringtone_key))).setChecked(enabled);
	}

	private void setCallPreferencesListener() {
		findPreference(getString(R.string.pref_device_ringtone_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean use = (Boolean) newValue;
				if (use) {
					int readExternalStorage = getActivity().getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, getActivity().getPackageName());
					if (readExternalStorage == PackageManager.PERMISSION_GRANTED) {
						mPrefs.enableDeviceRingtone(true);
						LinphoneManager.getInstance().enableDeviceRingtone(true);
					} else {
						LinphoneActivity.instance().checkAndRequestReadExternalStoragePermissionForDeviceRingtone();
					}
				} else {
					mPrefs.enableDeviceRingtone(false);
					LinphoneManager.getInstance().enableDeviceRingtone(false);
				}

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
				else if (value.equals(getString(R.string.pref_media_encryption_key_dtls)))
					menc = MediaEncryption.DTLS;
				mPrefs.setMediaEncryption(menc);

				preference.setSummary(mPrefs.getMediaEncryption().toString());
				return true;
			}
		});

		initMediaEncryptionPreference((ListPreference) findPreference(getString(R.string.pref_media_encryption_key)));

		findPreference(getString(R.string.pref_auto_answer_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean use = (Boolean) newValue;
				mPrefs.enableAutoAnswer(use);
				if (use) {
					findPreference(getString(R.string.pref_auto_answer_time_key)).setEnabled(true);
				} else {
					findPreference(getString(R.string.pref_auto_answer_time_key)).setEnabled(false);
				}
				return true;
			}
		});

		findPreference(getString(R.string.pref_rfc2833_dtmf_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean use = (Boolean) newValue;
				mPrefs.sendDtmfsAsRfc2833(use);
				return true;
			}
		});

		findPreference(getString(R.string.pref_voice_mail_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				EditTextPreference voiceMail = (EditTextPreference) findPreference(getString(R.string.pref_voice_mail_key));
				voiceMail.setSummary(newValue.toString());
				voiceMail.setText(newValue.toString());
				mPrefs.setVoiceMailUri(newValue.toString());
				return true;
			}
		});

		findPreference(getString(R.string.pref_sipinfo_dtmf_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean use = (Boolean) newValue;
				mPrefs.sendDTMFsAsSipInfo(use);
				return true;
			}
		});

		findPreference(getString(R.string.pref_dialer_call_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean use = (Boolean) newValue;
				mPrefs.setNativeDialerCall(use);
				return true;
			}
		});

		findPreference(getString(R.string.pref_incoming_call_timeout_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String)newValue;
				mPrefs.setIncTimeout(Integer.valueOf(value));
				preference.setSummary(value);
				return true;
			}
		});

		findPreference(getString(R.string.pref_auto_answer_time_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setAutoAnswerTime(Integer.valueOf(value));
				preference.setSummary(value);
				return true;
			}
		});
	}

	private void setEncryptionZrtp() {
		LinphoneUtils.displayErrorAlert(getString(R.string.lime_encryption_enable_zrtp), LinphoneActivity.instance());
		mPrefs.setMediaEncryption(MediaEncryption.ZRTP);
		findPreference(getString(R.string.pref_media_encryption_key)).setSummary(mPrefs.getMediaEncryption().toString());
	}

	private void initChatSettings() {
		setPreferenceDefaultValueAndSummary(R.string.pref_image_sharing_server_key, mPrefs.getSharingPictureServerUrl());
		initLimeEncryptionPreference((ListPreference) findPreference(getString(R.string.pref_use_lime_encryption_key)));
	}

	private void setChatPreferencesListener() {
		findPreference(getString(R.string.pref_image_sharing_server_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setSharingPictureServerUrl(value);
				preference.setSummary(value);
				return true;
			}
		});

		findPreference(getString(R.string.pref_use_lime_encryption_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = newValue.toString();
				LinphoneLimeState lime = LinphoneLimeState.Disabled;
				if (value.equals(LinphoneLimeState.Mandatory.toString()))
					lime = LinphoneLimeState.Mandatory;
				else if (value.equals(LinphoneLimeState.Preferred.toString()))
					lime = LinphoneLimeState.Preferred;
				mPrefs.setLimeEncryption(lime);

				lime = mPrefs.getLimeEncryption();
				if (lime == LinphoneLimeState.Disabled) {
					preference.setSummary(getString(R.string.lime_encryption_entry_disabled));
				} else if (lime == LinphoneLimeState.Mandatory) {
					setEncryptionZrtp();
					preference.setSummary(getString(R.string.lime_encryption_entry_mandatory));
				} else if (lime == LinphoneLimeState.Preferred) {
					setEncryptionZrtp();
					preference.setSummary(getString(R.string.lime_encryption_entry_preferred));
				}

				return true;
			}
		});
	}

	private void initNetworkSettings() {
		((CheckBoxPreference) findPreference(getString(R.string.pref_wifi_only_key))).setChecked(mPrefs.isWifiOnlyEnabled());

		// Disable UPnP if ICE si enabled, or disable ICE if UPnP is enabled
		CheckBoxPreference ice = (CheckBoxPreference) findPreference(getString(R.string.pref_ice_enable_key));
		CheckBoxPreference turn = (CheckBoxPreference) findPreference(getString(R.string.pref_turn_enable_key));
		ice.setChecked(mPrefs.isIceEnabled());
		turn.setEnabled(mPrefs.getStunServer() != null);
		turn.setChecked(mPrefs.isTurnEnabled());

		EditTextPreference turnUsername = (EditTextPreference) findPreference(getString(R.string.pref_turn_username_key));
		EditTextPreference turnPassword = (EditTextPreference) findPreference(getString(R.string.pref_turn_passwd_key));
		turnUsername.setEnabled(mPrefs.isTurnEnabled());
		turnUsername.setSummary(mPrefs.getTurnUsername());
		turnUsername.setText(mPrefs.getTurnUsername());
		turnPassword.setEnabled(mPrefs.isTurnEnabled());

		CheckBoxPreference randomPort = (CheckBoxPreference) findPreference(getString(R.string.pref_transport_use_random_ports_key));
		randomPort.setChecked(mPrefs.isUsingRandomPort());

		// Disable sip port choice if port is random
		EditTextPreference sipPort = (EditTextPreference) findPreference(getString(R.string.pref_sip_port_key));
		sipPort.setEnabled(!randomPort.isChecked());
		sipPort.setSummary(mPrefs.getSipPort());
		sipPort.setText(mPrefs.getSipPort());

		EditTextPreference stun = (EditTextPreference) findPreference(getString(R.string.pref_stun_server_key));
		stun.setSummary(mPrefs.getStunServer());
		stun.setText(mPrefs.getStunServer());

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

				CheckBoxPreference turn = (CheckBoxPreference) findPreference(getString(R.string.pref_turn_enable_key));
				turn.setEnabled(mPrefs.getStunServer() != null);
				return true;
			}
		});

		findPreference(getString(R.string.pref_ice_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setIceEnabled((Boolean) newValue);
				return true;
			}
		});

		findPreference(getString(R.string.pref_turn_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setTurnEnabled((Boolean) newValue);
				EditTextPreference turnUsername = (EditTextPreference) findPreference(getString(R.string.pref_turn_username_key));
				EditTextPreference turnPassword = (EditTextPreference) findPreference(getString(R.string.pref_turn_passwd_key));
				turnUsername.setEnabled(mPrefs.isTurnEnabled());
				turnPassword.setEnabled(mPrefs.isTurnEnabled());
				return true;
			}
		});

		findPreference(getString(R.string.pref_turn_username_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setTurnUsername((String) newValue);
				preference.setSummary(mPrefs.getTurnUsername());
				return true;
			}
		});

		findPreference(getString(R.string.pref_turn_passwd_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mPrefs.setTurnPassword((String) newValue);
				return true;
			}
		});

		findPreference(getString(R.string.pref_upnp_enable_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
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
				int port = -1;
				try {
					port = Integer.parseInt(newValue.toString());
				} catch (NumberFormatException nfe) {
				}

				mPrefs.setSipPort(port);
				preference.setSummary(newValue.toString());
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
		((CheckBoxPreference)findPreference(getString(R.string.pref_friendlist_subscribe_key))).setChecked(mPrefs.isFriendlistsubscriptionEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_debug_key))).setChecked(mPrefs.isDebugEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_background_mode_key))).setChecked(mPrefs.isBackgroundModeEnabled());
		((CheckBoxPreference)findPreference(getString(R.string.pref_service_notification_key))).setChecked(mPrefs.getServiceNotificationVisibility());
		((CheckBoxPreference)findPreference(getString(R.string.pref_autostart_key))).setChecked(mPrefs.isAutoStartEnabled());
		setPreferenceDefaultValueAndSummary(R.string.pref_remote_provisioning_key, mPrefs.getRemoteProvisioningUrl());
		setPreferenceDefaultValueAndSummary(R.string.pref_display_name_key, mPrefs.getDefaultDisplayName());
		setPreferenceDefaultValueAndSummary(R.string.pref_user_name_key, mPrefs.getDefaultUsername());
	}

	private void setAdvancedPreferencesListener() {

		findPreference(getString(R.string.pref_friendlist_subscribe_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.enabledFriendlistSubscription(value);
				LinphoneManager.getInstance().subscribeFriendList(value);
				return true;
			}
		});

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

		findPreference(getString(R.string.pref_service_notification_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean value = (Boolean) newValue;
				mPrefs.setServiceNotificationVisibility(value);
				if (value) {
					LinphoneService.instance().showServiceNotification();
				} else {
					LinphoneService.instance().hideServiceNotification();
				}
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

		findPreference(getString(R.string.pref_remote_provisioning_key)).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String value = (String) newValue;
				mPrefs.setRemoteProvisioningUrl(value);
				preference.setSummary(value);
				return true;
			}
		});

		findPreference(getString(R.string.pref_android_app_settings_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				synchronized (SettingsFragment.this) {
					Context context = SettingsFragment.this.getActivity();
					Intent i = new Intent();
				    i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				    i.addCategory(Intent.CATEGORY_DEFAULT);
				    i.setData(Uri.parse("package:" + context.getPackageName()));
				    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				    i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
				    i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
				    context.startActivity(i);
				}
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
				if (value.equals("")) return false;

				mPrefs.setDefaultUsername(value);
				preference.setSummary(value);
				return true;
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();

		// Init the settings page interface
		initAccounts();

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.SETTINGS);
		}
	}

	@Override
	public void onPause() {
		if (LinphoneActivity.isInstanciated() && LinphoneManager.isInstanciated()) {
			if (LinphoneManager.getInstance().getEchoTesterStatus())
				stopEchoTester();
			LinphoneActivity.instance().hideTopBar();
		}
		super.onPause();
	}
}
