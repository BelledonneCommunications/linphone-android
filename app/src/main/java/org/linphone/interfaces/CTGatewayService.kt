package org.linphone.interfaces

import ReportResult
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.linphone.models.TenantBrandingDefinition
import org.linphone.models.UserDevice
import org.linphone.models.UserInfo
import org.linphone.models.UserSession
import org.linphone.models.callhistory.CallRecordingInfo
import org.linphone.models.callhistory.ReportRequest
import org.linphone.models.callhistory.UserCallHistorySummary
import org.linphone.models.contact.ContactDirectoryModel
import org.linphone.models.contact.ContactGroupItem
import org.linphone.models.contact.ContactItemModel
import org.linphone.models.realtime.PresenceProfile
import org.linphone.models.realtime.SetPresenceModel
import org.linphone.models.usergroup.UserGroupModel
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CTGatewayService {
    @GET("api/v1.0/users/{userID}/devices?manufacturer=Softphones&model=UCM&model=KZSM")
    fun doGetUserDevices(
        @Path("userID") userID: String?
    ): Call<List<UserDevice>>

    @GET("api/v1.0/users/me")
    suspend fun getUserInfo(): Response<UserInfo>

    @GET("api/v1.0/users")
    fun doGetAllUsers(): Call<List<UserInfo>>

    @GET("api/v1.0/users/me/branding")
    fun doGetUserBranding(): Call<TenantBrandingDefinition>

    @POST("api/v1.0/clientdiagnostics/{fileName}")
    suspend fun postClientDiagnostics(
        @Path("fileName") fileName: String,
        @Body body: RequestBody
    ): Response<Void>

    @GET("api/v1.0/contactdirectories/users/me")
    fun doGetContactDirectories(): Call<List<ContactDirectoryModel>>

    @GET("api/v1.0/contactdirectories/{directoryId}/items")
    fun searchDirectory(
        @Path("directoryId") directoryId: String,
        @Query("filter") filter: String,
        @Query("maxItems") maxItems: Int = 100
    ): Call<List<ContactItemModel>>

    @GET("api/v1.0/personalusergroups?includeContacts=true")
    fun doGetPersonalUserGroups(): Call<List<UserGroupModel>>

    @GET("api/v1.0/tenantusergroups")
    fun doGetTenantUserGroups(): Call<List<UserGroupModel>>

    @PUT("api/v1.0/personalusergroups/{directoryId}/users")
    fun doAddUserToUserDirectory(
        @Path("directoryId") directoryId: String,
        @Body userIds: Array<String>
    ): Call<Void>

    @DELETE("api/v1.0/personalusergroups/{directoryId}/users/{userId}")
    fun doRemoveUserFromDirectory(
        @Path("directoryId") directoryId: String,
        @Path("userId") userId: String
    ): Call<Void>

    @PUT("api/v1.0/personalusergroups/{directoryId}/contacts")
    fun doAddContactToDirectory(
        @Path("directoryId") directoryId: String,
        @Body contactGroupItem: ContactGroupItem
    ): Call<Void>

    @DELETE("api/v1.0/personalusergroups/{id}/contacts/{contactId}")
    fun doRemoveContactFromDirectory(
        @Path("id") directoryId: String,
        @Path("contactId") contactId: String
    ): Call<Void>

    @GET("api/v1.0/users/{userId}/presenceprofiles")
    fun doGetPresenceProfiles(
        @Path("userId") userId: String
    ): Call<List<PresenceProfile>>

    @PUT("api/v1.0/users/{userId}/currentpresence")
    fun doSetPresence(
        @Path("userId") userId: String,
        @Body setPresenceModel: SetPresenceModel
    ): Call<Void>

    @GET("api/v1.0/usercallhistory/summary")
    suspend fun doGetMissedCallDate(): Response<UserCallHistorySummary>

    @PUT("api/v1.0/usercallhistory/summary")
    fun doSetMissedCallDate(@Query("dateTime") dateTime: String): Call<Void>

    @POST("api/v1.0/usercallhistory/report")
    suspend fun postReportRequest(@Body request: Map<String, String?>): ReportRequest

    @GET("api/v1.0/usercallhistory/report")
    suspend fun getReportResult(@Query("requestId") requestId: String): Response<ReportResult>

    @POST("api/v1.0/usersession")
    suspend fun postUserSession(@Body request: UserSession): Response<Void>

    @HTTP(method = "DELETE", path = "api/v1.0/usersession/{deviceId}", hasBody = true)
    suspend fun deleteUserSession(
        @Path("deviceId") deviceId: String,
        @Body request: UserSession
    ): Response<Void>

    @GET("api/v1.0/callsessions/{sessionId}/recordings")
    suspend fun getRecordingInfoList(@Path("sessionId") sessionId: String): Response<List<CallRecordingInfo>>

    @POST("api/v1.0/callsessions/{sessionId}/recordings/audio")
    suspend fun getRecordingAudio(@Path("sessionId") sessionId: String, @Body recordingId: String): ResponseBody
}
