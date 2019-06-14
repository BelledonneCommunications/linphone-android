package org.linphone.settings;

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
import androidx.appcompat.app.AppCompatDelegate;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.compatibility.Compatibility;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Config;
import org.linphone.core.Core;
import org.linphone.core.Factory;
import org.linphone.core.MediaEncryption;
import org.linphone.core.NatPolicy;
import org.linphone.core.ProxyConfig;
import org.linphone.core.Transports;
import org.linphone.core.Tunnel;
import org.linphone.core.TunnelConfig;
import org.linphone.core.VideoActivationPolicy;
import org.linphone.core.VideoDefinition;
import org.linphone.core.tools.Log;
import org.linphone.mediastream.Version;
import org.linphone.purchase.Purchasable;
import org.linphone.utils.LinphoneUtils;

public class LinphonePreferences {
    private static final int LINPHONE_CORE_RANDOM_PORT = -1;
    private static final String LINPHONE_DEFAULT_RC = "/.linphonerc";
    private static final String LINPHONE_FACTORY_RC = "/linphonerc";
    private static final String LINPHONE_LPCONFIG_XSD = "/lpconfig.xsd";
    private static final String DEFAULT_ASSISTANT_RC = "/default_assistant_create.rc";
    private static final String LINPHONE_ASSISTANT_RC = "/linphone_assistant_create.rc";

    private static LinphonePreferences sInstance;

    private Context mContext;
    private String mBasePath;
    // Tunnel settings
    private TunnelConfig mTunnelConfig = null;

    private LinphonePreferences() {}

    public static synchronized LinphonePreferences instance() {
        if (sInstance == null) {
            sInstance = new LinphonePreferences();
        }
        return sInstance;
    }

    public void destroy() {
        mContext = null;
        sInstance = null;
    }

    public void setContext(Context c) {
        mContext = c;
        mBasePath = mContext.getFilesDir().getAbsolutePath();
        try {
            copyAssetsFromPackage();
        } catch (IOException ioe) {

        }
    }

    /* Assets stuff */

    private void copyAssetsFromPackage() throws IOException {
        copyIfNotExist(R.raw.linphonerc_default, getLinphoneDefaultConfig());
        copyFromPackage(R.raw.linphonerc_factory, new File(getLinphoneFactoryConfig()).getName());
        copyIfNotExist(R.raw.lpconfig, mBasePath + LINPHONE_LPCONFIG_XSD);
        copyFromPackage(
                R.raw.default_assistant_create,
                new File(mBasePath + DEFAULT_ASSISTANT_RC).getName());
        copyFromPackage(
                R.raw.linphone_assistant_create,
                new File(mBasePath + LINPHONE_ASSISTANT_RC).getName());
    }

