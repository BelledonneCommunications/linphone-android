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
import org.linphone.core.LinphoneAddress.TransportType;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.AdaptiveRateAlgorithm;
import org.linphone.core.LinphoneCore.FirewallPolicy;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.Transports;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.LpConfig;
import org.linphone.core.TunnelConfig;
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

	public void setContext(Context c) {
		mContext = c;
	}

	private String getString(int key) {
		if (mContext == null && LinphoneManager.isInstanciated()) {
			mContext = LinphoneManager.getInstance().getContext();
		}

		return mContext.getString(key);
	}

	private LinphoneCore getLc() {
		if (!LinphoneManager.isInstanciated())
			return null;

		return LinphoneManager.getLcIfManagerNotDestroyedOrNull();
	}

	public LpConfig getConfig() {
		LinphoneCore lc = getLc();
		if (lc != null) {
			return lc.getConfig();
		}

		if (!LinphoneManager.isInstanciated()) {
			Log.w("LinphoneManager not instanciated yet...");
			return LinphoneCoreFactory.instance().createLpConfig(mContext.getFilesDir().getAbsolutePath() + "/.linphonerc");
		}

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
		private String tempUsername;
		private String tempDisplayName;
		private String tempUserId;
		private String tempPassword;
		private String tempDomain;
		private String tempProxy;
		private String tempRealm;
		private boolean tempOutboundProxy;
		private String tempContactsParams;
		private String tempExpire;
		private TransportType tempTransport;
		private boolean tempAvpfEnabled = false;
		private int tempAvpfRRInterval = 0;
		private String tempQualityReportingCollector;
		private boolean tempQualityReportingEnabled = false;
		private int tempQualityReportingInterval = 0;
		private boolean tempEnabled = true;
		private boolean tempNoDefault = false;


		public AccountBuilder(LinphoneCore lc) {
			this.lc = lc;
		}

		public AccountBuilder setTransport(TransportType transport) {
			tempTransport = transport;
			return this;
		}

		public AccountBuilder setUsername(String username) {
			tempUsername = username;
			return this;
		}

		public AccountBuilder setDisplayName(String displayName) {
			tempDisplayName = displayName;
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

		public AccountBuilder setAvpfEnabled(boolean enable) {
			tempAvpfEnabled = enable;
			return this;
		}

		public AccountBuilder setAvpfRRInterval(int interval) {
			tempAvpfRRInterval = interval;
			return this;
		}

		public AccountBuilder setRealm(String realm) {
			tempRealm = realm;
			return this;
		}

		public AccountBuilder setQualityReportingCollector(String collector) {
			tempQualityReportingCollector = collector;
			return this;
		}

		public AccountBuilder setQualityReportingEnabled(boolean enable) {
			tempQualityReportingEnabled = enable;
			return this;
		}

		public AccountBuilder setQualityReportingInterval(int interval) {
			tempQualityReportingInterval = interval;
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

			if (tempUsername == null || tempUsername.length() < 1 || tempDomain == null || tempDomain.length() < 1) {
				Log.w("Skipping account save: username or domain not provided");
				return;
			}

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
			LinphoneAddress identityAddr = LinphoneCoreFactory.instance().createLinphoneAddress(identity);

			if (tempDisplayName != null) {
				identityAddr.setDisplayName(tempDisplayName);
			}

			if (tempTransport != null) {
				proxyAddr.setTransport(tempTransport);
			}

			String route = tempOutboundProxy ? proxyAddr.asStringUriOnly() : null;

			LinphoneProxyConfig prxCfg = lc.createProxyConfig(identityAddr.asString(), proxyAddr.asStringUriOnly(), route, tempEnabled);

			if (tempContactsParams != null)
				prxCfg.setContactUriParameters(tempContactsParams);
			if (tempExpire != null) {
				try {
					prxCfg.setExpires(Integer.parseInt(tempExpire));
				} catch (NumberFormatException nfe) { }
			}

			prxCfg.enableAvpf(tempAvpfEnabled);
			prxCfg.setAvpfRRInterval(tempAvpfRRInterval);
			prxCfg.enableQualityReporting(tempQualityReportingEnabled);
			prxCfg.setQualityReportingCollector(tempQualityReportingCollector);
			prxCfg.setQualityReportingInterval(tempQualityReportingInterval);

			if(tempRealm != null)
				prxCfg.setRealm(tempRealm);

			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, null, null, tempDomain);

			lc.addProxyConfig(prxCfg);
			lc.addAuthInfo(authInfo);

			if (!tempNoDefault)
				lc.setDefaultProxyConfig(prxCfg);
		}
	}

	public void setAccountTransport(int n, String transport) {
		LinphoneProxyConfig proxyConfig = getProxyConfig(n);

		if (proxyConfig != null && transport != null) {
			LinphoneAddress proxyAddr;
			try {
				proxyAddr = LinphoneCoreFactory.instance().createLinphoneAddress(proxyConfig.getProxy());
                int port = 0;
				if (transport.equals(getString(R.string.pref_transport_udp_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportUdp);
				} else if (transport.equals(getString(R.string.pref_transport_tcp_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportTcp);
				} else if (transport.equals(getString(R.string.pref_transport_tls_key))) {
					proxyAddr.setTransport(TransportType.LinphoneTransportTls);
                    port = 5223;
				}

                /* 3G mobile firewall might block random TLS port, so we force use of 5223.
                 * However we must NOT use this port when changing to TCP/UDP because otherwise
                  * REGISTER (and everything actually) will fail...
                  * */
                if ("sip.linphone.org".equals(proxyConfig.getDomain())) {
                    proxyAddr.setPort(port);
                }

				LinphoneProxyConfig prxCfg = getProxyConfig(n);
				prxCfg.edit();
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
			prxCfg.edit();
			prxCfg.setIdentity(identity);
			prxCfg.done();

			if(info != null) {
				info.setUsername(username);
				saveAuthInfo(info);
			}
		} catch (LinphoneCoreException e) {
			e.printStackTrace();
		}
	}

	public String getAccountUsername(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUsername();
	}

	public void setAccountDisplayName(int n, String displayName) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			LinphoneAddress addr = LinphoneCoreFactory.instance().createLinphoneAddress(prxCfg.getIdentity());
			addr.setDisplayName(displayName);
			prxCfg.edit();
			prxCfg.setIdentity(addr.asString());
			prxCfg.done();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getAccountDisplayName(int n) {
		LinphoneAddress addr = getProxyConfig(n).getAddress();
		if(addr != null) {
			return addr.getDisplayName();
		}
		return null;
	}

	public void setAccountUserId(int n, String userId) {
		LinphoneAuthInfo info = getClonedAuthInfo(n);
		if(info != null) {
			info.setUserId(userId);
			saveAuthInfo(info);
		}
	}

	public String getAccountUserId(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getUserId();
	}

	public void setAccountPassword(int n, String password) {
		if(getAccountDomain(n) != null && getAccountUsername(n) != null) {
			LinphoneAuthInfo authInfo = LinphoneCoreFactory.instance().createAuthInfo(getAccountUsername(n), null, password, null, null, getAccountDomain(n));
			LinphoneManager.getLc().addAuthInfo(authInfo);
		}
	}

	public String getAccountPassword(int n) {
		LinphoneAuthInfo authInfo = getAuthInfo(n);
		return authInfo == null ? null : authInfo.getPassword();
	}
	public void setAccountDomain(int n, String domain) {
		String identity = "sip:" + getAccountUsername(n) + "@" + domain;

		try {
			LinphoneAuthInfo authInfo = getClonedAuthInfo(n);
			if(authInfo != null) {
				authInfo.setDomain(domain);
				saveAuthInfo(authInfo);
			}

			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.edit();
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
			prxCfg.edit();
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
			prxCfg.edit();
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
		prxCfg.edit();
		prxCfg.setContactUriParameters(contactParams);
		prxCfg.done();
	}

	public String getExpires(int n) {
		return String.valueOf(getProxyConfig(n).getExpires());
	}

	public void setExpires(int n, String expire) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.edit();
			prxCfg.setExpires(Integer.parseInt(expire));
			prxCfg.done();
		} catch (NumberFormatException nfe) { }
	}

	public String getPrefix(int n) {
		return getProxyConfig(n).getDialPrefix();
	}

	public void setPrefix(int n, String prefix) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.edit();
		prxCfg.setDialPrefix(prefix);
		prxCfg.done();
	}

	public boolean avpfEnabled(int n) {
		return getProxyConfig(n).avpfEnabled();
	}

	public void enableAvpf(int n, boolean enable) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.edit();
		prxCfg.enableAvpf(enable);
		prxCfg.done();
	}

	public String getAvpfRRInterval(int n) {
		return String.valueOf(getProxyConfig(n).getAvpfRRInterval());
	}

	public void setAvpfRRInterval(int n, String interval) {
		try {
			LinphoneProxyConfig prxCfg = getProxyConfig(n);
			prxCfg.edit();
			prxCfg.setAvpfRRInterval(Integer.parseInt(interval));
			prxCfg.done();
		} catch (NumberFormatException nfe) { }
	}

	public boolean getReplacePlusByZeroZero(int n) {
		return getProxyConfig(n).getDialEscapePlus();
	}

	public void setReplacePlusByZeroZero(int n, boolean replace) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.edit();
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
			return -1;

		LinphoneProxyConfig[] prxCfgs = getLc().getProxyConfigList();
		for (int i = 0; i < prxCfgs.length; i++) {
			if (defaultPrxCfg.getIdentity().equals(prxCfgs[i].getIdentity())) {
				return i;
			}
		}
		return -1;
	}

	public int getAccountCount() {
		if (getLc() == null || getLc().getProxyConfigList() == null)
			return 0;

		return getLc().getProxyConfigList().length;
	}

	public void setAccountEnabled(int n, boolean enabled) {
		LinphoneProxyConfig prxCfg = getProxyConfig(n);
		prxCfg.edit();
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

	public void resetDefaultProxyConfig(){
		int count = getLc().getProxyConfigList().length;
		for (int i = 0; i < count; i++) {
			if (isAccountEnabled(i)) {
				getLc().setDefaultProxyConfig(getProxyConfig(i));
				break;
			}
		}

		if(getLc().getDefaultProxyConfig() == null){
			getLc().setDefaultProxyConfig(getProxyConfig(0));
		}
	}

	public void deleteAccount(int n) {
		final LinphoneProxyConfig proxyCfg = getProxyConfig(n);
		if (proxyCfg != null)
			getLc().removeProxyConfig(proxyCfg);
		if (getLc().getProxyConfigList().length != 0) {
			resetDefaultProxyConfig();
		} else {
			getLc().setDefaultProxyConfig(null);
		}
		getLc().refreshRegisters();
	}
	// End of accounts settings

	// Audio settings
	public void setEchoCancellation(boolean enable) {
		getLc().enableEchoCancellation(enable);
	}

	public boolean isEchoCancellationEnabled() {
		return getLc().isEchoCancellationEnabled();
	}

	public int getEchoCalibration() {
		return getConfig().getInt("sound", "ec_delay", -1);
	}

	public boolean isEchoConfigurationUpdated() {
		return getConfig().getBool("app", "ec_updated", false);
	}

	public void echoConfigurationUpdated() {
		getConfig().setBool("app", "ec_updated", true);
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

	public String getVideoPreset() {
		String preset = getLc().getVideoPreset();
		if (preset == null) preset = "default";
		return preset;
	}

	public void setVideoPreset(String preset) {
		if (preset.equals("default")) preset = null;
		getLc().setVideoPreset(preset);
		preset = getVideoPreset();
		if (!preset.equals("custom")) {
			getLc().setPreferredFramerate(0);
		}
		setPreferredVideoSize(getPreferredVideoSize()); // Apply the bandwidth limit
	}

	public String getPreferredVideoSize() {
		//LinphoneCore can only return video size (width and height), not the name
		return getConfig().getString("video", "size", "qvga");
	}

	public void setPreferredVideoSize(String preferredVideoSize) {
		getLc().setPreferredVideoSizeByName(preferredVideoSize);
		String preset = getVideoPreset();
		if (!preset.equals("custom")) {
			int bandwidth = 512;
			if (preferredVideoSize.equals("720p")) {
				bandwidth = 1024 + 128;
			} else if (preferredVideoSize.equals("vga")) {
				bandwidth = 660;
			} else if (preferredVideoSize.equals("qvga")) {
				bandwidth = 380;
			} else if (preferredVideoSize.equals("qcif")) {
				bandwidth = 256;
			}
			setBandwidthLimit(bandwidth);
		}
	}

	public int getPreferredVideoFps() {
		return (int)getLc().getPreferredFramerate();
	}

	public void setPreferredVideoFps(int fps) {
		getLc().setPreferredFramerate(fps);
	}

	public int getBandwidthLimit() {
		return getLc().getDownloadBandwidth();
	}

	public void setBandwidthLimit(int bandwidth) {
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

	public String getVoiceMailUri() {
		return getConfig().getString("app", "voice_mail", null);
	}

	public void setVoiceMailUri(String uri) {
		getConfig().setString("app", "voice_mail", uri);
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

		 if (enable) {
			 // Add push infos to exisiting proxy configs
			 String regId = getPushNotificationRegistrationID();
			 String appId = getString(R.string.push_sender_id);
			 if (regId != null && getLc().getProxyConfigList().length > 0) {
				 for (LinphoneProxyConfig lpc : getLc().getProxyConfigList()) {
					 String contactInfos = "app-id=" + appId + ";pn-type=google;pn-tok=" + regId;
					 lpc.edit();
					 lpc.setContactUriParameters(contactInfos);
					 lpc.done();
					 Log.d("Push notif infos added to proxy config");
				 }
				 getLc().refreshRegisters();
			 }
		 } else {
			 if (getLc().getProxyConfigList().length > 0) {
				 for (LinphoneProxyConfig lpc : getLc().getProxyConfigList()) {
					 lpc.edit();
					 lpc.setContactUriParameters(null);
					 lpc.done();
					 Log.d("Push notif infos removed from proxy config");
				 }
				 getLc().refreshRegisters();
			 }
		 }
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
		LinphoneCoreFactory.instance().enableLogCollection(enabled);
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
		if (url != null && url.length() == 0) {
			url = null;
		}
		getLc().setProvisioningUri(url);
	}

	public String getRemoteProvisioningUrl() {
		return getLc().getProvisioningUri();
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
	private TunnelConfig tunnelConfig = null;

	public TunnelConfig getTunnelConfig() {
		if(getLc().isTunnelAvailable()) {
			if(tunnelConfig == null) {
				TunnelConfig servers[] = getLc().tunnelGetServers();
				if(servers.length > 0) {
					tunnelConfig = servers[0];
				} else {
					tunnelConfig = LinphoneCoreFactory.instance().createTunnelConfig();
				}
			}
			return tunnelConfig;
		} else {
			return null;
		}
	}

	public String getTunnelHost() {
		TunnelConfig config = getTunnelConfig();
		if(config != null) {
			return config.getHost();
		} else {
			return null;
		}
	}

	public void setTunnelHost(String host) {
		TunnelConfig config = getTunnelConfig();
		if(config != null) {
			config.setHost(host);
			LinphoneManager.getInstance().initTunnelFromConf();
		}
	}

	public int getTunnelPort() {
		TunnelConfig config = getTunnelConfig();
		if(config != null) {
			return config.getPort();
		} else {
			return -1;
		}
	}

	public void setTunnelPort(int port) {
		TunnelConfig config = getTunnelConfig();
		if(config != null) {
			config.setPort(port);
			LinphoneManager.getInstance().initTunnelFromConf();
		}
	}

	public String getTunnelMode() {
		return getConfig().getString("app", "tunnel", null);
	}

	public void setTunnelMode(String mode) {
		getConfig().setString("app", "tunnel", mode);
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

	public void firstRemoteProvisioningSuccessful() {
		getConfig().setBool("app", "first_remote_provisioning", false);
	}

	public boolean isFirstRemoteProvisioning() {
		return getConfig().getBool("app", "first_remote_provisioning", true);
	}

	public boolean isAdaptiveRateControlEnabled() {
		return getLc().isAdaptiveRateControlEnabled();
	}

	public void enableAdaptiveRateControl(boolean enabled) {
		getLc().enableAdaptiveRateControl(enabled);
	}

	public AdaptiveRateAlgorithm getAdaptiveRateAlgorithm() {
		return getLc().getAdaptiveRateAlgorithm();
	}

	public void setAdaptiveRateAlgorithm(AdaptiveRateAlgorithm alg) {
		getLc().setAdaptiveRateAlgorithm(alg);
	}

	public int getCodecBitrateLimit() {
		return getConfig().getInt("audio", "codec_bitrate_limit", 36);
	}

	public void setCodecBitrateLimit(int bitrate) {
		getConfig().setInt("audio", "codec_bitrate_limit", bitrate);
	}

	public void contactsMigrationDone(){
		getConfig().setBool("app", "contacts_migration_done", true);
	}

	public boolean isContactsMigrationDone(){
		return getConfig().getBool("app", "contacts_migration_done",false);
	}

	public String getXmlRpcServerUrl() {
		return getConfig().getString("app", "server_url", null);
	}

	public String getDebugPopupAddress(){
		return getConfig().getString("app", "debug_popup_magic", null);
	}

	public void enableDebugLogs(Boolean debugMode){
		getConfig().setBool("app", "debug_logs_enabled", debugMode);
	}

	public Boolean isDebugLogsEnabled(){
		return getConfig().getBool("app", "debug_logs_enabled", false);
	}

	public Boolean audioPermAsked(){
		return getConfig().getBool("app", "audio_perm", false);
	}

	public void neverAskAudioPerm(){
		 getConfig().setBool("app", "audio_perm", true);
	}

	public Boolean cameraPermAsked(){
		return getConfig().getBool("app", "camera_perm", false);
	}

	public void neverAskCameraPerm(){
		getConfig().setBool("app", "camera_perm", true);
	}
}
