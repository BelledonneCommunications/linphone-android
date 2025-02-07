package org.linphone.services

import net.openid.appauth.AuthorizationService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.linphone.authentication.AuthStateManager
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.AuthAuthenticator
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class APIClientService {
    private lateinit var ctGatewayService: CTGatewayService

    fun getUCGatewayService(
        baseUrl: String,
        authService: AuthorizationService,
        asm: AuthStateManager
    ): CTGatewayService {
        if (!::ctGatewayService.isInitialized) {
            ctGatewayService = getRetrofit(baseUrl, authService, asm).create(
                CTGatewayService::class.java
            )
        }

        return ctGatewayService
    }

    private fun getRetrofit(
        baseUrl: String,
        authService: AuthorizationService,
        asm: AuthStateManager
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(authService, asm))
            .build()
    }

    private fun getOkHttpClient(
        authService: AuthorizationService,
        asm: AuthStateManager
    ): OkHttpClient {
        // TODO its recommended to remove the HttpLoggingInterceptor from release code, but the
        // details it's exposing are the same as we currently expose in the webRTC code which is
        // far easier to intercept - need to have a discussion on this
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .authenticator(AuthAuthenticator(authService, asm))
            .build()
    }
}
