package org.linphone.middleware

import java.util.UUID
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

    companion object {
        var isRefreshingToken = false
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestUid = UUID.randomUUID()

        val future = CompletableFuture<Request?>()

        val authState = authManager.current

        if (authState.needsTokenRefresh) {
            Log.i("authenticate($requestUid) - AUTH needs refresh")
            isRefreshingToken = true
        }

        Log.i("authenticate($requestUid) - REQUEST: " + response.request.url)
        Log.i("authenticate($requestUid) - AUTH Using refresh token: " + authState.refreshToken)
        Log.i("authenticate($requestUid) - AUTH ID token: " + authState.idToken)
        Log.i(
            "authenticate($requestUid) - AUTH access token: " + authState.accessToken + " exp:" + authState.accessTokenExpirationTime
        )

        authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex != null) {
                Log.e("authenticate($requestUid) - Failed to authorize: $ex")
            }

            Log.i(
                "authenticate($requestUid) - AUTH performActionWithFreshTokens $accessToken, isRefreshingToken= $isRefreshingToken"
            )

            if (isRefreshingToken) {
                isRefreshingToken = false
                authManager.replace(authState, "performActionWithFreshTokens")
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
