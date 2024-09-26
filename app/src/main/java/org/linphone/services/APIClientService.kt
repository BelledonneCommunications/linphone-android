package org.linphone.services

import android.content.Context
import net.openid.appauth.AuthorizationService
import okhttp3.OkHttpClient
import org.linphone.authentication.AuthStateManager
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.AuthAuthenticator
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
            ctGatewayService = getRetrofit(context, baseUrl, authService, asm).create(
                CTGatewayService::class.java
            )
        }

        return ctGatewayService
    }

    private fun getRetrofit(
        context: Context,
        baseUrl: String,
        authService: AuthorizationService,
        asm: AuthStateManager
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(context, authService, asm))
            .build()
    }

    private fun getOkHttpClient(
        context: Context,
        authService: AuthorizationService,
        asm: AuthStateManager
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator(AuthAuthenticator(authService, asm))
            .build()
    }
}
