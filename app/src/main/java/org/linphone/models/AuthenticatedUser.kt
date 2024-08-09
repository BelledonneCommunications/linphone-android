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
        fun fromToken(jwtToken: String?): AuthenticatedUser {
            if (jwtToken == null) return AuthenticatedUser()

            val jwt = JWT(jwtToken)

            fun getClaim(key: String): String {
                val value = jwt.getClaim(key).asString()
                return if (value == null) {
                    ""
                } else {
                    value
                }
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
