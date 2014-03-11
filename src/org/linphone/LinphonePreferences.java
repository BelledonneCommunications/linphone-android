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
import org.linphone.core.LinphoneAddress.TransportType;
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
	
	public LpConfig getConfig() {
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
	
	public void setRingtone(String ringtonePath) {
		getConfig().setString("app", "ringtone", ringtonePath);
		
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

	public static class AccountBuilder {
		private LinphoneCore lc;
		public AccountBuilder(LinphoneCore lc) {
			this.lc = lc;
		}
		private String tempUsername;
		private String tempUserId;
		private String tempPassword;
		private String tempDomain;
		private String tempProxy;
		private boolean tempOutboundProxy;
		private String tempContactsParams;
		private String tempExpire;
		private TransportType tempTransport;
		private boolean tempEnabled = true;
		private boolean tempNoDefault = false;

		public AccountBuilder setTransport(TransportType transport) {
			tempTransport = transport;
			return this;
		}
		public AccountBuilder setUsername(String username) {
			tempUsername = username;
			return this;
		}

		public AccountBuilder setPassword(String password) {
			tempPassword = password;
			return this;
		}

		public AccountBuilder setDomain(String domain) {
			tempDomain = domain;
			return this;
		}

		public AccountBuilder setProxy(String proxy) {
			tempProxy = proxy;
			return this;
		}
		public AccountBuilder setOutboundProxyEnabled(boolean enabled) {
			tempOutboundProxy = enabled;
			return this;
		}

		public AccountBuilder setContactParameters(String contactParams) {
			tempContactsParams = contactParams;
			return this;
		}
		
		public AccountBuilder setExpires(String expire) {
			 tempExpire = expire;
			return this;
		}

		public AccountBuilder setUserId(String userId) {
			tempUserId = userId;
			return this;
		}

		public AccountBuilder setEnabled(boolean enable) {
			tempEnabled = enable;
			return this;
		}

		public AccountBuilder setNoDefault(boolean yesno) {
			tempNoDefault = yesno;
			return this;
		}

		/**
		 * Creates a new account
		 * @throws LinphoneCoreException 
		 */
		public void saveNewAccount() throws LinphoneCoreException {
			String identity = "sip:" + tempUsername + "@" + tempDomain;
			String proxy = "sip:";
			if (tempProxy == null) {
				proxy += tempDomain;
			} else {
				if (!tempProxy.startsWith("sip:") && !tempProxy.startsWith("<sip:")
					&& !tempProxy.startsWith("sips:") && !tempProxy.startsWith("<sips:")) {
					proxy += tempProxy;
				} else {
					proxy = tempProxy;
				}
			}
			LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
			
			if (tempTransport != null) {
				proxyAddr.setTransport(tempTransport);
			}
			
			String route = tempOutboundProxy ? proxyAddr.asStringUriOnly() : null;
			
			LinphoneProxyConfig prxCfg = LinphoneCoreFactory.instance().createProxyConfig(identity, proxyAddr.asStringUriOnly(), route, tempEnabled);

			if (tempContactsParams != null)
				prxCfg.setContactUriParameters(tempContactsParams);
			if (tempExpire != null) {
				try {
					prxCfg.setExpires(Integer.parseInt(tempExpire));
				} catch (NumberFormatException nfe) { }
			}
			
			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, null, null, tempDomain);
			
			lc.addProxyConfig(prxCfg);
			lc.addAuthInfo(authInfo);
			
			if (!tempNoDefault && LinphonePreferences.instance().getAccountCount() == 1)
				lc.setDefaultProxyConfig(prxCfg);
		}
	}
	

	

	
	public void setAccountTransport(int n, String transport) {
		LinphoneProxyConfig proxyConfig = getProxyConfig(n);
		
		if (proxyConfig != null && transport != null) {
			LinphoneAddress proxyAddr;
			try {
				proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxyConfig.getProxy());
				
				if (transport.equals(getString(R.string.pref_transport_udp_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportUdp);
				} else if (transport.equals(getString(R.string.pref_transport_tcp_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportTcp);
				} else if (transport.equals(getString(R.string.pref_transport_tls_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportTls);
				}
				
				LinphoneProxyConfig prxCfg = getProxyConfig(n);
				prxCfg.setProxy(proxyAddr.asStringUriOnly());
				prxCfg.done();
				
				if (isAccountOutboundProxySet(n)) {
					setAccountOutboundProxyEnabled(n, true);
				}
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
		}
	}
	
	public TransportType getAccountTransport(int n) {
		TransportType transport = null;
		LinphoneProxyConfig proxyConfig = getProxyConfig(n);
		
		if (proxyConfig != null) {
			LinphoneAddress proxyAddr;
			try {
				proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxyConfig.getProxy());
				transport = proxyAddr.getTransport();
			} catch (LinphoneCoreException e) {
				e.printStackTrace();
			}
		}
		
		return transport;
	}
	
	public String getAccountTransportKey(int n) {
		TransportType transport = getAccountTransport(n);
		String key = getString(R.string.pref_transport_udp_key);
		
		if (transport != null && transport == TransportType.LinphoneTransportTcp)
			key = getString(R.string.pref_transport_tcp_key);
		else if (transport != null && transport == TransportType.LinphoneTransportTls)
			key = getString(R.string.pref_transport_tls_key);
		
		return key;
	}
	
	public String getAccountTransportString(int n) {
		TransportType transport = getAccountTransport(n);
		
		if (transport != null && transport == TransportType.LinphoneTransportTcp)
			return getString(R.string.pref_transport_tcp);
		else if (transport != null && transport == TransportType.LinphoneTransportTls)
			return getString(R.string.pref_transport_tls);
		
		return getString(R.string.pref_transport_udp);
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

	public void setAccountUserId(int n, String userId) {
		LinphoneAuthInfo info = getClonedAuthInfo(n);
		info.setUserId(userId);
		saveAuthInfo(info);
	}

	public String getAccountUserId(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUserId();
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

	public void setAccountDomain(int n, String domain) {
		String identity = "sip:" + getAccountUsername(n) + "@" + domain;
		
		try {
			LinphoneAuthInfo authInfo = getClonedAuthInfo(n);
			authInfo.setDomain(domain);
			saveAuthInfo(authInfo);
			
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setIdentity(identity);
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountDomain(int n) {
		return getProxyConfig(n).getDomain();
	}

	public void setAccountProxy(int n, String proxy) {
		if (proxy == null || proxy.length() <= 0) {
			proxy = getAccountDomain(n);
		}
		
		if (!proxy.contains("sip:")) {
			proxy = "sip:" + proxy;
		}
		
		try {
			LinphoneAddress proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxy);
			if (!proxy.contains("transport=")) {
				proxyAddr.setTransport(getAccountTransport(n));
			}
			
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.setProxy(proxyAddr.asStringUriOnly());
			prxCfg.done();
			
			if (isAccountOutboundProxySet(n)) {
				setAccountOutboundProxyEnabled(n, true);
			}
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountProxy(int n) {
		String proxy = getProxyConfig(n).getProxy();
		return proxy;
	}


	public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			if (enabled) {
				String route = prxCfg.getProxy();
				prxCfg.setRoute(route);
			} else {
				prxCfg.setRoute(null);
			}
			prxCfg.done();
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public boolean isAccountOutboundProxySet(int n) {
		return getProxyConfig(n).getRoute() != null;
	}
	
	public void setAccountContactParameters(int n, String contactParams) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.setContactUriParameters(contactParams);
		prxCfg.done();
	}
	
	public String getExpires(int n) {
		return String.valueOf(getProxyConfig(n).getExpires());
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
		if (getLc() == null || getLc().getProxyConfigList() == null)
			return 0;
		
		return getLc().getProxyConfigList().length;
	}

	public void setAccountEnabled(int n, boolean enabled) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.enableRegister(enabled);
		prxCfg.done();
		
		// If default proxy config is disabled, try to set another one as default proxy
		if (!enabled && getLc().getDefaultProxyConfig().getIdentity().equals(prxCfg.getIdentity())) {
			int count = getLc().getProxyConfigList().length;
			if (count > 1) {
				for (int i = 0; i < count; i++) {
					if (isAccountEnabled(i)) {
						getLc().setDefaultProxyConfig(getProxyConfig(i));
						break;
					}
				}
			}
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
		getLc().enableVideo(enable, enable);
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
	
	public String getPreferredVideoSize() {
		//LinphoneCore can only return video size (width and height), not the name
		return getConfig().getString("video", "size", "qvga");
	}
	
	public void setPreferredVideoSize(String preferredVideoSize) {
		int bandwidth = 512;
		if (preferredVideoSize.equals("720p")) {
			bandwidth = 1024 + 128;
		} else if (preferredVideoSize.equals("qvga")) {
			bandwidth = 380;
		}  else if (preferredVideoSize.equals("qcif")) {
			bandwidth = 256;
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
	
	public void useRandomPort(boolean enabled) {
		useRandomPort(enabled, true);
	}
	
	public void useRandomPort(boolean enabled, boolean apply) {
		getConfig().setBool("app", "random_port", enabled);
		if (apply) {
			if (enabled) {
				setSipPort(LINPHONE_CORE_RANDOM_PORT);
			} else {
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
		else
			port = transports.tcp;
		return String.valueOf(port);
	}

	public void setSipPort(int port) {
		Transports transports = getLc().getSignalingTransportPorts();
		transports.udp = port;
		transports.tcp = port;
		transports.tls = LINPHONE_CORE_RANDOM_PORT;
		getLc().setSignalingTransportPorts(transports);
	}
	
	public boolean isUpnpEnabled() {
		return getLc().upnpAvailable() && getLc().getFirewallPolicy() == FirewallPolicy.UseUpnp;
	}
	
	public boolean isIceEnabled() {
		return getLc().getFirewallPolicy() == FirewallPolicy.UseIce;
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
		if (url != null && url.length() == 0)
			url = null;
		getConfig().setString("misc", "config-uri", url);
	}
	
	public String getRemoteProvisioningUrl() {
		return getConfig().getString("misc", "config-uri", null);
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

	public boolean isProvisioningLoginViewEnabled() {
		return getConfig().getBool("app", "show_login_view", false);
	}
	
	public void disableProvisioningLoginView() {
		if (isProvisioningLoginViewEnabled()) { // Only do it if it was previously enabled
			getConfig().setBool("app", "show_login_view", false);
		} else {
			Log.w("Remote provisioning login view wasn't enabled, ignoring");
		}
	}
}
