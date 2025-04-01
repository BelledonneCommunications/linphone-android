package org.linphone.services

import ZonedDateTimeAdapter
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.Date
import net.openid.appauth.AuthorizationService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.AuthAuthenticator
import org.linphone.typeadapters.BooleanTypeAdapter
import org.linphone.typeadapters.DateTypeAdapter
import org.threeten.bp.ZonedDateTime
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class APIClientService(val context: Context) {

    private lateinit var ctGatewayService: CTGatewayService
    private lateinit var baseUrl: String

    fun getUCGatewayService(): CTGatewayService {
        val dimensionsEnvironment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
        val asm = AuthStateManager.getInstance(context)
        val auth = AuthorizationServiceManager.getInstance(context).getAuthorizationServiceInstance()

        if (!::ctGatewayService.isInitialized || dimensionsEnvironment!!.gatewayApiUri != this.baseUrl) {
            baseUrl = dimensionsEnvironment!!.gatewayApiUri

            ctGatewayService = getRetrofit(dimensionsEnvironment!!.gatewayApiUri, auth, asm).create(
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
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Boolean::class.java, BooleanTypeAdapter()) // Register the adapter
            .registerTypeAdapter(Date::class.java, DateTypeAdapter())
            .registerTypeAdapter(ZonedDateTime::class.java, ZonedDateTimeAdapter())
            .create()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
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
