package org.linphone.services

import android.content.Context
import okhttp3.OkHttpClient
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.AuthInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class APIClientService {
    private lateinit var ctGatewayService: CTGatewayService

    fun getUCGatewayService(context: Context, baseUrl: String): CTGatewayService {
        if (!::ctGatewayService.isInitialized) {
            ctGatewayService = getRetrofit(context, baseUrl).create(CTGatewayService::class.java)
        }

        return ctGatewayService
    }

    private fun getRetrofit(context: Context, baseUrl: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(getOkHttpClient(context))
            .build()
    }

    private fun getOkHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(context))
            .build()
    }
}
