package org.linphone.services.realtime

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.time.OffsetDateTime

data class UserEvent<T> (
    @SerializedName("id")
    val id: String = "",
    @SerializedName("tenantId")
    val tenantId: String = "",
    @SerializedName("userId")
    val userId: String = "",
    @SerializedName("timeStamp")
    val timeStamp: OffsetDateTime,
    @SerializedName("data")
    val data: T
) {
    fun ToString(): String {
        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        return gson.toJson(this)
    }
}
