package org.linphone.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.rxSingle
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.UserInfo
import org.linphone.models.UserSession
import org.linphone.utils.Log

@SuppressLint("CheckResult")
class UserService(val context: Context) : DefaultLifecycleObserver {

    companion object {
        private val instance: AtomicReference<UserService> = AtomicReference<UserService>()

        fun getInstance(context: Context): UserService {
            var svc = instance.get()
            if (svc == null) {
                svc = UserService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    val pushTokenService = PushTokenService.getInstance(context)

    val user: Observable<UserInfo>
    var userSession: UserSession? = null

    var e911Accepted: Boolean = false

    var userSubscription: Disposable? = null

    init {
        Log.i("Created UserService")

        val asm = AuthStateManager.getInstance(context)
        val state: SharedPreferences = context.getSharedPreferences(
            UserInfo.STORE_NAME,
            Context.MODE_PRIVATE
        )

        e911Accepted = state.getBoolean(UserInfo.E911_STATE, false)

        user = asm.user
            .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
            .distinctUntilChanged { u -> u.id ?: "" }
            .switchMapSingle {
                rxSingle {
                    getUserInfo()
                }
            }
            .onErrorReturn { e ->
                Log.e(e.localizedMessage)
                UserInfo()
            }
            .replay(1)
            .autoConnect()

        // FixMe 20250411 - Bewarned weary traveller, nothing good will come of re-enabling this code before
        // we have a fix for for the duplicate message issue.

//        user.subscribe { u ->
//            CoroutineScope(Dispatchers.IO).launch {
//                createUserSession()
//            }
//        }
    }

    private suspend fun createUserSession() {
        if (userSession == null) {
            try {
                val dimensionsEnvironment =
                    DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
                if (dimensionsEnvironment != null) {
                    val deviceId = pushTokenService.getDeviceId()

                    val newUserSession = UserSession(
                        deviceId,
                        "plummobile",
                        Build.VERSION.RELEASE,
                        dimensionsEnvironment.name,
                        deviceId,
                        "${Build.MANUFACTURER} ${Build.MODEL}",
                        "Android",
                        Build.MANUFACTURER,
                        Build.MODEL,
                        Build.VERSION.BASE_OS,
                        pushTokenService.getToken()
                    )

                    val response = APIClientService(context).getUCGatewayService().postUserSession(
                        newUserSession
                    )

                    if (response.isSuccessful) {
                        userSession = newUserSession
                    } else {
                        throw Exception("Unable to create new session: Error(${response.code()})")
                    }
                }
            } catch (e: Exception) {
                Log.e("getUserSession", e)
                userSession = null
            }
        }
    }

    fun removeUserSession() {
        runBlocking {
            try {
                val userSessionToRemove = userSession
                if (userSessionToRemove != null) {
                    val deviceId = pushTokenService.getDeviceId()

                    val response = APIClientService(context).getUCGatewayService().deleteUserSession(
                        deviceId,
                        userSessionToRemove
                    )

                    if (!response.isSuccessful) {
                        throw Exception(
                            "Unable to delete session $deviceId: Error(${response.code()})"
                        )
                    }

                    userSession = null
                }
            } catch (e: Exception) {
                Log.e("removeUserSession", e)
            }
        }
    }

    private suspend fun getUserInfo(): UserInfo {
        Log.i("Fetching user info....")

        val response = APIClientService(context).getUCGatewayService().getUserInfo()

        if (response.code() < 200 || response.code() > 299) {
            throw Exception("Error fetching user info: " + response.message())
        }

        return response.body()!!
    }

    fun updateE911Accepted(accepted: Boolean) {
        e911Accepted = accepted

        val state: SharedPreferences = context.getSharedPreferences(
            UserInfo.STORE_NAME,
            Context.MODE_PRIVATE
        )
        state.edit(true) {
            putBoolean(UserInfo.E911_STATE, e911Accepted)
        }
    }
}
