package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import okhttp3.ResponseBody
import org.linphone.models.callhistory.CallRecordingInfo
import org.linphone.utils.Log

class RecordingsService(val context: Context) : DefaultLifecycleObserver {

    private val apiClient = APIClientService(context)

    private var cacheDirPath: String = context.cacheDir.path + File.pathSeparator + "recordings"

    init {
        // Create the cache folder, deleting any existing files if it already exists
        // TODO: Implement a better mechanism for clearing cached files
        val cacheDir = File(cacheDirPath)
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        cacheDir.mkdir()
    }

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

    suspend fun getRecordingAudio(sessionId: String, recordingId: String): File {
        // Check if the recording is already cached
        val cachePath = getRecordingCachePath(sessionId, recordingId)

        val file = File(cachePath)

        if (!file.exists()) {
            // Otherwise, fetch from the server
            val response = apiClient.getUCGatewayService().getRecordingAudio(sessionId, recordingId)

            // Save the recording locally
            writeToCacheFile(response, file)
        }

        return file
    }

    private fun getRecordingCachePath(sessionId: String, recordingId: String): String {
        val cachePath = context.cacheDir.path + File.pathSeparator + "recordings"

        return "$cachePath/${sessionId}_$recordingId.mp3"
    }
    private fun writeToCacheFile(body: ResponseBody, file: File) {
        try {
            file.createNewFile()

            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(e, "Failed to save recording to cache.")
            if (file.exists()) file.delete()
            throw e
        }
    }
}
