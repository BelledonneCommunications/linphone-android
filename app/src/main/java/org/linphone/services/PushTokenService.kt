package org.linphone.services

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.linphone.utils.Log

class PushTokenService(val context: Context) {

    private var voipToken: String? = null
    private var token: String? = null

    companion object {
        private const val TAG: String = "org.linphone.services.PushTokenService"

        private val instance: AtomicReference<PushTokenService> = AtomicReference<PushTokenService>()

        fun getInstance(context: Context): PushTokenService {
            var svc = instance.get()
            if (svc == null) {
                svc = PushTokenService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    @SuppressLint("HardwareIds")
    suspend fun getDeviceId(): String {
        return withContext(Dispatchers.IO) {
            Secure.getString(context.contentResolver, Secure.ANDROID_ID)
        }
    }

    val type: String
        get() = "firebase"

    suspend fun getVoipToken(): String {
        if (voipToken == null) {
            voipToken = FirebaseMessaging.getInstance().token.await()

            Log.i("{}: deviceId={}, token={}", "getVoipToken", getDeviceId(), voipToken)
        }

        return voipToken!!
    }

    fun updateVoipToken(newToken: String) {
        voipToken = newToken
    }

    fun updateToken(newToken: String) {
        token = newToken
    }

    suspend fun getToken(): String {
        if (token == null) {
            token = FirebaseMessaging.getInstance().token.await()

            Log.i("{}: deviceId={}, token={}", "getToken", getDeviceId(), token)
        }

        return token!!
    }
}
