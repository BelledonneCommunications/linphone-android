package org.linphone.api

import com.hansol.siphone.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.linphone.core.tools.Log

interface NotificationApiService {
    suspend fun registerDevice(username: String, deviceToken: String)
}

@Serializable
private data class RegisterDeviceRequest(
    val username: String,
    val deviceToken: String,
    val platform: String
)

class NotificationApiServiceImpl(private val httpClient: HttpClient) : NotificationApiService {
    companion object {
        private const val TAG = "[NotificationApiService]"
    }

    override suspend fun registerDevice(username: String, deviceToken: String) {
        try {
            httpClient.post("${BuildConfig.NOTIFICATION_API_BASE_URL}/api/notifications/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterDeviceRequest(username, deviceToken, "android"))
            }
            Log.i("$TAG Device registered successfully for user [$username]")
        } catch (e: Exception) {
            Log.e("$TAG Failed to register device for user [$username]: ${e.message}")
        }
    }
}
