package org.linphone.services

import android.content.Context
import android.media.AudioManager
import android.os.Build
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.interfaces.CTGatewayService
import org.linphone.middleware.FileTree
import org.linphone.utils.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class DiagnosticsService {

    companion object {
        private val LOG_FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

        private fun getGateway(context: Context): CTGatewayService {
            val env = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
                ?: throw NullPointerException("No environment selected.")

            return APIClientService()
                .getUCGatewayService(
                    context,
                    env.gatewayApiUri,
                    AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
                    AuthStateManager.getInstance(context)
                )
        }

        private fun getUploadName(context: Context): String {
            val userId = AuthStateManager.getInstance(context).getUser().id
            val timestamp = LOG_FILE_TIME_FORMAT.format(Date())
            return "${userId}_$timestamp.zip"
        }

        fun clearLogs(context: Context) {
            val logsFolder: String = FileTree.getLogsDirectory(context)
            File(logsFolder).walkTopDown()
                .filter { file -> file.isFile && (file.extension == "log" || file.extension == "json" || file.extension == "zip") }
                .forEach { file -> file.delete() }
        }

        fun uploadDiagnostics(context: Context) {
            try {
                val logsFolder: String = FileTree.getLogsDirectory(context)

                writeDiagnosticsFile(context)

                val zipFile = FileTree.zipAll(logsFolder)

                Log.i("Uploading logs from file ${zipFile.path}")

                // Flush current log files
                Timber.forest().forEach { t -> if (t is FileTree) t.flush() }

                val body = zipFile
                    .readBytes()
                    .toRequestBody(
                        "application/octet".toMediaTypeOrNull(),
                        0,
                        zipFile.length().toInt()
                    )

                val uploadName = getUploadName(context)

                getGateway(context).doPostClientDiagnostics(uploadName, body)
                    .enqueue(object : Callback<ResponseBody> {
                        override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                            Log.e(t, "Failed to upload logs")
                        }

                        override fun onResponse(
                            call: Call<ResponseBody>,
                            response: Response<ResponseBody>
                        ) {
                            val code = response.code()
                            if (code < 200 || code > 299) {
                                Log.e("Failed to upload logs: ${response.errorBody()}")
                            } else {
                                Log.i("Logs uploaded!")
                            }
                        }
                    })
            } catch (ex: Exception) {
                Log.e(ex, "Error uploading logs")
            }
        }

        private fun writeDiagnosticsFile(context: Context) {
            val info = JSONObject()
            info.put("productName", "Dimensions Connect Mobile")

            info.put("user", getUserInfo(context))
            info.put(
                "environment",
                DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()?.name
            )
            info.put("localTime", Date().toString())

            info.put("timeZone", TimeZone.getDefault().displayName)
            info.put("culture", Locale.getDefault().displayName)
            info.put("sysInfo", getSystemInfo())
            info.put("audioDevices", getAudioDevices(context))

            // Write the JSON to the logs folder
            val logsFolder: String = FileTree.getLogsDirectory(context)
            val diagnosticInfoFile = File("$logsFolder/diagnostic-info.json")
            val output: Writer = BufferedWriter(FileWriter(diagnosticInfoFile, false))
            output.write(info.toString(3))
            output.close()
        }
        private fun getUserInfo(context: Context): JSONObject {
            val user = AuthStateManager.getInstance(context).getUser()

            val jObj = JSONObject()

            if (user != null) {
                jObj.put("name", user.name)
                jObj.put("id", user.id)
                jObj.put("tenantId", user.tenantId)
                jObj.put("tenantName", user.tenantName)
                jObj.put("tenantTier", user.tenantTier)
            }

            return jObj
        }

        private fun getSystemInfo(): JSONObject {
            val jObj = JSONObject()
            jObj.put("manufacturer", Build.MANUFACTURER)
            jObj.put("product", Build.PRODUCT)
            jObj.put("model", Build.MODEL)
            jObj.put("version", System.getProperty("os.version"))
            jObj.put("sdk_version", Build.VERSION.SDK)

            val fields = Build.VERSION_CODES::class.java.fields
            var osCode = "UNKNOWN"
            fields.filter { it.getInt(Build.VERSION_CODES::class) == Build.VERSION.SDK_INT }
                .forEach { osCode = it.name }

            jObj.put("OS", "Android $osCode")

            return jObj
        }

        private fun getAudioDevices(context: Context): JSONArray {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            return JSONArray(audioDevices.toList())
        }
    }
}
