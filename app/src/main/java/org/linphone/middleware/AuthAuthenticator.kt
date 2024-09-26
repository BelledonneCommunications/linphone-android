package org.linphone.middleware

import java8.util.concurrent.CompletableFuture
import net.openid.appauth.AuthorizationService
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.linphone.authentication.AuthStateManager
import org.linphone.utils.Log

class AuthAuthenticator(
    private val authService: AuthorizationService,
    private val authManager: AuthStateManager
) :
    Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val future = CompletableFuture<Request?>()

        val authState = authManager.current
        Log.i("AUTH Using refresh token: " + authState.refreshToken)
        Log.i("AUTH ID token: " + authState.idToken)
        Log.i(
            "AUTH access token: " + authState.accessToken + " exp:" + authState.accessTokenExpirationTime
        )

        authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) {
                Log.e("Failed to authorize: $ex")
            }

            if (response.request.header("Authorization") != null) {
                future.complete(null) // Give up, we've already failed to authenticate.
            }

            val responseWithAuth = response.request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()

            future.complete(responseWithAuth)
        }

        return future.get()
    }
}
