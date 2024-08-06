package org.linphone.interfaces

import org.linphone.models.UserDevice
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface CTGatewayService {
    @GET("api/v1.0/users/{userID}/devices?manufacturer=Softphones&model=UCM&model=KZSM")
    fun doGetUserDevices(
        @Path("userID") userID: String?
    ): Call<List<UserDevice>>
}
