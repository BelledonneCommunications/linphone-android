package org.linphone;

import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;

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
	
	private LinphoneCore getLc() {
		return LinphoneManager.getLcIfManagerNotDestroyedOrNull();
	}
	
	public boolean isFirstLaunch() {
		return false;
	}
	
	public void firstLaunchSuccessful() {
	}

	public boolean isDebugEnabled() {
		return true;
	}

	public void setRemoteProvisioningUrl(String url) {
		getLc().getConfig().setString("app", "remote_provisioning", url);
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
		return getLc().getConfig().getBool("app", "auto_start", false);
	}
	
	public void setAutoStart(boolean autoStartEnabled) {
		getLc().getConfig().setBool("app", "auto_start", autoStartEnabled);
	}

	public String getSharingPictureServerUrl() {
		return getLc().getConfig().getString("app", "sharing_server", null);
	}
	
	public void setSharingPictureServerUrl(String url) {
		getLc().getConfig().setString("app", "sharing_server", url);
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
	private LinphoneProxyConfig getProxyConfig(int n) {
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		if (n < 0 || n >= prxCfgs.length)
			return null;
		return prxCfgs[n];
	}
	
	private LinphoneAuthInfo getAuthInfo(int n) {
		LinphoneAuthInfo[] authsInfos = getLc().getAuthInfosList();
		if (n < 0 || n >= authsInfos.length)
			return null;
		return authsInfos[n];
	}
	
	private String tempUsername;
	private String tempUserId;
	private String tempPassword;
	private String tempDomain;
	private String tempProxy;
	private boolean tempOutboundProxy;
	
	/**
	 * Saves a created account or an edited account
	 * @throws LinphoneCoreException 
	 */
	public void saveNewAccount() throws LinphoneCoreException {
		String identity = "sip:" + tempUsername + "@" + tempDomain;
		String proxy = "sip:";
		proxy += tempProxy == null ? tempDomain : tempProxy;
		String route = tempOutboundProxy ? tempProxy : null;
		
		LinphoneProxyConfig prxCfg = LinphoneCoreFactory.instance().createProxyConfig(identity, proxy, route, true);
		LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, null, null);
		
		getLc().addProxyConfig(prxCfg);
		getLc().addAuthInfo(authInfo);
		
		if (getAccountCount() == 1)
			getLc().setDefaultProxyConfig(prxCfg);
		
		tempUsername = null;
		tempUserId = null;
		tempPassword = null;
		tempDomain = null;
		tempProxy = null;
		tempOutboundProxy = false;
	}
	
	public void setNewAccountUsername(String username) {
		tempUsername = username;
	}
	
	public void setAccountUsername(int n, String username) {
		
	}

	public String getAccountUsername(int n) {
		return getAuthInfo(n).getUsername();
	}

	public void setNewAccountUserId(String userId) {
		tempUserId = userId;
	}

	public void setAccountUserId(int n, String userId) {
		
	}

	public String getAccountUserId(int n) {
		return getAuthInfo(n).getUserId();
	}

	public void setNewAccountPassword(String password) {
		tempPassword = password;
	}

	public void setAccountPassword(int n, String password) {
		
	}

	public String getAccountPassword(int n) {
		return getAuthInfo(n).getPassword();
	}

	public void setNewAccountDomain(String domain) {
		tempDomain = domain;
	}

	public void setAccountDomain(int n, String domain) {
		
	}

	public String getAccountDomain(int n) {
		return getProxyConfig(n).getDomain();
	}

	public void setNewAccountProxy(String proxy) {
		tempProxy = proxy;
	}

	public void setAccountProxy(int n, String proxy) {
		
	}

	public String getAccountProxy(int n) {
		return getProxyConfig(n).getProxy();
	}

	public void setNewAccountOutboundProxyEnabled(boolean enabled) {
		tempOutboundProxy = enabled;
	}

	public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
		
	}

	public boolean isAccountOutboundProxySet(int n) {
		return getProxyConfig(n).getRoute() != null;
	}

	public void setAccountEnabled(int n, Boolean enabled) {
		
	}
	
	public void setDefaultAccount(int accountIndex) {
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		if (accountIndex >= 0 && accountIndex < prxCfgs.length)
			getLc().setDefaultProxyConfig(prxCfgs[accountIndex]);
	}

	public int getDefaultAccountIndex() {
		LinphoneProxyConfig defaultPrxCfg = getLc().getDefaultProxyConfig();
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		for (int i = 0; i < prxCfgs.length; i++) {
			if (defaultPrxCfg.equals(prxCfgs[i])) {
				return i;
			}
		}
		return -1;
	}

	public int getAccountCount() {
		return getLc().getProxyConfigList().length;
	}

	public boolean isAccountEnabled(int n) {
		return false;
	}

	public void deleteAccount(int n) {
		
	}
	// End of Accounts
	
	public MediaEncryption getMediaEncryption() {
		return getLc().getMediaEncryption();
	}
	
	public void setMediaEncryption(MediaEncryption menc) {
		if (menc == null)
			return;
		
		getLc().setMediaEncryption(menc);
	}
	
	public String getTransport() {
		Transports transports = getLc().getSignalingTransportPorts();
		String transport = getString(R.string.pref_transport_udp);
		if (transports.tcp > 0)
			transport = getString(R.string.pref_transport_tcp);
		else if (transports.tls > 0)
			transport = getString(R.string.pref_transport_tls);
		return transport;
	}
	
	public void setTransport(String transportKey) {
		if (transportKey == null)
			return;
		
		Transports transports = getLc().getSignalingTransportPorts();
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
		getLc().setSignalingTransportPorts(transports);
	}

	public String getStunServer() {
		return getLc().getStunServer();
	}
	
	public void setStunServer(String stun) {
		getLc().setStunServer(stun);
	}

	public String getExpire() {
		return null;
	}
	
	public void setExpire(String expire) {
		
	}

	public String getSipPortIfNotRandom() {
		Transports transports = getLc().getSignalingTransportPorts();
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
		
		Transports transports = getLc().getSignalingTransportPorts();
		if (transports.udp > 0)
			transports.udp = port;
		else if (transports.tcp > 0)
			transports.tcp = port;
		else
			transports.udp = port;
		getLc().setSignalingTransportPorts(transports);
	}

	public void setIceEnabled(boolean b) {
		
	}

	public void setPushNotificationEnabled(boolean b) {
		
	}
}
