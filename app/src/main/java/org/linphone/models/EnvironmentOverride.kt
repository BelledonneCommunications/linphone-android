package org.linphone.models

data class EnvironmentOverride(
    var id: String,
    var name: String?,
    var isDefault: Boolean = false,
    var isHidden: Boolean = false,
    var defaultTenantId: String?
)
