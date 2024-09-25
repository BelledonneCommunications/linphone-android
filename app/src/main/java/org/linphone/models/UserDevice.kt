package org.linphone.models

import com.google.gson.annotations.SerializedName

data class SubscribableUserDeviceList(val userDevices: List<UserDevice>?) {
    var hasValue = userDevices != null
}

data class UserDevice(
    @SerializedName("deviceId")
    var deviceId: String,

    @SerializedName("pbxId")
    var pbxId: String,

    @SerializedName("sipUsername")
    var sipUsername: String,

    @SerializedName("sipPassword")
    var sipUserPassword: String,

    @SerializedName("sipRealm")
    var sipRealm: String,

    @SerializedName("sipPort")
    var sipPort: Int,

    @SerializedName("sipOutboundProxy")
    var sipOutboundProxy: String,

    @SerializedName("remoteSipHost")
    var remoteSipHost: String,

    @SerializedName("sipOutboundProxyPort")
    var sipOutboundProxyPort: Int,

    @SerializedName("sipTransport")
    var sipTransport: String,

    @SerializedName("sipRegisterTimeout")
    var sipRegisterTimeout: Int = 3600,

    @SerializedName("primaryDns")
    var primaryDns: String,

    @SerializedName("secondaryDns")
    var secondaryDns: String,

    @SerializedName("callWaitingEnabled")
    var callWaitingEnabled: Boolean? = null,

    @SerializedName("audioCodecs")
    var audioCodecs: Map<String, String>? = null,

    @SerializedName("videoCodecs")
    var videoCodecs: Map<String, String>? = null
) {

    fun hasCredentials(): Boolean {
        return sipUsername.isNotBlank() &&
            sipUserPassword.isNotBlank() &&
            (sipRealm.isNotBlank() || sipOutboundProxy.isNotBlank())
    }
}
