package org.linphone.models

import com.google.gson.annotations.SerializedName

data class ClientProfileSettings(
    @SerializedName("presenceSelectionEnabled")
    var presenceSelectionEnabled: Boolean = false,

    @SerializedName("presenceOverrideEnabled")
    var presenceOverrideEnabled: Boolean = false,

    @SerializedName("queueControlEnabled")
    var queueControlEnabled: Boolean = false,

    @SerializedName("agentControlDisplayed")
    var agentControlDisplayed: Boolean = false
)
