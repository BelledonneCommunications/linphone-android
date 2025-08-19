package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.rx3.rxSingle
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
import org.linphone.models.TenantBrandingDefinition
import org.linphone.utils.Log
import org.linphone.utils.Optional

class BrandingService(val context: Context) : DefaultLifecycleObserver {

    private val apiClient = APIClientService(context)
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    val brand = authStateManager.user
        .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
        .distinctUntilChanged { user -> user.id ?: "" }
        .switchMapSingle { rxSingle { fetchBranding() } }
        .replay(1)
        .autoConnect()
        .takeUntil(destroy)

    init {
        Log.d("Created BrandingService")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()
    }

    companion object {
        private const val TAG: String = "BrandingService"

        private val instance: AtomicReference<BrandingService> = AtomicReference<BrandingService>()

        fun getInstance(context: Context): BrandingService {
            var svc = instance.get()
            if (svc == null) {
                svc = BrandingService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    private suspend fun fetchBranding(): Optional<TenantBrandingDefinition> {
        val body = apiClient.getUCGatewayService().getUserBranding().body()
        return if (body == null) Optional.empty() else Optional.of(body)
    }
}
