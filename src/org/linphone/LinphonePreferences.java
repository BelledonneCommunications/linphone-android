package org.linphone;
/*
ChatListFragment.java
Copyright (C) 2012  Belledonne Communications, Grenoble, France

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

/**
 * @author Sylvain Berfini
 */
public class LinphonePreferences {
	private static LinphonePreferences instance;
	
	public static final synchronized LinphonePreferences instance() {
		if (instance == null) {
			instance = new LinphonePreferences();
		}
		return instance;
	}
	
	private LinphonePreferences() {
		
	}
	
	public boolean isFirstLaunch() {
		return false;
	}
	
	public void firstLaunchSuccessful() {
	}

	public boolean isDebugEnabled() {
		return false;
	}

	public String getRemoteProvisioningUrl() {
		return null;
	}

	public String getTunnelMode() {
		return null;
	}

	public boolean useFrontCam() {
		return false;
	}

	public boolean isVideoEnabled() {
		return false;
	}

	public boolean shouldInitiateVideoCall() {
		return false;
	}

	public boolean shouldAutomaticallyAcceptVideoRequests() {
		return false;
	}

	public void setPushNotificationRegistrationID(String regId) {
		
	}

	public String getPushNotificationRegistrationID() {
		return null;
	}

	public boolean shouldStartAtStartup() {
		return false;
	}

	public String getSharingPictureServerUrl() {
		return null;
	}

	public boolean shouldUseLinphoneToStoreChatHistory() {
		return false;
	}

	public boolean areAnimationsEnabled() {
		return false;
	}

	public boolean shouldAutomaticallyAcceptFriendsRequests() {
		return false;
	}

	public boolean isBackgroundModeEnabled() {
		return false;
	}

	public boolean shouldOnlyRegisterOnWifiNetwork() {
		return false;
	}

	public boolean shouldUseSoftvolume() {
		return false;
	}

	public String getRingtone(String defaultRingtone) {
		return defaultRingtone;
	}
	
	public void setRingtone(String ringtone) {
		
	}

	// Accounts
	public void setAccountUsername(int n, String string) {
		
	}

	public String getAccountUsername(int i) {
		return null;
	}

	public void setAccountUserId(int n, String string) {
		
	}

	public String getAccountUserId(int n) {
		return null;
	}

	public void setAccountPassword(int n, String string) {
		
	}

	public String getAccountPassword(int n) {
		return null;
	}

	public void setAccountDomain(int n, String string) {
		
	}

	public String getAccountDomain(int i) {
		return null;
	}

	public void setAccountProxy(int n, String string) {
		
	}

	public String getAccountProxy(int n) {
		return null;
	}

	public void setAccountOutboundProxyEnabled(int n, Boolean newValue) {
		
	}

	public boolean isAccountOutboundProxySet(int n) {
		return false;
	}

	public void setAccountEnabled(int n, Boolean newValue) {
		
	}
	
	public void setDefaultAccount(int accountIndex) {
		
	}

	public int getDefaultAccountIndex() {
		return 0;
	}

	public void setAccountCount(int i) {
		
	}

	public int getAccountCount() {
		return 0;
	}

	public boolean isAccountEnabled(int n) {
		return false;
	}

	public void deleteAccount(int n) {
		
	}
	// End of Accounts
}
