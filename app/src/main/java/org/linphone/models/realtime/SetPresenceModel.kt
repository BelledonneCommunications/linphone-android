package org.linphone.models.realtime

import com.google.gson.annotations.SerializedName

data class SetPresenceModel(
    @SerializedName("profileId")
    var profileId: String = "",

    @SerializedName("message")
    var message: String = "",

    @SerializedName("dnd")
    var dnd: Any? = null,

    @SerializedName("forward")
    var forward: UserForwardStateOn? = null,

    @SerializedName("routing")
    var routing: Any? = null,

    @SerializedName("acd")
    var acd: Any? = null,

    @SerializedName("enablePersonalRoutingGroup")
    var enablePersonalRoutingGroup: Boolean = false,

    @SerializedName("hideFromSelection")
    var hideFromSelection: Boolean = false
) {
    companion object {
        fun equals(x: SetPresenceModel?, y: SetPresenceModel?): Boolean {
            if (x == null || y == null) return x == y

            return x.profileId == y.profileId &&
                x.message == y.message &&
                x.forward?.destination == y.forward?.destination
        }
    }
}