    private void copyIfNotExist(int ressourceId, String target) throws IOException {
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId, lFileToCopy.getName());
        }
    }

    private void copyFromPackage(int ressourceId, String target) throws IOException {
        FileOutputStream lOutputStream = mContext.openFileOutput(target, 0);
        InputStream lInputStream = mContext.getResources().openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while ((readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff, 0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }

    public String getLinphoneDefaultConfig() {
        return mBasePath + LINPHONE_DEFAULT_RC;
    }

    public String getLinphoneFactoryConfig() {
        return mBasePath + LINPHONE_FACTORY_RC;
    }

    public String getDefaultDynamicConfigFile() {
        return mBasePath + DEFAULT_ASSISTANT_RC;
    }

    public String getLinphoneDynamicConfigFile() {
        return mBasePath + LINPHONE_ASSISTANT_RC;
    }

    private String getString(int key) {
        if (mContext == null && LinphoneService.isReady()) {
            mContext = LinphoneService.instance();
        }

        return mContext.getString(key);
    }

    private Core getLc() {
        if (!LinphoneService.isReady()) return null;

        return LinphoneManager.getCore();
    }

    public Config getConfig() {
        Core core = getLc();
        if (core != null) {
            return core.getConfig();
        }

        if (!LinphoneService.isReady()) {
            File linphonerc = new File(mBasePath + "/.linphonerc");
            if (linphonerc.exists()) {
                return Factory.instance().createConfig(linphonerc.getAbsolutePath());
            } else if (mContext != null) {
                InputStream inputStream =
                        mContext.getResources().openRawResource(R.raw.linphonerc_default);
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
            return Factory.instance().createConfig(getLinphoneDefaultConfig());
        }
        return null;
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
        if (ringtone == null || ringtone.length() == 0) ringtone = defaultRingtone;
        return ringtone;
    }

    // Accounts settings
    private ProxyConfig getProxyConfig(int n) {
        if (getLc() == null) return null;
        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        if (n < 0 || n >= prxCfgs.length) return null;
        return prxCfgs[n];
    }

    private AuthInfo getAuthInfo(int n) {
        ProxyConfig prxCfg = getProxyConfig(n);
        if (prxCfg == null) return null;
        Address addr = prxCfg.getIdentityAddress();
        return getLc().findAuthInfo(null, addr.getUsername(), addr.getDomain());
    }

    public String getAccountUsername(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getUsername();
    }

    public String getAccountHa1(int n) {
        AuthInfo authInfo = getAuthInfo(n);
        return authInfo == null ? null : authInfo.getHa1();
    }

    public String getAccountDomain(int n) {
        ProxyConfig proxyConf = getProxyConfig(n);
        return (proxyConf != null) ? proxyConf.getDomain() : "";
    }

    public int getDefaultAccountIndex() {
        if (getLc() == null) return -1;
        ProxyConfig defaultPrxCfg = getLc().getDefaultProxyConfig();
        if (defaultPrxCfg == null) return -1;

        ProxyConfig[] prxCfgs = getLc().getProxyConfigList();
        for (int i = 0; i < prxCfgs.length; i++) {
            if (defaultPrxCfg.getIdentityAddress().equals(prxCfgs[i].getIdentityAddress())) {
                return i;
            }
        }
        return -1;
    }

    public int getAccountCount() {
        if (getLc() == null || getLc().getProxyConfigList() == null) return 0;

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
        if (!enabled
                && getLc().getDefaultProxyConfig()
                        .getIdentityAddress()
                        .equals(prxCfg.getIdentityAddress())) {
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

    private boolean isAccountEnabled(int n) {
        return getProxyConfig(n).registerEnabled();
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

    public float getMicGainDb() {
        return getLc().getMicGainDb();
    }

    public void setMicGainDb(float gain) {
        getLc().setMicGainDb(gain);
    }

    public float getPlaybackGainDb() {
        return getLc().getPlaybackGainDb();
    }

    public void setPlaybackGainDb(float gain) {
        getLc().setPlaybackGainDb(gain);
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
        // Core can only return video size (width and height), not the name
        return getConfig().getString("video", "size", "qvga");
    }

    public void setPreferredVideoSize(String preferredVideoSize) {
        if (getLc() == null) return;
        VideoDefinition preferredVideoDefinition =
                Factory.instance().createVideoDefinitionFromName(preferredVideoSize);
        getLc().setPreferredVideoDefinition(preferredVideoDefinition);
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

    // Contact settings
    public boolean isFriendlistsubscriptionEnabled() {
        if (getConfig().getBool("app", "friendlist_subscription_enabled", false)) {
            // Old setting, do migration
            getConfig().setBool("app", "friendlist_subscription_enabled", false);
            enabledFriendlistSubscription(true);
        }
        return getLc().isFriendListSubscriptionEnabled();
    }

    public void enabledFriendlistSubscription(boolean enabled) {
        getLc().enableFriendListSubscription(enabled);
    }

    public boolean isPresenceStorageInNativeAndroidContactEnabled() {
        return getConfig().getBool("app", "store_presence_in_native_contact", false);
    }

    public void enabledPresenceStorageInNativeAndroidContact(boolean enabled) {
        getConfig().setBool("app", "store_presence_in_native_contact", enabled);
    }

    public boolean isDisplayContactOrganization() {
        return getConfig()
                .getBool(
                        "app",
                        "display_contact_organization",
                        mContext.getResources().getBoolean(R.bool.display_contact_organization));
    }

    public void enabledDisplayContactOrganization(boolean enabled) {
        getConfig().setBool("app", "display_contact_organization", enabled);
    }
    // End of contact settings

    // Call settings
    public boolean acceptMediaEncryptionMandatory() {
        return getLc().isMediaEncryptionMandatory();
    }

    public void setMediaEncryptionMandatory(boolean accept) {
        getLc().setMediaEncryptionMandatory(accept);
    }

    public boolean acceptIncomingEarlyMedia() {
        return getConfig().getBool("sip", "incoming_calls_early_media", false);
    }

    public void setAcceptIncomingEarlyMedia(boolean accept) {
        getConfig().setBool("sip", "incoming_calls_early_media", accept);
    }

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

    public boolean isWifiOnlyEnabled() {
        return getLc().wifiOnlyEnabled();
    }

    // Network settings
    public void setWifiOnlyEnabled(Boolean enable) {
        getLc().enableWifiOnly(enable);
    }

    public void useRandomPort(boolean enabled) {
        useRandomPort(enabled, true);
    }

    private void useRandomPort(boolean enabled, boolean apply) {
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
        if (transports.getUdpPort() > 0) port = transports.getUdpPort();
        else port = transports.getTcpPort();
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

        getLc().setNatPolicy(nat);
    }

    public boolean isIceEnabled() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.iceEnabled();
    }

    public void setIceEnabled(boolean enabled) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.enableIce(enabled);
        nat.enableStun(enabled);
        getLc().setNatPolicy(nat);
    }

    public boolean isTurnEnabled() {
        NatPolicy nat = getOrCreateNatPolicy();
        return nat.turnEnabled();
    }

    public void setTurnEnabled(boolean enabled) {
        if (getLc() == null) return;
        NatPolicy nat = getOrCreateNatPolicy();
        nat.enableTurn(enabled);
        getLc().setNatPolicy(nat);
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
            authInfo =
                    Factory.instance().createAuthInfo(username, username, null, null, null, null);
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
            authInfo =
                    Factory.instance()
                            .createAuthInfo(
                                    nat.getStunServerUsername(),
                                    nat.getStunServerUsername(),
                                    password,
                                    null,
                                    null,
                                    null);
            getLc().addAuthInfo(authInfo);
        }
    }

    public MediaEncryption getMediaEncryption() {
        if (getLc() == null) return null;
        return getLc().getMediaEncryption();
    }

    public void setMediaEncryption(MediaEncryption menc) {
        if (getLc() == null) return;
        if (menc == null) return;

        getLc().setMediaEncryption(menc);
    }

    public boolean isPushNotificationEnabled() {
        return getConfig().getBool("app", "push_notification", true);
    }

    public void setPushNotificationEnabled(boolean enable) {
        getConfig().setBool("app", "push_notification", enable);

        Core core = getLc();
        if (core == null) {
            return;
        }

        if (enable) {
            // Add push infos to exisiting proxy configs
            String regId = getPushNotificationRegistrationID();
            String appId = getString(R.string.gcm_defaultSenderId);
            if (regId != null && core.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : core.getProxyConfigList()) {
                    if (lpc == null) continue;
                    if (!lpc.isPushNotificationAllowed()) {
                        lpc.edit();
                        lpc.setContactUriParameters(null);
                        lpc.done();
                        if (lpc.getIdentityAddress() != null)
                            Log.d(
                                    "[Push Notification] infos removed from proxy config "
                                            + lpc.getIdentityAddress().asStringUriOnly());
                    } else {
                        String contactInfos =
                                "app-id="
                                        + appId
                                        + ";pn-type="
                                        + getString(R.string.push_type)
                                        + ";pn-timeout=0"
                                        + ";pn-tok="
                                        + regId
                                        + ";pn-silent=1";
                        String prevContactParams = lpc.getContactParameters();
                        if (prevContactParams == null
                                || prevContactParams.compareTo(contactInfos) != 0) {
                            lpc.edit();
                            lpc.setContactUriParameters(contactInfos);
                            lpc.done();
                            if (lpc.getIdentityAddress() != null)
                                Log.d(
                                        "[Push Notification] infos added to proxy config "
                                                + lpc.getIdentityAddress().asStringUriOnly());
                        }
                    }
                }
                Log.i(
                        "[Push Notification] Refreshing registers to ensure token is up to date: "
                                + regId);
                core.refreshRegisters();
            }
        } else {
            if (core.getProxyConfigList().length > 0) {
                for (ProxyConfig lpc : core.getProxyConfigList()) {
                    lpc.edit();
                    lpc.setContactUriParameters(null);
                    lpc.done();
                    if (lpc.getIdentityAddress() != null)
                        Log.d(
                                "[Push Notification] infos removed from proxy config "
                                        + lpc.getIdentityAddress().asStringUriOnly());
                }
                core.refreshRegisters();
            }
        }
    }

    private String getPushNotificationRegistrationID() {
        return getConfig().getString("app", "push_notification_regid", null);
    }

    public void setPushNotificationRegistrationID(String regId) {
        if (getConfig() == null) return;
        Log.i("[Push Notification] New token received: " + regId);
        getConfig().setString("app", "push_notification_regid", (regId != null) ? regId : "");
        setPushNotificationEnabled(isPushNotificationEnabled());
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

    public boolean isDebugEnabled() {
        return getConfig().getBool("app", "debug", false);
    }

    // Advanced settings
    public void setDebugEnabled(boolean enabled) {
        getConfig().setBool("app", "debug", enabled);
        LinphoneUtils.configureLoggingService(enabled, mContext.getString(R.string.app_name));
    }

    public void setJavaLogger(boolean enabled) {
        getConfig().setBool("app", "java_logger", enabled);
        LinphoneUtils.configureLoggingService(
                isDebugEnabled(), mContext.getString(R.string.app_name));
    }

    public boolean useJavaLogger() {
        return getConfig().getBool("app", "java_logger", false);
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

    public String getLogCollectionUploadServerUrl() {
        if (getLc() == null) return null;
        return getLc().getLogCollectionUploadServerUrl();
    }

    public void setLogCollectionUploadServerUrl(String url) {
        if (getLc() == null) return;
        getLc().setLogCollectionUploadServerUrl(url);
    }

    public String getRemoteProvisioningUrl() {
        if (getLc() == null) return null;
        return getLc().getProvisioningUri();
    }

    public void setRemoteProvisioningUrl(String url) {
        if (getLc() == null) return;
        if (url != null && url.length() == 0) {
            url = null;
        }
        getLc().setProvisioningUri(url);
    }

    public String getDefaultDisplayName() {
        if (getLc() == null) return null;
        return getLc().createPrimaryContactParsed().getDisplayName();
    }

    public void setDefaultDisplayName(String displayName) {
        if (getLc() == null) return;
        Address primary = getLc().createPrimaryContactParsed();
        primary.setDisplayName(displayName);
        getLc().setPrimaryContact(primary.asString());
    }

    public String getDefaultUsername() {
        if (getLc() == null) return null;
        return getLc().createPrimaryContactParsed().getUsername();
    }

    public void setDefaultUsername(String username) {
        if (getLc() == null) return;
        Address primary = getLc().createPrimaryContactParsed();
        primary.setUsername(username);
        getLc().setPrimaryContact(primary.asString());
    }
    // End of advanced settings

    public TunnelConfig getTunnelConfig() {
        if (getLc() == null) return null;
        if (getLc().tunnelAvailable()) {
            Tunnel tunnel = getLc().getTunnel();
            if (mTunnelConfig == null) {
                TunnelConfig[] servers = tunnel.getServers();
                if (servers.length > 0) {
                    mTunnelConfig = servers[0];
                } else {
                    mTunnelConfig = Factory.instance().createTunnelConfig();
                }
            }
            return mTunnelConfig;
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

    public String getInAppPurchaseValidatingServerUrl() {
        return getConfig().getString("in-app-purchase", "server_url", null);
    }

    public Purchasable getInAppPurchasedItem() {
        String id = getConfig().getString("in-app-purchase", "purchase_item_id", null);
        String payload = getConfig().getString("in-app-purchase", "purchase_item_payload", null);
        String signature =
                getConfig().getString("in-app-purchase", "purchase_item_signature", null);
        String username = getConfig().getString("in-app-purchase", "purchase_item_username", null);

        return new Purchasable(id).setPayloadAndSignature(payload, signature).setUserData(username);
    }

    public void setInAppPurchasedItem(Purchasable item) {
        if (item == null) return;

        getConfig().setString("in-app-purchase", "purchase_item_id", item.getId());
        getConfig().setString("in-app-purchase", "purchase_item_payload", item.getPayload());
        getConfig()
                .setString(
                        "in-app-purchase", "purchase_item_signature", item.getPayloadSignature());
        getConfig().setString("in-app-purchase", "purchase_item_username", item.getUserData());
    }

    public ArrayList<String> getInAppPurchasables() {
        ArrayList<String> purchasables = new ArrayList<>();
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

    public String getInappPopupTime() {
        return getConfig().getString("app", "inapp_popup_time", null);
    }

    public void setInappPopupTime(String date) {
        getConfig().setString("app", "inapp_popup_time", date);
    }

    public String getLinkPopupTime() {
        return getConfig().getString("app", "link_popup_time", null);
    }

    public void setLinkPopupTime(String date) {
        getConfig().setString("app", "link_popup_time", date);
    }

    public boolean isLinkPopupEnabled() {
        return getConfig().getBool("app", "link_popup_enabled", true);
    }

    public void enableLinkPopup(boolean enable) {
        getConfig().setBool("app", "link_popup_enabled", enable);
    }

    public boolean isDNDSettingsPopupEnabled() {
        return getConfig().getBool("app", "dnd_settings_popup_enabled", true);
    }

    public void enableDNDSettingsPopup(boolean enable) {
        getConfig().setBool("app", "dnd_settings_popup_enabled", enable);
    }

    public boolean isLimeSecurityPopupEnabled() {
        return getConfig().getBool("app", "lime_security_popup_enabled", true);
    }

    public void enableLimeSecurityPopup(boolean enable) {
        getConfig().setBool("app", "lime_security_popup_enabled", enable);
    }

    public String getDebugPopupAddress() {
        return getConfig().getString("app", "debug_popup_magic", null);
    }

    public String getActivityToLaunchOnIncomingReceived() {
        return getConfig()
                .getString(
                        "app", "incoming_call_activity", "org.linphone.call.CallIncomingActivity");
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

    public String getCheckReleaseUrl() {
        return getConfig().getString("misc", "version_check_url_root", null);
    }

    public int getLastCheckReleaseTimestamp() {
        return getConfig().getInt("app", "version_check_url_last_timestamp", 0);
    }

    public void setLastCheckReleaseTimestamp(int timestamp) {
        getConfig().setInt("app", "version_check_url_last_timestamp", timestamp);
    }

    public boolean isOverlayEnabled() {
        if (Version.sdkAboveOrEqual(Version.API26_O_80)
                && mContext.getResources().getBoolean(R.bool.allow_pip_while_video_call)) {
            // Disable overlay and use PIP feature
            return false;
        }
        return getConfig().getBool("app", "display_overlay", false);
    }

    public void enableOverlay(boolean enable) {
        getConfig().setBool("app", "display_overlay", enable);
    }

    public boolean isDeviceRingtoneEnabled() {
        int readExternalStorage =
                mContext.getPackageManager()
                        .checkPermission(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                mContext.getPackageName());
        return getConfig().getBool("app", "device_ringtone", true)
                && readExternalStorage == PackageManager.PERMISSION_GRANTED;
    }

    public void enableDeviceRingtone(boolean enable) {
        getConfig().setBool("app", "device_ringtone", enable);
        LinphoneManager.getInstance().enableDeviceRingtone(enable);
    }

    public boolean isIncomingCallVibrationEnabled() {
        return getConfig().getBool("app", "incoming_call_vibration", true);
    }

    public void enableIncomingCallVibration(boolean enable) {
        getConfig().setBool("app", "incoming_call_vibration", enable);
    }

    public boolean isBisFeatureEnabled() {
        return getConfig().getBool("app", "bis_feature", true);
    }

    public boolean isAutoAnswerEnabled() {
        return getConfig().getBool("app", "auto_answer", false);
    }

    public void enableAutoAnswer(boolean enable) {
        getConfig().setBool("app", "auto_answer", enable);
    }

    public int getAutoAnswerTime() {
        return getConfig().getInt("app", "auto_answer_delay", 0);
    }

    public void setAutoAnswerTime(int time) {
        getConfig().setInt("app", "auto_answer_delay", time);
    }

    public void disableFriendsStorage() {
        getConfig().setBool("misc", "store_friends", false);
    }

    public boolean useBasicChatRoomFor1To1() {
        return getConfig().getBool("app", "prefer_basic_chat_room", false);
    }

    // 0 is download all, -1 is disable feature, else size is bytes
    public int getAutoDownloadFileMaxSize() {
        return getLc().getMaxSizeForAutoDownloadIncomingFiles();
    }

    // 0 is download all, -1 is disable feature, else size is bytes
    public void setAutoDownloadFileMaxSize(int size) {
        getLc().setMaxSizeForAutoDownloadIncomingFiles(size);
    }

    public boolean hasPowerSaverDialogBeenPrompted() {
        return getConfig().getBool("app", "android_power_saver_dialog", false);
    }

    public void powerSaverDialogPrompted(boolean b) {
        getConfig().setBool("app", "android_power_saver_dialog", b);
    }

    public boolean isDarkModeEnabled() {
        if (getConfig() == null) return false;
        return getConfig()
                .getBool(
                        "app",
                        "dark_mode",
                        AppCompatDelegate.getDefaultNightMode()
                                == AppCompatDelegate.MODE_NIGHT_YES);
    }

    public void enableDarkMode(boolean enable) {
        getConfig().setBool("app", "dark_mode", enable);
    }

    public String getDeviceName(Context context) {
        String defaultValue = Compatibility.getDeviceName(context);
        return getConfig().getString("app", "device_name", defaultValue);
    }

    public void setDeviceName(String name) {
        getConfig().setString("app", "device_name", name);
    }

    public boolean isEchoCancellationCalibrationDone() {
        return getConfig().getBool("app", "echo_cancellation_calibration_done", false);
    }

    public void setEchoCancellationCalibrationDone(boolean done) {
        getConfig().setBool("app", "echo_cancellation_calibration_done", done);
    }

    public boolean isOpenH264CodecDownloadEnabled() {
        return getConfig().getBool("app", "open_h264_download_enabled", true);
    }

    public void setOpenH264CodecDownloadEnabled(boolean enable) {
        getConfig().setBool("app", "open_h264_download_enabled", enable);
    }

    public boolean isVideoPreviewEnabled() {
        return isVideoEnabled() && getConfig().getBool("app", "video_preview", false);
    }

    public void setVideoPreviewEnabled(boolean enabled) {
        getConfig().setBool("app", "video_preview", enabled);
    }
}
