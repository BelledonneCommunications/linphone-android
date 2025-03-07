package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicReference
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.TenantBrandingDefinition
import org.linphone.utils.Log
import org.linphone.utils.Optional
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class BrandingService(val context: Context) : DefaultLifecycleObserver {

    private val apiClient = APIClientService()
    private val dimensionsEnvironment = DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
    private val authStateManager = AuthStateManager.getInstance(context)

    private val brandSubject = BehaviorSubject.create<Optional<TenantBrandingDefinition>>()
    private val destroy = PublishSubject.create<Unit>()

    public val brand = brandSubject.map { x -> x }

    init {
        Log.d("Created BrandingService")

        val sub = authStateManager.user
            .distinctUntilChanged { user -> user.id ?: "" }
            .takeUntil(destroy)
            .subscribe { user ->
                try {
                    Log.d("Brand user: " + user.name)
                    if ((user.id == null || user.id == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) && brandSubject.value != null) {
                        brandSubject.onNext(
                            Optional.empty()
                        )
                    } else {
                        fetchBranding()
                    }
                } catch (ex: Exception) {
                    Log.e(ex)
                }
            }
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

    fun fetchBranding() {
        Log.d("Fetch branding...")

        apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        ).doGetUserBranding()
            .enqueue(object : Callback<TenantBrandingDefinition> {
                override fun onFailure(call: Call<TenantBrandingDefinition>, t: Throwable) {
                    Log.e("Failed to fetch brand", t)
                }

                override fun onResponse(
                    call: Call<TenantBrandingDefinition>,
                    response: Response<TenantBrandingDefinition>
                ) {
                    Timber.d("Got brand from API")
                    brandSubject.onNext(Optional.ofNullable(response.body()))
                }
            })
    }
}
