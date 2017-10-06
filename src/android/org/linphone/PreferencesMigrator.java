package org.linphone;

/*
PreferencesMigrator.java
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

import org.linphone.LinphonePreferences.AccountBuilder;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.linphone.mediastream.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.preference.PreferenceManager;

public class PreferencesMigrator {
	private LinphonePreferences mNewPrefs;
	private SharedPreferences mOldPrefs;
	private Resources mResources;

	public PreferencesMigrator(Context context) {
		mNewPrefs = LinphonePreferences.instance();
		mResources = context.getResources();
		mOldPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	public boolean isEchoMigratioNeeded() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc == null) {
			return false;
		}

		if (mNewPrefs.isEchoConfigurationUpdated()) {
			return false;
		}

		return (!lc.needsEchoCalibration() && mNewPrefs.isEchoCancellationEnabled());
	}

	public boolean isMigrationNeeded() {
		int accountNumber = mOldPrefs.getInt(getString(R.string.pref_extra_accounts), -1);
		return accountNumber != -1;
	}

	public void doMigration() {
		mNewPrefs.firstLaunchSuccessful(); // If migration is needed, it is safe to assume Linphone has already been started once.
		mNewPrefs.removePreviousVersionAuthInfoRemoval(); // Remove flag in linphonerc asking core not to store auths infos

		mNewPrefs.setFrontCamAsDefault(getPrefBoolean(R.string.pref_video_use_front_camera_key, true));
		mNewPrefs.setWifiOnlyEnabled(getPrefBoolean(R.string.pref_wifi_only_key, false));
		mNewPrefs.useRandomPort(getPrefBoolean(R.string.pref_transport_use_random_ports_key, true), false);
		mNewPrefs.setPushNotificationEnabled(getPrefBoolean(R.string.pref_push_notification_key, false));
		mNewPrefs.setPushNotificationRegistrationID(getPrefString(R.string.push_reg_id_key, null));
		mNewPrefs.setDebugEnabled(getPrefBoolean(R.string.pref_debug_key, false));
		mNewPrefs.setBackgroundModeEnabled(getPrefBoolean(R.string.pref_background_mode_key, true));
		mNewPrefs.setAutoStart(getPrefBoolean(R.string.pref_autostart_key, false));
		mNewPrefs.setSharingPictureServerUrl(getPrefString(R.string.pref_image_sharing_server_key, null));
		mNewPrefs.setRemoteProvisioningUrl(getPrefString(R.string.pref_remote_provisioning_key, null));

		doAccountsMigration();
		deleteAllOldPreferences();
	}

	public void migrateRemoteProvisioningUriIfNeeded() {
		String oldUri = mNewPrefs.getConfig().getString("app", "remote_provisioning", null);
		String currentUri = mNewPrefs.getRemoteProvisioningUrl();
		if (oldUri != null && oldUri.length() > 0 && currentUri == null) {
			mNewPrefs.setRemoteProvisioningUrl(oldUri);
			mNewPrefs.getConfig().setString("app", "remote_provisioning", null);
			mNewPrefs.getConfig().sync();
		}
	}

	public void migrateSharingServerUrlIfNeeded() {
		String currentUrl = mNewPrefs.getConfig().getString("app", "sharing_server", null);
		if (currentUrl == null || currentUrl.equals("https://www.linphone.org:444/upload.php")) {
			mNewPrefs.setSharingPictureServerUrl("https://www.linphone.org:444/lft.php");
			mNewPrefs.getConfig().sync();
		}
	}

	private void doAccountsMigration() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		lc.clearAuthInfos();
		lc.clearProxyConfigs();

		for (int i = 0; i < mOldPrefs.getInt(getString(R.string.pref_extra_accounts), 1); i++) {
			doAccountMigration(i, i == getPrefInt(R.string.pref_default_account_key, 0));
		}
	}

	private void doAccountMigration(int index, boolean isDefaultAccount) {
		String key = index == 0 ? "" : String.valueOf(index);

		String username = getPrefString(getString(R.string.pref_username_key) + key, null);
		String userid = getPrefString(getString(R.string.pref_auth_userid_key) + key, null);
		String password = getPrefString(getString(R.string.pref_passwd_key) + key, null);
		String domain = getPrefString(getString(R.string.pref_domain_key) + key, null);
		if (username != null && username.length() > 0 && password != null) {
			String proxy = getPrefString(getString(R.string.pref_proxy_key) + key, null);
			String expire = getPrefString(R.string.pref_expire_key, null);

			AccountBuilder builder = new AccountBuilder(LinphoneManager.getLc())
			.setUsername(username)
			.setUserId(userid)
			.setDomain(domain)
			.setPassword(password)
			.setProxy(proxy)
			.setExpires(expire);

			if (getPrefBoolean(getString(R.string.pref_enable_outbound_proxy_key) + key, false)) {
				builder.setOutboundProxyEnabled(true);
			}
			if (mResources.getBoolean(R.bool.enable_push_id)) {
				String regId = mNewPrefs.getPushNotificationRegistrationID();
				String appId = getString(R.string.push_sender_id);
				if (regId != null && mNewPrefs.isPushNotificationEnabled()) {
					String contactInfos = "app-id=" + appId + ";pn-type=" + getString(R.string.push_type) + ";pn-tok=" + regId;
					builder.setContactParameters(contactInfos);
				}
			}

			try {
				builder.saveNewAccount();
			} catch (LinphoneCoreException e) {
				Log.e(e);
			}

			if (isDefaultAccount) {
				mNewPrefs.setDefaultAccount(index);
			}
		}
	}

	public void doPresenceMigrationIfNeeded() {
		LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		LpConfig cfg = lc.getConfig();
		if (cfg.getString("app", "friendlist_subscription_enabled", null) == null){
			LinphoneProxyConfig proxy = lc.getDefaultProxyConfig();
			if (proxy != null) {
				String domain = proxy.getDomain();
				if (domain!=null && domain.equals(getString(R.string.default_domain))) {
					cfg.setBool("app", "friendlist_subscription_enabled", true);
				}
			}
		}
	}

	private void deleteAllOldPreferences() {
		Editor editor = mOldPrefs.edit();
		editor.clear();
		editor.apply();
	}

	private String getString(int key) {
		return mResources.getString(key);
	}
	private boolean getPrefBoolean(int key, boolean defaultValue) {
		return mOldPrefs.getBoolean(mResources.getString(key), defaultValue);
	}
	private boolean getPrefBoolean(String key, boolean defaultValue) {
		return mOldPrefs.getBoolean(key, defaultValue);
	}
	private String getPrefString(int key, String defaultValue) {
		return mOldPrefs.getString(mResources.getString(key), defaultValue);
	}
	private int getPrefInt(int key, int defaultValue) {
		return mOldPrefs.getInt(mResources.getString(key), defaultValue);
	}
	private String getPrefString(String key, String defaultValue) {
		return mOldPrefs.getString(key, defaultValue);
	}
}
