package org.linphone;

/*
PreferencesMigrator.java
Copyright (C) 2013  Belledonne Communications, Grenoble, France

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

import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.preference.PreferenceManager;

/**
 * @author Sylvain Berfini
 */
public class PreferencesMigrator {
	private LinphonePreferences mNewPrefs;
	private SharedPreferences mOldPrefs;
	private Resources mResources;
	
	public PreferencesMigrator(Context context) {
		mNewPrefs = LinphonePreferences.instance();
		mResources = context.getResources();
		mOldPrefs = PreferenceManager.getDefaultSharedPreferences(context);
	}
	
	public boolean isMigrationNeeded() {
		int accountNumber = mOldPrefs.getInt(getString(R.string.pref_extra_accounts), -1);
		boolean migrationNeeded = accountNumber != -1;
		return migrationNeeded;
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
		mNewPrefs.setAnimationsEnabled(getPrefBoolean(R.string.pref_animation_enable_key, false));
		mNewPrefs.setAutoStart(getPrefBoolean(R.string.pref_autostart_key, false));
		mNewPrefs.setSharingPictureServerUrl(getPrefString(R.string.pref_image_sharing_server_key, null));
		mNewPrefs.setRemoteProvisioningUrl(getPrefString(R.string.pref_remote_provisioning_key, null));
		
		doAccountsMigration();
		deleteAllOldPreferences();
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
			mNewPrefs.setNewAccountUsername(username);
			mNewPrefs.setNewAccountUserId(userid);
			mNewPrefs.setNewAccountDomain(domain);
			mNewPrefs.setNewAccountPassword(password);
			
			String proxy = getPrefString(getString(R.string.pref_proxy_key) + key, null);
			mNewPrefs.setNewAccountProxy(proxy);
			String expire = getPrefString(R.string.pref_expire_key, null);
			mNewPrefs.setNewAccountExpires(expire);
			
			if (getPrefBoolean(getString(R.string.pref_enable_outbound_proxy_key) + key, false)) {
				mNewPrefs.setNewAccountOutboundProxyEnabled(true);
			}
			if (mResources.getBoolean(R.bool.enable_push_id)) {
				String regId = mNewPrefs.getPushNotificationRegistrationID();
				String appId = getString(R.string.push_sender_id);
				if (regId != null && mNewPrefs.isPushNotificationEnabled()) {
					String contactInfos = "app-id=" + appId + ";pn-type=google;pn-tok=" + regId;
					mNewPrefs.setNewAccountContactParameters(contactInfos);
				}
			}
			
			try {
				mNewPrefs.saveNewAccount();
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
			
			if (isDefaultAccount) {
				mNewPrefs.setDefaultAccount(index);
			}
		}
	}
	
	private void deleteAllOldPreferences() {
		Editor editor = mOldPrefs.edit();
		editor.clear();
		editor.commit();
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
