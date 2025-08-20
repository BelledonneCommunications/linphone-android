package org.linphone.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.Hashtable

@Keep
class TenantBrandingDefinition {
    @SerializedName("brandName")
    var brandName: String? = null

    @SerializedName("identityPortal")
    var identityPortal: PortalBrandingDefinition? = null

    @SerializedName("systemPortal")
    var systemPortal: PortalBrandingDefinition? = null

    @SerializedName("enterprisePortal")
    var enterprisePortal: PortalBrandingDefinition? = null

    @SerializedName("accountPortal")
    var accountPortal: PortalBrandingDefinition? = null

    @SerializedName("customerPortal")
    var customerPortal: PortalBrandingDefinition? = null

    @SerializedName("documentationRootUrl")
    var documentationRootUrl: String? = null

    @SerializedName("cssVariables")
    var cssVariables: Hashtable<String, String> = Hashtable<String, String>()
}

@Keep
class PortalBrandingDefinition {
    @SerializedName("name")
    var name: String? = null

    @SerializedName("logoImageUrl")
    var logoImageUrl: String? = null

    @SerializedName("favIconUrl")
    var favIconUrl: String? = null

    @SerializedName("clientStartupScript")
    var clientStartupScript: String? = null
}
