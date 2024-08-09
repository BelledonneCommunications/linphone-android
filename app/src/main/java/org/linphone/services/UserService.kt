package org.linphone.services

import android.content.Context
import io.reactivex.rxjava3.core.Observable
import java.util.concurrent.atomic.AtomicReference
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
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

    val user: Observable<AuthenticatedUser>

    init {
        Log.i("Created UserService")

        val asm = AuthStateManager.getInstance(context)
        user = asm.user
    }
}
