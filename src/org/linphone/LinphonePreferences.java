package org.linphone;

import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.linphone.mediastream.Log;

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
	
	private LpConfig getConfig() {
		LinphoneCore lc = getLc();
		if (lc != null)
			return lc.getConfig();
		
		return LinphoneCoreFactory.instance().createLpConfig(LinphoneManager.getInstance().mLinphoneConfigFile);
	}
	
	public boolean isFirstLaunch() {
		return getConfig().getBool("app", "first_launch", true);
	}
	
	public void firstLaunchSuccessful() { 
		getConfig().setBool("app", "first_launch", false);
	}
	
	public void setDebugEnabled(boolean enabled) {
		getConfig().setBool("app", "debug", enabled);
	}

	public boolean isDebugEnabled() {
		return getConfig().getBool("app", "debug", false);
	}

	public void setRemoteProvisioningUrl(String url) {
		getConfig().setString("app", "remote_provisioning", url);
	}
	
	public String getRemoteProvisioningUrl() {
		return getConfig().getString("app", "remote_provisioning", null);
	}

	public String getTunnelMode() {
		return null; //TODO
	}

	public boolean useFrontCam() {
		return false; //TODO
	}

	public boolean isVideoEnabled() {
		return false; //TODO
	}

	public boolean shouldInitiateVideoCall() {
		return false; //TODO
	}

	public boolean shouldAutomaticallyAcceptVideoRequests() {
		return false; //TODO
	}

	public boolean isAutoStartEnabled() {
		return getConfig().getBool("app", "auto_start", false);
	}
	
	public void setAutoStart(boolean autoStartEnabled) {
		getConfig().setBool("app", "auto_start", autoStartEnabled);
	}

	public String getSharingPictureServerUrl() {
		return getConfig().getString("app", "sharing_server", null);
	}
	
	public void setSharingPictureServerUrl(String url) {
		getConfig().setString("app", "sharing_server", url);
	}

	public void setAnimationsEnabled(boolean enabled) {
		getConfig().setBool("app", "animations", enabled);
	}
	
	public boolean areAnimationsEnabled() {
		return getConfig().getBool("app", "animations", false);
	}

	public boolean shouldAutomaticallyAcceptFriendsRequests() {
		return false; //TODO
	}

	public void setBackgroundModeEnabled(boolean enabled) {
		getConfig().setBool("app", "background_mode", enabled);
	}
	
	public boolean isBackgroundModeEnabled() {
		return getConfig().getBool("app", "background_mode", true);
	}

	public boolean shouldUseSoftvolume() {
		return false; //TODO
	}

	public String getRingtone(String defaultRingtone) {
		String ringtone = getLc().getRing();
		if (ringtone == null || ringtone.length() == 0)
			ringtone = defaultRingtone;
		return ringtone;
	}
	
	public void setRingtone(String ringtone) {
		 getLc().setRing(ringtone);
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
	private String tempContactsParams;
	
	/**
	 * Creates a new account using values previously set using setNew* functions
	 * @throws LinphoneCoreException 
	 */
	public void saveNewAccount() throws LinphoneCoreException {
		String identity = "sip:" + tempUsername + "@" + tempDomain;
		String proxy = "sip:";
		proxy += tempProxy == null ? tempDomain : tempProxy;
		String route = tempOutboundProxy ? tempProxy : null;
		
		LinphoneProxyConfig prxCfg = LinphoneCoreFactory.instance().createProxyConfig(identity, proxy, route, true);
		if (tempContactsParams != null)
			prxCfg.setContactParameters(tempContactsParams);
		
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
		tempContactsParams = null;
	}
	
	public void setNewAccountUsername(String username) {
		tempUsername = username;
	}
	
	public void setAccountUsername(int n, String username) {
		getAuthInfo(n).setUsername(username);
	}

	public String getAccountUsername(int n) {
		return getAuthInfo(n).getUsername();
	}

	public void setNewAccountUserId(String userId) {
		tempUserId = userId;
	}

	public void setAccountUserId(int n, String userId) {
		getAuthInfo(n).setUserId(userId);
	}

	public String getAccountUserId(int n) {
		return getAuthInfo(n).getUserId();
	}

	public void setNewAccountPassword(String password) {
		tempPassword = password;
	}

	public void setAccountPassword(int n, String password) {
		getAuthInfo(n).setPassword(password);
	}

	public String getAccountPassword(int n) {
		return getAuthInfo(n).getPassword();
	}

	public void setNewAccountDomain(String domain) {
		tempDomain = domain;
	}

	public void setAccountDomain(int n, String domain) {
		 //TODO
	}

	public String getAccountDomain(int n) {
		return getProxyConfig(n).getDomain();
	}

	public void setNewAccountProxy(String proxy) {
		tempProxy = proxy;
	}

	public void setAccountProxy(int n, String proxy) {
		 //TODO
	}

	public String getAccountProxy(int n) {
		return getProxyConfig(n).getProxy();
	}

	public void setNewAccountOutboundProxyEnabled(boolean enabled) {
		tempOutboundProxy = enabled;
	}

	public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
		 //TODO
	}

	public void setNewAccountContactParameters(String contactParams) {
		tempContactsParams = contactParams;
	}

	public boolean isAccountOutboundProxySet(int n) {
		return getProxyConfig(n).getRoute() != null;
	}
	
	public void setDefaultAccount(int accountIndex) {
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		if (accountIndex >= 0 && accountIndex < prxCfgs.length)
			getLc().setDefaultProxyConfig(prxCfgs[accountIndex]);
	}

	public int getDefaultAccountIndex() {
		LinphoneProxyConfig defaultPrxCfg = getLc().getDefaultProxyConfig();
		if (defaultPrxCfg == null)
			return 0;
		
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		for (int i = 0; i < prxCfgs.length; i++) {
			if (defaultPrxCfg.getIdentity().equals(prxCfgs[i].getIdentity())) {
				return i;
			}
		}
		return 0;
	}

	public int getAccountCount() {
		return Math.min(getLc().getProxyConfigList().length, getLc().getAuthInfosList().length);
	}

	public void setAccountEnabled(int n, boolean disabled) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		try {
			prxCfg.enableRegister(!disabled);
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isAccountEnabled(int n) {
		return getProxyConfig(n).registerEnabled();
	}

	public void deleteAccount(int n) {
		LinphoneProxyConfig proxyCfg = getProxyConfig(n);
		if (proxyCfg != null)
			getLc().removeProxyConfig(proxyCfg);
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		if (authInfo != null)
			getLc().removeAuthInfo(authInfo);
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
		return null; //TODO
	}
	
	public void setExpire(String expire) {
		 //TODO
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

	public void setIceEnabled(boolean enabled) {
		if (enabled) {
			getLc().setFirewallPolicy(FirewallPolicy.UseIce);
		} else {
			String stun = getStunServer();
			if (stun != null && stun.length() > 0) {
				getLc().setFirewallPolicy(FirewallPolicy.UseStun);
			} else {
				getLc().setFirewallPolicy(FirewallPolicy.NoFirewall);
			}
		 }
	}
	
	public boolean isIceEnabled() {
		return getLc().getFirewallPolicy() == FirewallPolicy.UseIce;
	}

	public void setUpnpEnabled(boolean enabled) {
		if (enabled) {
			if (isIceEnabled()) {
				Log.e("Cannot have both ice and upnp enabled, disabling upnp");
			} else {
				getLc().setFirewallPolicy(FirewallPolicy.UseUpnp);
			}
		}
		else {
			String stun = getStunServer();
			if (stun != null && stun.length() > 0) {
				getLc().setFirewallPolicy(FirewallPolicy.UseStun);
			} else {
				getLc().setFirewallPolicy(FirewallPolicy.NoFirewall);
			}
		}
	}
	
	public boolean isUpnpEnabled() {
		return getLc().upnpAvailable() && getLc().getFirewallPolicy() == FirewallPolicy.UseUpnp;
	}

	// Push Notifications
	public void setPushNotificationEnabled(boolean enable) {
		 getConfig().setBool("app", "push_notification", enable);
	}
	
	public boolean isPushNotificationEnabled() {
		return getConfig().getBool("app", "push_notification", false);
	}

	public void setPushNotificationRegistrationID(String regId) {
		 getConfig().setString("app", "push_notification_regid", regId);
	}

	public String getPushNotificationRegistrationID() {
		return getConfig().getString("app", "push_notification_regid", null);
	}
	// End of Push Notifications
	
	public void useRandomPort(boolean enabled) {
		//TODO
	}
	
	public boolean isUsingRandomPort() {
		return false; //TODO
	}

	public void useIpv6(Boolean enable) {
		 getLc().enableIpv6(enable);
	}
	
	public boolean isUsingIpv6() {
		return false; //TODO
	}

	public void setWifiOnlyEnabled(Boolean enable) {
		getConfig().setBool("app", "wifi_only", enable);
	}

	public boolean isWifiOnlyEnabled() {
		return getConfig().getBool("app", "wifi_only", false);
	}
}
