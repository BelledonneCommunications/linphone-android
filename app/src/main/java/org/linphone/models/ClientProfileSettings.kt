package org.linphone.models

import com.google.gson.annotations.SerializedName

data class ClientProfileSettings(
    @SerializedName("presenceSelectionEnabled")
    var presenceSelectionEnabled: Boolean,

    @SerializedName("presenceOverrideEnabled")
    var presenceOverrideEnabled: Boolean,

    @SerializedName("queueControlEnabled")
    var queueControlEnabled: Boolean,

    @SerializedName("agentControlDisplayed")
    var agentControlDisplayed: Boolean
)
