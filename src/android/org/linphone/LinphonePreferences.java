package org.linphone;

/*
LinphonePreferences.java
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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import org.linphone.core.AVPFMode;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Config;
import org.linphone.core.Core;
import org.linphone.core.CoreException;
import org.linphone.core.Factory;
import org.linphone.core.LimeState;
import org.linphone.core.MediaEncryption;
import org.linphone.core.NatPolicy;
import org.linphone.core.ProxyConfig;
import org.linphone.core.TransportType;
import org.linphone.core.Transports;
import org.linphone.core.Tunnel;
import org.linphone.core.TunnelConfig;
import org.linphone.core.VideoActivationPolicy;
import org.linphone.mediastream.Log;
import org.linphone.purchase.Purchasable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class LinphonePreferences {
    private static final int LINPHONE_CORE_RANDOM_PORT = -1;
    private static LinphonePreferences instance;
    private Context mContext;
    private String basePath;

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
        basePath = mContext.getFilesDir().getAbsolutePath();
    }

    private String getString(int key) {
        if (mContext == null && LinphoneManager.isInstanciated()) {
            mContext = LinphoneManager.getInstance().getContext();
        }

        return mContext.getString(key);
    }

    private Core getLc() {
        if (!LinphoneManager.isInstanciated())
            return null;

        return LinphoneManager.getLcIfManagerNotDestroyedOrNull();
    }

    public Config getConfig() {
        Core lc = getLc();
        if (lc != null) {
            return lc.getConfig();
        }

        if (!LinphoneManager.isInstanciated()) {
            File linphonerc = new File(basePath + "/.linphonerc");
            if (linphonerc.exists()) {
                return Factory.instance().createConfig(linphonerc.getAbsolutePath());
            } else if (mContext != null) {
                InputStream inputStream = mContext.getResources().openRawResource(R.raw.linphonerc_default);
                InputStreamReader inputreader = new InputStreamReader(inputStream);
                BufferedReader buffreader = new BufferedReader(inputreader);
                StringBuilder text = new StringBuilder();
                String line;
                try {
                    while ((line = buffreader.readLine()) != null) {
                        text.append(line);
                        text.append('\n');
                    }
                } catch (IOException ioe) {
                    Log.e(ioe);
                }
                return Factory.instance().createConfigFromString(text.toString());
            }
        } else {
            return Factory.instance().createConfig(LinphoneManager.getInstance().mConfigFile);
        }
        return null;
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
    private ProxyConfig getProxyConfig(int n) {
        if (getLc() == null) return null;
        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (n < 0 || n >= prxCfgs.length)
            return null;
        return prxCfgs[n];
    }

    private AuthInfo getAuthInfo(int n) {
        ProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) return null;
        Address addr = prxCfg.getIdentityAddress();
        AuthInfo authInfo = getLc().findAuthInfo(null, addr.getUsername(), addr.getDomain());
        return authInfo;
    }

    /**
     * Removes a authInfo from the core and returns a copy of it.
     * Useful to edit a authInfo (you should call saveAuthInfo after the modifications to save them).
     */
    private AuthInfo getClonedAuthInfo(int n) {
        if (getLc() == null) return null;
        AuthInfo authInfo = getAuthInfo(n);
        if (authInfo == null)
            return null;

        AuthInfo cloneAuthInfo = authInfo.clone();
        getLc().removeAuthInfo(authInfo);
        return cloneAuthInfo;
    }

    /**
     * Saves a authInfo into the core.
     * Useful to save the changes made to a cloned authInfo.
     */
    private void saveAuthInfo(AuthInfo authInfo) {
        if (getLc() == null) return;
        getLc().addAuthInfo(authInfo);
    }

    public static class AccountBuilder {
        private Core lc;
        private String tempUsername;
        private String tempDisplayName;
        private String tempUserId;
        private String tempPassword;
        private String tempHa1;
        private String tempDomain;
        private String tempProxy;
        private String tempRealm;
        private String tempPrefix;
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


        public AccountBuilder(Core lc) {
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

        public AccountBuilder setHa1(String ha1) {
            tempHa1 = ha1;
            return this;
        }

        public AccountBuilder setDomain(String domain) {
            tempDomain = domain;
            return this;
        }

        public AccountBuilder setServerAddr(String proxy) {
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

        public AccountBuilder setUserid(String userId) {
            tempUserId = userId;
            return this;
        }

        public AccountBuilder setAvpfEnabled(boolean enable) {
            tempAvpfEnabled = enable;
            return this;
        }

        public AccountBuilder setAvpfRrInterval(int interval) {
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

        public AccountBuilder setPrefix(String prefix) {
            tempPrefix = prefix;
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
         *
         * @throws CoreException
         */
        public void saveNewAccount() throws CoreException {

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

            Address proxyAddr = Factory.instance().createAddress(proxy);
            Address identityAddr = Factory.instance().createAddress(identity);
            if (proxyAddr == null || identityAddr == null) {
                throw new CoreException("Proxy or Identity address is null !");
            }

            if (tempDisplayName != null) {
                identityAddr.setDisplayName(tempDisplayName);
            }

            if (tempTransport != null) {
                proxyAddr.setTransport(tempTransport);
            }

            String route = tempOutboundProxy ? proxyAddr.asStringUriOnly() : null;

            ProxyConfig prxCfg = lc.createProxyConfig();
            prxCfg.setIdentityAddress(identityAddr);
            prxCfg.setServerAddr(proxyAddr.asStringUriOnly());
            prxCfg.setRoute(route);
            prxCfg.enableRegister(tempEnabled);

            if (tempContactsParams != null)
                prxCfg.setContactUriParameters(tempContactsParams);
            if (tempExpire != null) {
                prxCfg.setExpires(Integer.parseInt(tempExpire));
            }

            prxCfg.setAvpfMode(AVPFMode.Enabled);
            prxCfg.setAvpfRrInterval(tempAvpfRRInterval);
            prxCfg.enableQualityReporting(tempQualityReportingEnabled);
            prxCfg.setQualityReportingCollector(tempQualityReportingCollector);
            prxCfg.setQualityReportingInterval(tempQualityReportingInterval);

            String regId = LinphonePreferences.instance().getPushNotificationRegistrationID();
            String appId = LinphonePreferences.instance().getString(R.string.push_sender_id);
            if (regId != null && LinphonePreferences.instance().isPushNotificationEnabled()) {
                String contactInfos = "app-id=" + appId + ";pn-type=" + LinphonePreferences.instance().getString(R.string.push_type) + ";pn-tok=" + regId + ";pn-silent=1";
                prxCfg.setContactUriParameters(contactInfos);
            }

            if (tempPrefix != null) {
                prxCfg.setDialPrefix(tempPrefix);
            }


            if (tempRealm != null)
                prxCfg.setRealm(tempRealm);

            AuthInfo authInfo = Factory.instance().createAuthInfo(tempUsername, tempUserId, tempPassword, tempHa1, tempRealm, tempDomain);

            lc.addProxyConfig(prxCfg);
            lc.addAuthInfo(authInfo);

            if (!tempNoDefault)
                lc.setDefaultProxyConfig(prxCfg);
        }
    }

    public void setAccountTransport(int n, String transport) {
        ProxyConfig proxyConfig = getProxyConfig(n);

        if (proxyConfig != null && transport != null) {
            Address proxyAddr;
            proxyAddr = Factory.instance().createAddress(proxyConfig.getServerAddr());
            int port = 0;
            if (transport.equals(getString(R.string.pref_transport_udp_key))) {
                proxyAddr.setTransport(TransportType.Udp);

            } else if (transport.equals(getString(R.string.pref_transport_tcp_key))) {
                proxyAddr.setTransport(TransportType.Tcp);
            } else if (transport.equals(getString(R.string.pref_transport_tls_key))) {
                proxyAddr.setTransport(TransportType.Tls);
                port = 5223;
            }

            /* 3G mobile firewall might block random TLS port, so we force use of 5223.
             * However we must NOT use this port when changing to TCP/UDP because otherwise
             * REGISTER (and everything actually) will fail...
             * */
            if ("sip.linphone.org".equals(proxyConfig.getDomain())) {
                proxyAddr.setPort(port);
            }

            ProxyConfig prxCfg = getProxyConfig(n);
            prxCfg.edit();
            prxCfg.setServerAddr(proxyAddr.asStringUriOnly());
            prxCfg.done();

            if (isAccountOutboundProxySet(n)) {
                setAccountOutboundProxyEnabled(n, true);
            }
        }
    }

    public TransportType getAccountTransport(int n) {
        TransportType transport = null;
        ProxyConfig proxyConfig = getProxyConfig(n);

        if (proxyConfig != null) {
            Address proxyAddr;
            proxyAddr = Factory.instance().createAddress(proxyConfig.getServerAddr());
            transport = proxyAddr.getTransport();
        }

        return transport;
    }

    public String getAccountTransportKey(int n) {
        TransportType transport = getAccountTransport(n);
        String key = getString(R.string.pref_transport_udp_key);

        if (transport != null && transport == TransportType.Tcp)
            key = getString(R.string.pref_transport_tcp_key);
        else if (transport != null && transport == TransportType.Tls)
            key = getString(R.string.pref_transport_tls_key);

        return key;
    }

    public String getAccountTransportString(int n) {
        TransportType transport = getAccountTransport(n);

        if (transport != null && transport == TransportType.Tcp)
            return getString(R.string.pref_transport_tcp);
        else if (transport != null && transport == TransportType.Tls)
            return getString(R.string.pref_transport_tls);

        return getString(R.string.pref_transport_udp);
    }

    public void setAccountUsername(int n, String username) {
        String identity = "sip:" + username + "@" + getAccountDomain(n);
        AuthInfo old_info = getAuthInfo(n);
        ProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) {
            Log.e("Error, no proxy config at index " + n);
            return;
        }
        prxCfg.edit();
        prxCfg.setIdentityAddress(Factory.instance().createAddress(identity));
        prxCfg.enableRegister(true);
        prxCfg.done();

        if (old_info != null) {
            // We have to remove the previous auth info after otherwise we can't unregister the previous proxy config
            AuthInfo new_info = old_info.clone();
            getLc().removeAuthInfo(old_info);
            new_info.setUsername(username);
            saveAuthInfo(new_info);
        }
    }

    public String getAccountUsername(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getUsername();
    }

    public void setAccountDisplayName(int n, String displayName) {
        try {
            ProxyConfig prxCfg = getProxyConfig(n);
            Address addr = prxCfg.getIdentityAddress();
            addr.setDisplayName(displayName);
            prxCfg.edit();
            prxCfg.setIdentityAddress(addr);
            prxCfg.done();
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public String getAccountDisplayName(int n) {
        if (getProxyConfig(n) == null || getProxyConfig(n).getIdentityAddress() == null)
            return null;
        return getProxyConfig(n).getIdentityAddress().getDisplayName();
    }

    public void setAccountUserId(int n, String userId) {
        AuthInfo info = getClonedAuthInfo(n);
        if (info != null) {
            info.setUserid(userId);
            saveAuthInfo(info);
        }
    }

    public String getAccountUserId(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getUserid();
    }

    public String getAccountRealm(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getRealm();
    }

    public void setAccountPassword(int n, String password) {
        setAccountPassword(n, password, null);
    }

    public void setAccountHa1(int n, String ha1) {
        setAccountPassword(n, null, ha1);
    }

    private void setAccountPassword(int n, String password, String ha1) {
        if (getLc() == null) return;
        String user = getAccountUsername(n);
        String domain = getAccountDomain(n);
        String userid = null;
        String realm = null;
        if (user != null && domain != null) {
            if (LinphoneManager.getLc().getAuthInfoList().length > n && LinphoneManager.getLc().getAuthInfoList()[n] != null) {
                userid = getAccountUserId(n);
                realm = getAccountRealm(n);
                LinphoneManager.getLc().removeAuthInfo(LinphoneManager.getLc().getAuthInfoList()[n]);
            }
            AuthInfo authInfo = Factory.instance().createAuthInfo(
                    user, userid, password, ha1, realm, domain);
            LinphoneManager.getLc().addAuthInfo(authInfo);
        }
    }

    public String getAccountPassword(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getPassword();
    }

    public String getAccountHa1(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getHa1();
    }

    public void setAccountIce(int n, boolean ice) {
        try {
            ProxyConfig prxCfg = getProxyConfig(n);
            prxCfg.edit();
            prxCfg.getNatPolicy().enableIce(ice);
            prxCfg.getNatPolicy().enableStun(ice);
            prxCfg.done();
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public boolean getAccountIce(int n) {
        if (getProxyConfig(n) == null || getProxyConfig(n).getNatPolicy() == null) return false;
        return getProxyConfig(n).getNatPolicy().iceEnabled();
    }

    public void setAccountStunServer(int n, String stun) {
        try {
            ProxyConfig prxCfg = getProxyConfig(n);
            prxCfg.edit();
            NatPolicy np = prxCfg.getNatPolicy();
            np.setStunServer(stun);
            prxCfg.done();
        } catch (Exception e) {
            Log.e(e);
        }
    }

    public String getAccountStunServer(int n) {
        if (getProxyConfig(n) == null || getProxyConfig(n).getNatPolicy() == null) return "";
        return getProxyConfig(n).getNatPolicy().getStunServer();
    }

    public void setAccountDomain(int n, String domain) {
        String identity = "sip:" + getAccountUsername(n) + "@" + domain;
        AuthInfo old_info = getAuthInfo(n);
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setIdentityAddress(Factory.instance().createAddress(identity));
        prxCfg.enableRegister(true);
        prxCfg.done();

        if (old_info != null) {
            // We have to remove the previous auth info after otherwise we can't unregister the previous proxy config
            AuthInfo new_info = old_info.clone();
            getLc().removeAuthInfo(old_info);
            new_info.setDomain(domain);
            saveAuthInfo(new_info);
        }
    }

    public String getAccountDomain(int n) {
        ProxyConfig proxyConf = getProxyConfig(n);
        return (proxyConf != null) ? proxyConf.getDomain() : "";
    }

    public void setAccountProxy(int n, String proxy) {
        if (proxy == null || proxy.length() <= 0) {
            proxy = getAccountDomain(n);
        }

        if (!proxy.contains("sip:")) {
            proxy = "sip:" + proxy;
        }

        Address proxyAddr = Factory.instance().createAddress(proxy);
        if (proxyAddr == null) return;

        if (!proxy.contains("transport=")) {
            proxyAddr.setTransport(getAccountTransport(n));
        }

        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setServerAddr(proxyAddr.asStringUriOnly());
        prxCfg.done();

        if (isAccountOutboundProxySet(n)) {
            setAccountOutboundProxyEnabled(n, true);
        }
    }

    public String getAccountProxy(int n) {
        String proxy = getProxyConfig(n).getServerAddr();
        return proxy;
    }


    public void setAccountOutboundProxyEnabled(int n, boolean enabled) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        if (enabled) {
            String route = prxCfg.getServerAddr();
            prxCfg.setRoute(route);
        } else {
            prxCfg.setRoute(null);
        }
        prxCfg.done();
    }

    public boolean isAccountOutboundProxySet(int n) {
        return getProxyConfig(n).getRoute() != null;
    }

    public void setAccountContactParameters(int n, String contactParams) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setContactUriParameters(contactParams);
        prxCfg.done();
    }

    public String getExpires(int n) {
        return String.valueOf(getProxyConfig(n).getExpires());
    }

    public void setExpires(int n, String expire) {
        try {
            ProxyConfig prxCfg = getProxyConfig(n);
            prxCfg.edit();
            prxCfg.setExpires(Integer.parseInt(expire));
            prxCfg.done();
        } catch (NumberFormatException nfe) {
        }
    }

    public String getPrefix(int n) {
        return getProxyConfig(n).getDialPrefix();
    }

    public void setPrefix(int n, String prefix) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setDialPrefix(prefix);
        prxCfg.done();
    }

    public boolean avpfEnabled(int n) {
        return getProxyConfig(n).avpfEnabled();
    }

    public void setAvpfMode(int n, boolean enable) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setAvpfMode(enable ? AVPFMode.Enabled : AVPFMode.Disabled);
        prxCfg.done();
    }

    public String getAvpfRrInterval(int n) {
        return String.valueOf(getProxyConfig(n).getAvpfRrInterval());
    }

    public void setAvpfRrInterval(int n, String interval) {
        try {
            ProxyConfig prxCfg = getProxyConfig(n);
            prxCfg.edit();
            prxCfg.setAvpfRrInterval(Integer.parseInt(interval));
            prxCfg.done();
        } catch (NumberFormatException nfe) {
        }
    }

    public boolean getReplacePlusByZeroZero(int n) {
        return getProxyConfig(n).getDialEscapePlus();
    }

    public void setReplacePlusByZeroZero(int n, boolean replace) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setDialEscapePlus(replace);
        prxCfg.done();
    }

    public void enablePushNotifForProxy(int n, boolean enable) {
        ProxyConfig prxCfg = getProxyConfig(n);
        prxCfg.edit();
        prxCfg.setPushNotificationAllowed(enable);
        prxCfg.done();

        setPushNotificationEnabled(isPushNotificationEnabled());
    }

    public boolean isPushNotifEnabledForProxy(int n) {
        ProxyConfig prxCfg = getProxyConfig(n);
        return prxCfg.isPushNotificationAllowed();
    }

    public boolean isFriendlistsubscriptionEnabled() {
        return getConfig().getBool("app", "friendlist_subscription_enabled", false);
    }

    public void enabledFriendlistSubscription(boolean enabled) {
        getConfig().setBool("app", "friendlist_subscription_enabled", enabled);

    }

    public void setDefaultAccount(int accountIndex) {
        if (getLc() == null) return;
        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (accountIndex >= 0 && accountIndex < prxCfgs.length)
            getLc().setDefaultProxyConfig(prxCfgs[accountIndex]);
    }

    public int getDefaultAccountIndex() {
        if (getLc() == null)
            return -1;
        ProxyConfig defaultPrxCfg = getLc().getDefaultProxyConfig();
        if (defaultPrxCfg == null)
            return -1;

        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        for (int i = 0; i < prxCfgs.length; i++) {
            if (defaultPrxCfg.getIdentityAddress().equals(prxCfgs[i].getIdentityAddress())) {
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
        if (getLc() == null) return;
        ProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) {
            LinphoneUtils.displayErrorAlert(getString(R.string.error), mContext);
            return;
        }
        prxCfg.edit();
        prxCfg.enableRegister(enabled);
        prxCfg.done();

        // If default proxy config is disabled, try to set another one as default proxy
        if (!enabled && getLc().getDefaultProxyConfig().getIdentityAddress().equals(prxCfg.getIdentityAddress())) {
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

    public void resetDefaultProxyConfig() {
        if (getLc() == null) return;
        int count = getLc().getProxyConfigList().length;
        for (int i = 0; i < count; i++) {
            if (isAccountEnabled(i)) {
                getLc().setDefaultProxyConfig(getProxyConfig(i));
                break;
            }
        }

        if (getLc().getDefaultProxyConfig() == null) {
            getLc().setDefaultProxyConfig(getProxyConfig(0));
        }
    }

    public void deleteAccount(int n) {
        if (getLc() == null) return;
        ProxyConfig proxyCfg = getProxyConfig(n);
        if (proxyCfg != null)
            getLc().removeProxyConfig(proxyCfg);
        if (getLc().getProxyConfigList().length != 0) {
            resetDefaultProxyConfig();
        } else {
            getLc().setDefaultProxyConfig(null);
        }

        AuthInfo authInfo = getAuthInfo(n);
        if (authInfo != null) {
            getLc().removeAuthInfo(authInfo);
        }

        getLc().refreshRegisters();
    }
    // End of accounts settings

    // Audio settings
    public void setEchoCancellation(boolean enable) {
        if (getLc() == null) return;
        getLc().enableEchoCancellation(enable);
    }

    public boolean echoCancellationEnabled() {
        if (getLc() == null) return false;
        return getLc().echoCancellationEnabled();
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
        if (getLc() == null) return false;
        return getLc().videoSupported() && getLc().videoEnabled();
    }

    public void enableVideo(boolean enable) {
        if (getLc() == null) return;
        getLc().enableVideoCapture(enable);
        getLc().enableVideoDisplay(enable);
    }

    public boolean shouldInitiateVideoCall() {
        if (getLc() == null) return false;
        return getLc().getVideoActivationPolicy().getAutomaticallyInitiate();
    }

    public void setInitiateVideoCall(boolean initiate) {
        if (getLc() == null) return;
        VideoActivationPolicy vap = getLc().getVideoActivationPolicy();
        vap.setAutomaticallyInitiate(initiate);
        getLc().setVideoActivationPolicy(vap);
    }

    public boolean shouldAutomaticallyAcceptVideoRequests() {
        if (getLc() == null) return false;
        VideoActivationPolicy vap = getLc().getVideoActivationPolicy();
        return vap.getAutomaticallyAccept();
    }

    public void setAutomaticallyAcceptVideoRequests(boolean accept) {
        if (getLc() == null) return;
        VideoActivationPolicy vap = getLc().getVideoActivationPolicy();
        vap.setAutomaticallyAccept(accept);
        getLc().setVideoActivationPolicy(vap);
    }

    public String getVideoPreset() {
        if (getLc() == null) return null;
        String preset = getLc().getVideoPreset();
        if (preset == null) preset = "default";
        return preset;
    }

    public void setVideoPreset(String preset) {
        if (getLc() == null) return;
        if (preset.equals("default")) preset = null;
        getLc().setVideoPreset(preset);
        preset = getVideoPreset();
        if (!preset.equals("custom")) {
            getLc().setPreferredFramerate(0);
        }
        setPreferredVideoSize(getPreferredVideoSize()); // Apply the bandwidth limit
    }

    public String getPreferredVideoSize() {
        //Core can only return video size (width and height), not the name
        return getConfig().getString("video", "size", "qvga");
    }

    public void setPreferredVideoSize(String preferredVideoSize) {
        if (getLc() == null) return;
        getLc().setPreferredVideoSizeByName(preferredVideoSize);
    }

    public int getPreferredVideoFps() {
        if (getLc() == null) return 0;
        return (int) getLc().getPreferredFramerate();
    }

    public void setPreferredVideoFps(int fps) {
        if (getLc() == null) return;
        getLc().setPreferredFramerate(fps);
    }

    public int getBandwidthLimit() {
        if (getLc() == null) return 0;
        return getLc().getDownloadBandwidth();
    }

    public void setBandwidthLimit(int bandwidth) {
        if (getLc() == null) return;
        getLc().setUploadBandwidth(bandwidth);
        getLc().setDownloadBandwidth(bandwidth);
    }
    // End of video settings

    // Call settings
    public boolean useRfc2833Dtmfs() {
        if (getLc() == null) return false;
        return getLc().getUseRfc2833ForDtmf();
    }

    public void sendDtmfsAsRfc2833(boolean use) {
        if (getLc() == null) return;
        getLc().setUseRfc2833ForDtmf(use);
    }

    public boolean useSipInfoDtmfs() {
        if (getLc() == null) return false;
        return getLc().getUseInfoForDtmf();
    }

    public void sendDTMFsAsSipInfo(boolean use) {
        if (getLc() == null) return;
        getLc().setUseInfoForDtmf(use);
    }

    public int getIncTimeout() {
        if (getLc() == null) return 0;
        return getLc().getIncTimeout();
    }

    public void setIncTimeout(int timeout) {
        if (getLc() == null) return;
        getLc().setIncTimeout(timeout);
    }

    public int getInCallTimeout() {
        if (getLc() == null) return 0;
        return getLc().getInCallTimeout();
    }

    public void setInCallTimeout(int timeout) {
        if (getLc() == null) return;
        getLc().setInCallTimeout(timeout);
    }

    public String getVoiceMailUri() {
        return getConfig().getString("app", "voice_mail", null);
    }

    public void setVoiceMailUri(String uri) {
        getConfig().setString("app", "voice_mail", uri);
    }

    public boolean getNativeDialerCall() {
        return getConfig().getBool("app", "native_dialer_call", false);
    }

    public void setNativeDialerCall(boolean use) {
        getConfig().setBool("app", "native_dialer_call", use);
    }
// End of call settings

    // Network settings
    public void setWifiOnlyEnabled(Boolean enable) {
        if (getLc() == null) return;
        getLc().enableWifiOnly(enable);
    }

    public boolean isWifiOnlyEnabled() {
        if (getLc() == null) return false;
        return getLc().wifiOnlyEnabled();
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
        if (getLc() == null) return null;
        Transports transports = getLc().getTransports();
        int port;
        if (transports.getUdpPort() > 0)
            port = transports.getUdpPort();
        else
            port = transports.getTcpPort();
        return String.valueOf(port);
    }

    public void setSipPort(int port) {
        if (getLc() == null) return;
        Transports transports = getLc().getTransports();
        transports.setUdpPort(port);
        transports.setTcpPort(port);
        transports.setTlsPort(LINPHONE_CORE_RANDOM_PORT);
        getLc().setTransports(transports);
    }

    private NatPolicy getOrCreateNatPolicy() {
        if (getLc() == null) return null;
        NatPolicy nat = getLc().getNatPolicy();
        if (nat == null) {
            nat = getLc().createNatPolicy();
        }
        return nat;
    }

    public String getStunServer() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.getStunServer();
    }

    public void setStunServer(String stun) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.setStunServer(stun);

        if (stun != null && !stun.isEmpty()) {
        }
        getLc().setNatPolicy(nat);
    }

    public void setIceEnabled(boolean enabled) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.enableIce(enabled);
        nat.enableStun(enabled);
        getLc().setNatPolicy(nat);
    }

    public void setTurnEnabled(boolean enabled) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.enableTurn(enabled);
        getLc().setNatPolicy(nat);
    }

    public void setUpnpEnabled(boolean enabled) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.enableUpnp(enabled);
        getLc().setNatPolicy(nat);
    }

    public boolean isUpnpEnabled() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.upnpEnabled();
    }

    public boolean isIceEnabled() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.iceEnabled();
    }

    public boolean isTurnEnabled() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.turnEnabled();
    }

    public String getTurnUsername() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.getStunServerUsername();
    }

    public void setTurnUsername(String username) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        AuthInfo authInfo = getLc().findAuthInfo(null, nat.getStunServerUsername(), null);

        if (authInfo != null) {
            AuthInfo cloneAuthInfo = authInfo.clone();
            getLc().removeAuthInfo(authInfo);
            cloneAuthInfo.setUsername(username);
            cloneAuthInfo.setUserid(username);
            getLc().addAuthInfo(cloneAuthInfo);
        } else {
            authInfo = Factory.instance().createAuthInfo(username, username, null, null, null, null);
            getLc().addAuthInfo(authInfo);
        }
        nat.setStunServerUsername(username);
        getLc().setNatPolicy(nat);
    }

    public void setTurnPassword(String password) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        AuthInfo authInfo = getLc().findAuthInfo(null, nat.getStunServerUsername(), null);

        if (authInfo != null) {
            AuthInfo cloneAuthInfo = authInfo.clone();
            getLc().removeAuthInfo(authInfo);
            cloneAuthInfo.setPassword(password);
            getLc().addAuthInfo(cloneAuthInfo);
        } else {
            authInfo = Factory.instance().createAuthInfo(nat.getStunServerUsername(), nat.getStunServerUsername(), password, null, null, null);
            getLc().addAuthInfo(authInfo);
        }
    }

    public MediaEncryption getMediaEncryption() {
        if (getLc() == null) return null;
        return getLc().getMediaEncryption();
    }

    public void setMediaEncryption(MediaEncryption menc) {
        if (getLc() == null) return;
        if (menc == null)
            return;

        getLc().setMediaEncryption(menc);
    }

    public void setPushNotificationEnabled(boolean enable) {
        getConfig().setBool("app", "push_notification", enable);

        Core lc = getLc();
        if (lc == null) {
            return;
        }

        if (enable) {
            // Add push infos to exisiting proxy configs
            String regId = getPushNotificationRegistrationID();
            String appId = getString(R.string.push_sender_id);
            if (regId != null && lc.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : lc.getProxyConfigList()) {
                    if (lpc == null) continue;
                    if (!lpc.isPushNotificationAllowed()) {
                        lpc.edit();
                        lpc.setContactUriParameters(null);
                        lpc.done();
                        if (lpc.getIdentityAddress() != null)
                            Log.d("Push notif infos removed from proxy config " + lpc.getIdentityAddress().asStringUriOnly());
                    } else {
                        String contactInfos = "app-id=" + appId + ";pn-type=" + getString(R.string.push_type) + ";pn-tok=" + regId + ";pn-silent=1";
                        String prevContactParams = lpc.getContactParameters();
                        if (prevContactParams == null || prevContactParams.compareTo(contactInfos) != 0) {
                            lpc.edit();
                            lpc.setContactUriParameters(contactInfos);
                            lpc.done();
                            if (lpc.getIdentityAddress() != null)
                                Log.d("Push notif infos added to proxy config " + lpc.getIdentityAddress().asStringUriOnly());
                        }
                    }
                }
                Log.i("[Push Notification] Refreshing registers to ensure token is up to date" + regId);
                lc.refreshRegisters();
            }
        } else {
            if (lc.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : lc.getProxyConfigList()) {
                    lpc.edit();
                    lpc.setContactUriParameters(null);
                    lpc.done();
                    if (lpc.getIdentityAddress() != null)
                        Log.d("Push notif infos removed from proxy config " + lpc.getIdentityAddress().asStringUriOnly());
                }
                lc.refreshRegisters();
            }
        }
    }

    public boolean isPushNotificationEnabled() {
        return getConfig().getBool("app", "push_notification", true);
    }

    public void setPushNotificationRegistrationID(String regId) {
        if (getConfig() == null) return;
        Log.i("[Push Notification] New token received" + regId);
        getConfig().setString("app", "push_notification_regid", (regId != null) ? regId : "");
        setPushNotificationEnabled(isPushNotificationEnabled());
    }

    public String getPushNotificationRegistrationID() {
        return getConfig().getString("app", "push_notification_regid", null);
    }

    public void useIpv6(Boolean enable) {
        if (getLc() == null) return;
        getLc().enableIpv6(enable);
    }

    public boolean isUsingIpv6() {
        if (getLc() == null) return false;
        return getLc().ipv6Enabled();
    }
    // End of network settings

    // Advanced settings
    public void setDebugEnabled(boolean enabled) {
        getConfig().setBool("app", "debug", enabled);
        LinphoneUtils.initLoggingService(enabled, mContext.getString(R.string.app_name));
    }

    public boolean isDebugEnabled() {
        return getConfig().getBool("app", "debug", false);
    }

    public void setJavaLogger(boolean enabled) {
        getConfig().setBool("app", "java_logger", enabled);
        LinphoneUtils.initLoggingService(isDebugEnabled(), mContext.getString(R.string.app_name));
    }

    public boolean useJavaLogger() {
        return getConfig().getBool("app", "java_logger", false);
    }

    public void setBackgroundModeEnabled(boolean enabled) {
        getConfig().setBool("app", "background_mode", enabled);
    }

    public boolean isBackgroundModeEnabled() {
        return getConfig().getBool("app", "background_mode", true);
    }

    public boolean isAutoStartEnabled() {
        return getConfig().getBool("app", "auto_start", false);
    }

    public void setAutoStart(boolean autoStartEnabled) {
        getConfig().setBool("app", "auto_start", autoStartEnabled);
    }

    public String getSharingPictureServerUrl() {
        if (getLc() == null) return null;
        return getLc().getFileTransferServer();
    }

    public void setSharingPictureServerUrl(String url) {
        if (getLc() == null) return;
        getLc().setFileTransferServer(url);
    }

    public void setRemoteProvisioningUrl(String url) {
        if (getLc() == null) return;
        if (url != null && url.length() == 0) {
            url = null;
        }
        getLc().setProvisioningUri(url);
    }

    public String getRemoteProvisioningUrl() {
        if (getLc() == null) return null;
        return getLc().getProvisioningUri();
    }

    public void setDefaultDisplayName(String displayName) {
        if (getLc() == null) return;
        Address primary = getLc().getPrimaryContactParsed();
        primary.setDisplayName(displayName);
        getLc().setPrimaryContact(primary.asString());
    }

    public String getDefaultDisplayName() {
        if (getLc() == null) return null;
        return getLc().getPrimaryContactParsed().getDisplayName();
    }

    public void setDefaultUsername(String username) {
        if (getLc() == null) return;
        Address primary = getLc().getPrimaryContactParsed();
        primary.setUsername(username);
        getLc().setPrimaryContact(primary.asString());
    }

    public String getDefaultUsername() {
        if (getLc() == null) return null;
        return getLc().getPrimaryContactParsed().getUsername();
    }
    // End of advanced settings

    // Tunnel settings
    private TunnelConfig tunnelConfig = null;

    public TunnelConfig getTunnelConfig() {
        if (getLc() == null) return null;
        if (getLc().tunnelAvailable()) {
            Tunnel tunnel = getLc().getTunnel();
            if (tunnelConfig == null) {
                TunnelConfig servers[] = tunnel.getServers();
                if (servers.length > 0) {
                    tunnelConfig = servers[0];
                } else {
                    tunnelConfig = Factory.instance().createTunnelConfig();
                }
            }
            return tunnelConfig;
        } else {
            return null;
        }
    }

    public String getTunnelHost() {
        TunnelConfig config = getTunnelConfig();
        if (config != null) {
            return config.getHost();
        } else {
            return null;
        }
    }

    public void setTunnelHost(String host) {
        TunnelConfig config = getTunnelConfig();
        if (config != null) {
            config.setHost(host);
            LinphoneManager.getInstance().initTunnelFromConf();
        }
    }

    public int getTunnelPort() {
        TunnelConfig config = getTunnelConfig();
        if (config != null) {
            return config.getPort();
        } else {
            return -1;
        }
    }

    public void setTunnelPort(int port) {
        TunnelConfig config = getTunnelConfig();
        if (config != null) {
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

        return (getConfig() != null) ? getConfig().getBool("app", "show_login_view", false) : false;
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

    public boolean adaptiveRateControlEnabled() {
        if (getLc() == null) return false;
        return getLc().adaptiveRateControlEnabled();
    }

    public void enableAdaptiveRateControl(boolean enabled) {
        if (getLc() == null) return;
        getLc().enableAdaptiveRateControl(enabled);
    }

    public int getCodecBitrateLimit() {
        return getConfig().getInt("audio", "codec_bitrate_limit", 36);
    }

    public void setCodecBitrateLimit(int bitrate) {
        getConfig().setInt("audio", "codec_bitrate_limit", bitrate);
    }

    public void contactsMigrationDone() {
        getConfig().setBool("app", "contacts_migration_done", true);
    }

    public boolean isContactsMigrationDone() {
        return getConfig().getBool("app", "contacts_migration_done", false);
    }

    public String getInAppPurchaseValidatingServerUrl() {
        return getConfig().getString("in-app-purchase", "server_url", null);
    }

    public Purchasable getInAppPurchasedItem() {
        String id = getConfig().getString("in-app-purchase", "purchase_item_id", null);
        String payload = getConfig().getString("in-app-purchase", "purchase_item_payload", null);
        String signature = getConfig().getString("in-app-purchase", "purchase_item_signature", null);
        String username = getConfig().getString("in-app-purchase", "purchase_item_username", null);

        Purchasable item = new Purchasable(id).setPayloadAndSignature(payload, signature).setUserData(username);
        return item;
    }

    public void setInAppPurchasedItem(Purchasable item) {
        if (item == null)
            return;

        getConfig().setString("in-app-purchase", "purchase_item_id", item.getId());
        getConfig().setString("in-app-purchase", "purchase_item_payload", item.getPayload());
        getConfig().setString("in-app-purchase", "purchase_item_signature", item.getPayloadSignature());
        getConfig().setString("in-app-purchase", "purchase_item_username", item.getUserData());
    }

    public ArrayList<String> getInAppPurchasables() {
        ArrayList<String> purchasables = new ArrayList<String>();
        String list = getConfig().getString("in-app-purchase", "purchasable_items_ids", null);
        if (list != null) {
            for (String purchasable : list.split(";")) {
                if (purchasable.length() > 0) {
                    purchasables.add(purchasable);
                }
            }
        }
        return purchasables;
    }

    public String getXmlrpcUrl() {
        return getConfig().getString("assistant", "xmlrpc_url", null);
    }

    public void setXmlrpcUrl(String url) {
        getConfig().setString("assistant", "xmlrpc_url", url);
    }

    public String getInappPopupTime() {
        return getConfig().getString("app", "inapp_popup_time", null);
    }

    public void setInappPopupTime(String date) {
        getConfig().setString("app", "inapp_popup_time", date);
    }

    public void setLinkPopupTime(String date) {
        getConfig().setString("app", "link_popup_time", date);
    }

    public String getLinkPopupTime() {
        return getConfig().getString("app", "link_popup_time", null);
    }

    public String getXmlRpcServerUrl() {
        return getConfig().getString("app", "server_url", null);
    }

    public String getDebugPopupAddress() {
        return getConfig().getString("app", "debug_popup_magic", null);
    }

    public String getActivityToLaunchOnIncomingReceived() {
        return getConfig().getString("app", "incoming_call_activity", "org.linphone.activities.LinphoneActivity");
    }

    public void setActivityToLaunchOnIncomingReceived(String name) {
        getConfig().setString("app", "incoming_call_activity", name);
    }

    public boolean getServiceNotificationVisibility() {
        return getConfig().getBool("app", "show_service_notification", false);
    }

    public void setServiceNotificationVisibility(boolean enable) {
        getConfig().setBool("app", "show_service_notification", enable);
    }

    public boolean isOverlayEnabled() {
        return getConfig().getBool("app", "display_overlay", false);
    }

    public void enableOverlay(boolean enable) {
        getConfig().setBool("app", "display_overlay", enable);
    }

    public LimeState limeEnabled() {
        if (getLc() == null) return LimeState.Disabled;
        return getLc().limeEnabled();
    }

    public void enableLime(LimeState lime) {
        if (getLc() == null) return;
        getLc().enableLime(lime);
    }

    public boolean firstTimeAskingForPermission(String permission) {
        return firstTimeAskingForPermission(permission, true);
    }

    public boolean firstTimeAskingForPermission(String permission, boolean toggle) {
        boolean firstTime = getConfig().getBool("app", permission, true);
        if (toggle) {
            permissionHasBeenAsked(permission);
        }
        return firstTime;
    }

    public void permissionHasBeenAsked(String permission) {
        getConfig().setBool("app", permission, false);
    }

    public boolean isDeviceRingtoneEnabled() {
        int readExternalStorage = mContext.getPackageManager().checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, mContext.getPackageName());
        return getConfig().getBool("app", "device_ringtone", true) && readExternalStorage == PackageManager.PERMISSION_GRANTED;
    }

    public void enableDeviceRingtone(boolean enable) {
        getConfig().setBool("app", "device_ringtone", enable);
    }

    public boolean isBisFeatureEnabled() {
        return getConfig().getBool("app", "bis_feature", true);
    }

    public void enableBisFeature(boolean enable) {
        getConfig().setBool("app", "bis_feature", enable);
    }

    public boolean isAutoAnswerEnabled() {
        return getConfig().getBool("app", "auto_answer", false);
    }

    public void enableAutoAnswer(boolean enable) {
        getConfig().setBool("app", "auto_answer", enable);
    }

    public void setAutoAnswerTime(int time) {
        getConfig().setInt("app", "auto_answer_delay", time);
    }

    public int getAutoAnswerTime() {
        return getConfig().getInt("app", "auto_answer_delay", 0);
    }

    public int getCodeLength() {
        return getConfig().getInt("app", "activation_code_length", 0);
    }

    public void disableFriendsStorage() {
        getConfig().setBool("misc", "store_friends", false);
    }

    public void enableFriendsStorage() {
        getConfig().setBool("misc", "store_friends", true);
    }

    public boolean isFriendsStorageEnabled() {
        return getConfig().getBool("misc", "store_friends", true);
    }

    public boolean useBasicChatRoomFor1To1() {
        return getConfig().getBool("app", "prefer_basic_chat_room", false);
    }
}
