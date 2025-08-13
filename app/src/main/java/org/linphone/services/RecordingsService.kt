package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import java.util.concurrent.atomic.AtomicReference
import org.linphone.models.callhistory.CallRecordingInfo

class RecordingsService(val context: Context) : DefaultLifecycleObserver {

    private val apiClient = APIClientService(context)

    companion object {
        private val instance: AtomicReference<RecordingsService> = AtomicReference<RecordingsService>()

        fun getInstance(context: Context): RecordingsService {
            var svc = instance.get()
            if (svc == null) {
                svc = RecordingsService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    suspend fun getRecordingInfoList(sessionId: String): List<CallRecordingInfo> {
        val response = apiClient.getUCGatewayService().getRecordingInfoList(sessionId)
        if (response.code() < 200 || response.code() > 299) {
            throw Exception("Error fetching recording info: " + response.message())
        }

        return response.body()!!
    }
}
