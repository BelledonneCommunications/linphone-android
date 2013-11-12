package org.linphone;

/*
LinphonePreferences.java
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

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.linphone.mediastream.Log;

import android.content.Context;

/**
 * @author Sylvain Berfini
 */
public class LinphonePreferences {
	private static final int LINPHONE_CORE_RANDOM_PORT = -1;
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
			mContext = LinphoneManager.getInstance().getContext();
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
	
	public void removePreviousVersionAuthInfoRemoval() {
		getConfig().setBool("sip", "store_auth_info", true);
	}
	
	// App settings
	public boolean isFirstLaunch() {
		return getConfig().getBool("app", "first_launch", true);
	}
	
	public void firstLaunchSuccessful() { 
		getConfig().setBool("app", "first_launch", false);
	}

	public String getRingtone(String defaultRingtone) {
		String ringtone = getConfig().getString("app", "ringtone", defaultRingtone);
		if (ringtone == null || ringtone.length() == 0)
			ringtone = defaultRingtone;
		return ringtone;
	}

	public boolean shouldAutomaticallyAcceptFriendsRequests() {
		return false; //TODO
	}
	// End of app settings
	
	// Accounts settings
	private LinphoneProxyConfig getProxyConfig(int n) {
		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		if (n < 0 || n >= prxCfgs.length)
			return null;
		return prxCfgs[n];
	}
	
	private LinphoneAuthInfo getAuthInfo(int n) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		try {
			LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxCfg.getIdentity());
			LinphoneAuthInfo authInfo = getLc().findAuthInfo(addr.getUserName(), null, addr.getDomain());
			return authInfo;
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	/**
	 * Removes a authInfo from the core and returns a copy of it.
	 * Useful to edit a authInfo (you should call saveAuthInfo after the modifications to save them).
	 */
	private LinphoneAuthInfo getClonedAuthInfo(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		if (authInfo == null)
			return null;
		
		LinphoneAuthInfo cloneAuthInfo = authInfo.clone();
		getLc().removeAuthInfo(authInfo);
		return cloneAuthInfo;
	}
	
	/**
	 * Saves a authInfo into the core.
	 * Useful to save the changes made to a cloned authInfo.
	 */
	private void saveAuthInfo(LinphoneAuthInfo authInfo) {
		getLc().addAuthInfo(authInfo);
	}
	
	private String tempUsername;
	private String tempUserId;
	private String tempPassword;
	private String tempDomain;
	private String tempProxy;
	private boolean tempOutboundProxy;
	private String tempContactsParams;
	private String tempExpire;
	
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
		if (tempExpire != null) {
			try {
				prxCfg.setExpires(Integer.parseInt(tempExpire));
			} catch (NumberFormatException nfe) { }
		}
		
		LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, null, null, tempDomain);
		
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
		tempExpire = null;
	}
	
	public void setNewAccountUsername(String username) {
		tempUsername = username;
	}
	
	public void setAccountUsername(int n, String username) {
		String identity = "sip:" + username + "@" + getAccountDomain(n);
		LinphoneAuthInfo info = getClonedAuthInfo(n); // Get the auth info before editing the proxy config to ensure to get the correct auth info
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setIdentity(identity);
			prxCfg.done();
			
			info.setUsername(username);
			saveAuthInfo(info);
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountUsername(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUsername();
	}

	public void setNewAccountUserId(String userId) {
		tempUserId = userId;
	}

	public void setAccountUserId(int n, String userId) {
		LinphoneAuthInfo info = getClonedAuthInfo(n);
		info.setUserId(userId);
		saveAuthInfo(info);
	}

	public String getAccountUserId(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUserId();
	}

	public void setNewAccountPassword(String password) {
		tempPassword = password;
	}

	public void setAccountPassword(int n, String password) {
		LinphoneAuthInfo info = getClonedAuthInfo(n);
		info.setPassword(password);
		saveAuthInfo(info);
	}

	public String getAccountPassword(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getPassword();
	}

	public void setNewAccountDomain(String domain) {
		tempDomain = domain;
	}

	public void setAccountDomain(int n, String domain) {
		String identity = "sip:" + getAccountUsername(n) + "@" + domain;
		String proxy = "sip:" + domain;
		
		try {
			LinphoneAuthInfo authInfo = getClonedAuthInfo(n);
			authInfo.setDomain(domain);
			saveAuthInfo(authInfo);
			
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setIdentity(identity);
			prxCfg.setProxy(proxy);
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountDomain(int n) {
		return getProxyConfig(n).getDomain();
	}

	public void setNewAccountProxy(String proxy) {
		tempProxy = proxy;
	}

	public void setAccountProxy(int n, String proxy) {
		if (proxy == null || proxy.length() <= 0) {
			proxy = getAccountDomain(n);
		}
		if (!proxy.startsWith("sip:")) {
			proxy = "sip:" + proxy;
		}
		
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setProxy(proxy);
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountProxy(int n) {
		String proxy = getProxyConfig(n).getProxy();
		if (proxy != null && proxy.startsWith("sip:")) {
			proxy = proxy.substring(4);
		}
		return proxy;
	}

	public void setNewAccountOutboundProxyEnabled(boolean enabled) {
		tempOutboundProxy = enabled;
	}

	public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			if (enabled) {
				String route = prxCfg.getProxy();
				if (!route.startsWith("sip:")) {
					route = "sip:" + route;
				}
				prxCfg.setRoute(route);
			} else {
				prxCfg.setRoute(null);
			}
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public void setNewAccountContactParameters(String contactParams) {
		tempContactsParams = contactParams;
	}

	public boolean isAccountOutboundProxySet(int n) {
		return getProxyConfig(n).getRoute() != null;
	}
	
	public String getExpires(int n) {
		return String.valueOf(getProxyConfig(n).getExpires());
	}
	
	public void setNewAccountExpires(String expire) {
		 tempExpire = expire;
	}
	
	public void setExpires(int n, String expire) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setExpires(Integer.parseInt(expire));
			prxCfg.done();
		} catch (NumberFormatException nfe) { }
	}
	
	public String getPrefix(int n) {
		return getProxyConfig(n).getDialPrefix();
	}
	
	public void setPrefix(int n, String prefix) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.setDialPrefix(prefix);
		prxCfg.done();
	}
	
	public boolean getReplacePlusByZeroZero(int n) {
		return getProxyConfig(n).getDialEscapePlus();
	}
	
	public void setReplacePlusByZeroZero(int n, boolean replace) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.setDialEscapePlus(replace);
		prxCfg.done();
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
		return getLc().getProxyConfigList().length;
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
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		if (authInfo != null)
			getLc().removeAuthInfo(authInfo);
		LinphoneProxyConfig proxyCfg = getProxyConfig(n);
		if (proxyCfg != null)
			getLc().removeProxyConfig(proxyCfg);
		
		if (getLc().getProxyConfigList().length == 0) {
			// TODO: remove once issue http://bugs.linphone.org/view.php?id=984 will be fixed
			LinphoneActivity.instance().getStatusFragment().registrationStateChanged(RegistrationState.RegistrationNone);
		} else {
			getLc().refreshRegisters();
		}
	}
	// End of accounts settings
	
	// Audio settings
	public void setEchoCancellation(boolean enable) {
		getLc().enableEchoCancellation(enable);
	}
	
	public boolean isEchoCancellationEnabled() {
		return getLc().isEchoCancellationEnabled();
	}
	// End of audio settings
	
	// Video settings
	public boolean useFrontCam() {
		return getConfig().getBool("app", "front_camera_default", true);
	}
	
	public void setFrontCamAsDefault(boolean frontcam) {
		getConfig().setBool("app", "front_camera_default", frontcam);
	}
	
	public boolean isVideoEnabled() {
		return getLc().isVideoSupported() && getLc().isVideoEnabled();
	}
	
	public void enableVideo(boolean enable) {
		if (enable) {
			getLc().enableVideo(shouldAutomaticallyShareMyVideo(), true);
		} else {
			getLc().enableVideo(false, false);
		}
	}

	public boolean shouldInitiateVideoCall() {
		return getLc().getVideoAutoInitiatePolicy();
	}
	
	public void setInitiateVideoCall(boolean initiate) {
		getLc().setVideoPolicy(initiate, shouldAutomaticallyAcceptVideoRequests());
	}

	public boolean shouldAutomaticallyAcceptVideoRequests() {
		return getLc().getVideoAutoAcceptPolicy();
	}
	
	public void setAutomaticallyAcceptVideoRequests(boolean accept) {
		getLc().setVideoPolicy(shouldInitiateVideoCall(), accept);
	}
	
	public boolean shouldAutomaticallyShareMyVideo() {
		return getConfig().getBool("video", "capture", true);
	}
	
	public void setAutomaticallyShareMyVideo(boolean accept) {
		getConfig().setBool("video", "capture", accept);
		if (isVideoEnabled())
			enableVideo(true);
	}
	
	public String getPreferredVideoSize() {
		//LinphoneCore can only return video size (width and height), not the name
		return getConfig().getString("video", "size", "qvga");
	}
	
	public void setPreferredVideoSize(String preferredVideoSize) {
		int bandwidth = 512;
		if (preferredVideoSize.equals(getString(R.string.pref_preferred_video_size_hd_key))) {
			preferredVideoSize = "720p";
			bandwidth = 1024 + 128;
		} else if (preferredVideoSize.equals(getString(R.string.pref_preferred_video_size_vga_key))) {
			preferredVideoSize = "vga";
			bandwidth = 512;
		} else if (preferredVideoSize.equals(getString(R.string.pref_preferred_video_size_qvga_key))) {
			preferredVideoSize = "qvga";
			bandwidth = 380;
		}

		getLc().setPreferredVideoSizeByName(preferredVideoSize);
		getLc().setUploadBandwidth(bandwidth);
		getLc().setDownloadBandwidth(bandwidth);
	}
	// End of video settings
	
	// Call settings
	public boolean useRfc2833Dtmfs() {
		return getLc().getUseRfc2833ForDtmfs();
	}
	
	public void sendDtmfsAsRfc2833(boolean use) {
		getLc().setUseRfc2833ForDtmfs(use);
	}
	
	public boolean useSipInfoDtmfs() {
		return getLc().getUseSipInfoForDtmfs();
	}
	
	public void sendDTMFsAsSipInfo(boolean use) {
		getLc().setUseSipInfoForDtmfs(use);
	}
	// End of call settings
	
	// Network settings
	public void setWifiOnlyEnabled(Boolean enable) {
		getConfig().setBool("app", "wifi_only", enable);
	}

	public boolean isWifiOnlyEnabled() {
		return getConfig().getBool("app", "wifi_only", false);
	}
	
	public String getStunServer() {
		return getLc().getStunServer();
	}
	
	public void setStunServer(String stun) {
		getLc().setStunServer(stun);
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
	
	public boolean isIceEnabled() {
		return getLc().getFirewallPolicy() == FirewallPolicy.UseIce;
	}
	
	public void useRandomPort(boolean enabled) {
		useRandomPort(enabled, true);
	}
	
	public void useRandomPort(boolean enabled, boolean apply) {
		getConfig().setBool("app", "random_port", enabled);
		if (apply) {
			if (enabled) {
				setSipPort(LINPHONE_CORE_RANDOM_PORT);
			} else {
				if (getTransport().equals(getString(R.string.pref_transport_tls)))
					setSipPort(5061);
				else
					setSipPort(5060);
			}
		}
	}
	
	public boolean isUsingRandomPort() {
		return getConfig().getBool("app", "random_port", true);
	}
	
	public String getSipPort() {
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
	
	public void setSipPort(int port) {
		Transports transports = getLc().getSignalingTransportPorts();
		if (transports.udp > 0)
			transports.udp = port;
		else if (transports.tcp > 0)
			transports.tcp = port;
		else
			transports.tls = port;
		getLc().setSignalingTransportPorts(transports);
	}

	public String getTransportKey() {
		Transports transports = getLc().getSignalingTransportPorts();
		String transport = getString(R.string.pref_transport_udp_key);
		if (transports.tcp > 0)
			transport = getString(R.string.pref_transport_tcp_key);
		else if (transports.tls > 0)
			transport = getString(R.string.pref_transport_tls_key);
		return transport;
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
	
	public MediaEncryption getMediaEncryption() {
		return getLc().getMediaEncryption();
	}
	
	public void setMediaEncryption(MediaEncryption menc) {
		if (menc == null)
			return;
		
		getLc().setMediaEncryption(menc);
	}
	
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
	
	public void useIpv6(Boolean enable) {
		 getLc().enableIpv6(enable);
	}
	
	public boolean isUsingIpv6() {
		return getLc().isIpv6Enabled();
	}
	// End of network settings
	
	// Advanced settings
	public void setDebugEnabled(boolean enabled) {
		getConfig().setBool("app", "debug", enabled);
		LinphoneCoreFactory.instance().setDebugMode(enabled, getString(R.string.app_name));
	}

	public boolean isDebugEnabled() {
		return getConfig().getBool("app", "debug", false);
	}
	
	public void setBackgroundModeEnabled(boolean enabled) {
		getConfig().setBool("app", "background_mode", enabled);
	}
	
	public boolean isBackgroundModeEnabled() {
		return getConfig().getBool("app", "background_mode", true);
	}

	public void setAnimationsEnabled(boolean enabled) {
		getConfig().setBool("app", "animations", enabled);
	}
	
	public boolean areAnimationsEnabled() {
		return getConfig().getBool("app", "animations", false);
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

	public void setRemoteProvisioningUrl(String url) {
		getConfig().setString("app", "remote_provisioning", url);
	}
	
	public String getRemoteProvisioningUrl() {
		return getConfig().getString("app", "remote_provisioning", null);
	}

	public void setDefaultDisplayName(String displayName) {
		getLc().setPrimaryContact(displayName, getDefaultUsername());
	}
	
	public String getDefaultDisplayName() {
		return getLc().getPrimaryContactDisplayName();
	}
	
	public void setDefaultUsername(String username) {
		getLc().setPrimaryContact(getDefaultDisplayName(), username);
	}
	
	public String getDefaultUsername() {
		return getLc().getPrimaryContactUsername();
	}
	// End of advanced settings
	
	// Tunnel settings
	public String getTunnelMode() {
		return getConfig().getString("app", "tunnel", null);
	}
	
	public void setTunnelMode(String mode) {
		getConfig().setString("app", "tunnel", mode);
		LinphoneManager.getInstance().initTunnelFromConf();
	}
	
	public String getTunnelHost() {
		return getConfig().getString("tunnel", "host", null);
	}
	
	public void setTunnelHost(String host) {
		getConfig().setString("tunnel", "host", host);
		LinphoneManager.getInstance().initTunnelFromConf();
	}
	
	public int getTunnelPort() {
		return getConfig().getInt("tunnel", "port", 443);
	}
	
	public void setTunnelPort(int port) {
		getConfig().setInt("tunnel", "port", port);
		LinphoneManager.getInstance().initTunnelFromConf();
	}
	// End of tunnel settings
}
