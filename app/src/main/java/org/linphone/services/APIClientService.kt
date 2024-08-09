package org.linphone.services

import android.content.Context
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import okhttp3.OkHttpClient
import org.linphone.authentication.AuthStateManager
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.AuthAuthenticator
import org.linphone.middleware.AuthInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class APIClientService {
    private lateinit var ctGatewayService: CTGatewayService

    fun getUCGatewayService(
        context: Context,
        baseUrl: String,
        authService: AuthorizationService,
        asm: AuthStateManager
    ): CTGatewayService {
        if (!::ctGatewayService.isInitialized) {
            ctGatewayService = getRetrofit(context, baseUrl, authService, asm.current).create(
                CTGatewayService::class.java
            )
        }

        return ctGatewayService
    }

    private fun getRetrofit(
        context: Context,
        baseUrl: String,
        authService: AuthorizationService,
        authState: AuthState
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(context, authService, authState))
            .build()
    }

    private fun getOkHttpClient(
        context: Context,
        authService: AuthorizationService,
        authState: AuthState
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator(AuthAuthenticator(authService, authState))
            .addInterceptor(AuthInterceptor(context))
            .build()
    }
}
