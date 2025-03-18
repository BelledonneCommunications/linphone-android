package org.linphone.models

import com.auth0.android.jwt.JWT

class AuthenticatedUser(
    var id: String? = null,
    var name: String? = null,
    var email: String? = null,
    var tenantName: String? = null,
    var tenantTier: String? = null,
    var tenantId: String? = null
) {
    companion object {
        const val UNINTIALIZED_AUTHENTICATEDUSER: String = "NoAuthenticatedUser"
        const val UNINTIALIZED_ACCESS_TOKEN: String = "<null>"

        fun fromToken(jwtToken: String?): AuthenticatedUser {
            if (jwtToken == null || jwtToken == UNINTIALIZED_ACCESS_TOKEN) {
                return AuthenticatedUser(
                    UNINTIALIZED_AUTHENTICATEDUSER
                )
            }

            val jwt = JWT(jwtToken)

            fun getClaim(key: String): String {
                return jwt.getClaim(key).asString() ?: ""
            }

            return AuthenticatedUser(
                id = getClaim("sub"),
                name = getClaim("name"),
                email = getClaim("email"),
                tenantName = getClaim("tenant_name"),
                tenantId = getClaim("tenant_id"),
                tenantTier = getClaim("tenant_tier")
            )
        }
    }
}
