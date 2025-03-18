package org.linphone.services

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.rx3.rxSingle
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
import org.linphone.models.UserInfo
import org.linphone.utils.Log

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

    val user: Observable<UserInfo>
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
