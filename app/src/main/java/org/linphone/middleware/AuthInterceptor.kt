package org.linphone.middleware

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import org.linphone.authentication.AuthStateManager

class AuthInterceptor(context: Context) : Interceptor {
    private val asm = AuthStateManager.getInstance(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        requestBuilder.addHeader("Accept", "application/json")
        requestBuilder.addHeader("Authorization", "Bearer ${asm.current.accessToken}")

        return chain.proceed(requestBuilder.build())
    }
}
