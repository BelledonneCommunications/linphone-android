package org.linphone.interfaces

import okhttp3.RequestBody
import org.linphone.models.TenantBrandingDefinition
import org.linphone.models.UserDevice
import org.linphone.models.UserInfo
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CTGatewayService {
    @GET("api/v1.0/users/{userID}/devices?manufacturer=Softphones&model=UCM&model=KZSM")
    fun doGetUserDevices(
        @Path("userID") userID: String?
    ): Call<List<UserDevice>>

    @GET("api/v1.0/users/me")
    suspend fun getUserInfo(): Response<UserInfo>

    @GET("api/v1.0/users/me/branding")
    fun doGetUserBranding(): Call<TenantBrandingDefinition>

    @POST("api/v1.0/clientdiagnostics/{fileName}")
    suspend fun postClientDiagnostics(
        @Path("fileName") fileName: String,
        @Body body: RequestBody
    ): Response<Void>
}