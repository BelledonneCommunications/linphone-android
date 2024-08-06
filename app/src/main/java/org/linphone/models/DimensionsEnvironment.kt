package org.linphone.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class DimensionsEnvironment(

    @SerializedName("Id")
    var id: String,

    @SerializedName("Name")
    var name: String,

    @SerializedName("IsDefault")
    var isDefault: Boolean,

    @SerializedName("IsHidden")
    var isHidden: Boolean,

    @SerializedName("IdentityServerUri")
    var identityServerUri: String,

    @SerializedName("GatewayApiUri")
    var gatewayApiUri: String,

    @SerializedName("RealtimeApiUri")
    var realtimeApiUri: String,

    @SerializedName("DocumentationUri")
    var documentationUri: String,

    @SerializedName("DiagnosticsBlobConnectionString")
    var diagnosticsBlobConnectionString: String,

    @SerializedName("ResourcesBlobUrl")
    var resourcesBlobUrl: String
) {
    companion object {
        public fun jsonDeserialize(jsonStr: String): DimensionsEnvironment {
            val dimensionsEnvironmentType = object : TypeToken<DimensionsEnvironment>() {}.type

            return Gson().fromJson(jsonStr, dimensionsEnvironmentType)
        }

        public fun jsonSerialize(dimensionsEnvironment: DimensionsEnvironment): String {
            return Gson().toJson(dimensionsEnvironment)
        }
    }
}
