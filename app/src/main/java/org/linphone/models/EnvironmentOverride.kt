package org.linphone.models

import androidx.annotation.Keep

@Keep
data class EnvironmentOverride(
    var id: String,
    var name: String?,
    var isDefault: Boolean = false,
    var isHidden: Boolean = false,
    var defaultTenantId: String?,
    var documentationUri: String?
)
