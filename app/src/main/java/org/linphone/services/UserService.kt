package org.linphone.services

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.rx3.rxSingle
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.UserInfo
import org.linphone.utils.Log

class UserService public constructor(context: Context) {

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
    // = ReplaySubject.create(1)

    init {
        Log.i("Created UserService")

        val asm = AuthStateManager.getInstance(context)
        val apiClientService = APIClientService()
        val dimensionsEnvironment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
        val ucGatewayService = apiClientService.getUCGatewayService(
            context,
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).getAuthorizationServiceInstance(),
            asm
        )

        user = asm.user
            .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
            .doOnNext { Log.i("Fetching user info....") }
            .switchMapSingle {
                rxSingle { ucGatewayService.getUserInfo() }
            }
            .map { x -> x.body()!! }
            .replay(1)
            .autoConnect()
    }
}
