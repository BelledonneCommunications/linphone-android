package org.linphone.models

import com.google.gson.annotations.SerializedName

data class UserSession(

    @SerializedName("sessionId")
    var sessionId: String,
    @SerializedName("client")
    var client: String,
    @SerializedName("clientVersion")
    var clientVersion: String,

    @SerializedName("region")
    var region: String,

    @SerializedName("deviceId")
    var deviceId: String,
    @SerializedName("deviceName")
    var deviceName: String,
    @SerializedName("devicePlatform")
    var devicePlatform: String,
    @SerializedName("deviceManufacturer")
    var deviceManufacturer: String,
    @SerializedName("deviceModel")
    var deviceModel: String,
    @SerializedName("deviceVersion")
    var deviceVersion: String,
    @SerializedName("deviceToken")
    var deviceToken: String
)
