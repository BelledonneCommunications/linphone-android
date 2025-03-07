package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

data class PresenceProfile(
    @SerializedName("id")
    var id: String = "",

    @SerializedName("name")
    var name: String = "",

    @SerializedName("isAvailable")
    var isAvailable: Boolean = false,

    @SerializedName("message")
    var message: String = "",

    @SerializedName("dnd")
    var dnd: Any? = null,

    @SerializedName("forward")
    var forward: UserForwardStateOn,

    @SerializedName("callRouting")
    var callRouting: Any? = null,

    @SerializedName("acd")
    var acd: Any? = null,

    @SerializedName("enablePersonalRoutingGroup")
    var enablePersonalRoutingGroup: Boolean = false,

    @SerializedName("hideFromSelection")
    var hideFromSelection: Boolean = false
) {
    override fun toString(): String {
        return name
    }
}
