package org.linphone.models

import java.util.Hashtable

class TenantBrandingDefinition {
    var brandName: String? = null

    var identityPortal: PortalBrandingDefinition? = null
    var systemPortal: PortalBrandingDefinition? = null
    var enterprisePortal: PortalBrandingDefinition? = null
    var accountPortal: PortalBrandingDefinition? = null
    var customerPortal: PortalBrandingDefinition? = null
    var documentationRootUrl: String? = null

    var cssVariables: Hashtable<String, String> = Hashtable<String, String>()
    var emailTemplatePlainText: String? = null
    var emailTemplateHtml: String? = null
    var emailSenderAddress: String? = null
}

class PortalBrandingDefinition {
    var name: String? = null
    var logoImageUrl: String? = null
    var favIconUrl: String? = null
    var clientStartupScript: String? = null
}
