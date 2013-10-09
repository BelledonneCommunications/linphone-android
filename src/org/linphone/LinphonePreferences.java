package org.linphone;

import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreFactory;

import android.content.Context;

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
	private Context mContext;
	
	public static final synchronized LinphonePreferences instance() {
		if (instance == null) {
			instance = new LinphonePreferences();
		}
		return instance;
	}
	
	private LinphonePreferences() {
		
	}
	
	private String getString(int key) {
		if (mContext == null) {
			mContext = LinphoneService.instance();
		}
		
		return mContext.getString(key);
	}
	
	public boolean isFirstLaunch() {
		return false;
	}
	
	public void firstLaunchSuccessful() {
	}

	public boolean isDebugEnabled() {
		return false;
	}

	public void setRemoteProvisioningUrl(String url) {
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().getConfig().setString("app", "remote_provisioning", url);
	}
	
	public String getRemoteProvisioningUrl() {
		return LinphoneCoreFactory.instance().createLpConfig(LinphoneManager.getInstance().mLinphoneConfigFile).getString("app", "remote_provisioning", null);
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

	public boolean isAutoStartEnabled() {
		return LinphoneManager.getLcIfManagerNotDestroyedOrNull().getConfig().getBool("app", "auto_start", false);
	}
	
	public void setAutoStart(boolean autoStartEnabled) {
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().getConfig().setBool("app", "auto_start", autoStartEnabled);
	}

	public String getSharingPictureServerUrl() {
		return LinphoneManager.getLcIfManagerNotDestroyedOrNull().getConfig().getString("app", "sharing_server", null);
	}
	
	public void setSharingPictureServerUrl(String url) {
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().getConfig().setString("app", "sharing_server", url);
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
	
	public MediaEncryption getMediaEncryption() {
		return LinphoneManager.getLcIfManagerNotDestroyedOrNull().getMediaEncryption();
	}
	
	public void setMediaEncryption(MediaEncryption menc) {
		if (menc == null)
			return;
		
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().setMediaEncryption(menc);
	}
	
	public String getTransport() {
		Transports transports = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getSignalingTransportPorts();
		String transport = getString(R.string.pref_transport_udp);
		if (transports.tcp > 0)
			transport = getString(R.string.pref_transport_tcp_key);
		else if (transports.tls > 0)
			transport = getString(R.string.pref_transport_tls_key);
		return transport;
	}
	
	public void setTransport(String transportKey) {
		if (transportKey == null)
			return;
		
		Transports transports = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getSignalingTransportPorts();
		if (transports.udp > 0) {
			if (transportKey.equals(getString(R.string.pref_transport_tcp_key))) {
				transports.tcp = transports.udp;
				transports.udp = transports.tls;
			} else if (transportKey.equals(getString(R.string.pref_transport_tls_key))) {
				transports.tls = transports.udp;
				transports.udp = transports.tcp;
			}
		} else if (transports.tcp > 0) {
			if (transportKey.equals(getString(R.string.pref_transport_udp_key))) {
				transports.udp = transports.tcp;
				transports.tcp = transports.tls;
			} else if (transportKey.equals(getString(R.string.pref_transport_tls_key))) {
				transports.tls = transports.tcp;
				transports.tcp = transports.udp;
			}
		} else if (transports.tls > 0) {
			if (transportKey.equals(getString(R.string.pref_transport_udp_key))) {
				transports.udp = transports.tls;
				transports.tls = transports.tcp;
			} else if (transportKey.equals(getString(R.string.pref_transport_tcp_key))) {
				transports.tcp = transports.tls;
				transports.tls = transports.udp;
			}
		}
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().setSignalingTransportPorts(transports);
	}

	public String getStunServer() {
		return LinphoneManager.getLcIfManagerNotDestroyedOrNull().getStunServer();
	}
	
	public void setStunServer(String stun) {
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().setStunServer(stun);
	}

	public String getExpire() {
		return null;
	}
	
	public void setExpire(String expire) {
		
	}

	public String getSipPortIfNotRandom() {
		Transports transports = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getSignalingTransportPorts();
		int port;
		if (transports.udp > 0)
			port = transports.udp;
		else if (transports.tcp > 0)
			port = transports.tcp;
		else
			port = transports.tls;
		return String.valueOf(port);
	}
	
	public void setSipPortIfNotRandom(int port) {
		if (port <= 0)
			return;
		
		Transports transports = LinphoneManager.getLcIfManagerNotDestroyedOrNull().getSignalingTransportPorts();
		if (transports.udp > 0)
			transports.udp = port;
		else if (transports.tcp > 0)
			transports.tcp = port;
		else
			transports.udp = port;
		LinphoneManager.getLcIfManagerNotDestroyedOrNull().setSignalingTransportPorts(transports);
	}
}
