package org.linphone.authentication

import android.content.Context
import androidx.annotation.AnyThread
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.SubscribableUserDeviceList
import org.linphone.models.UserDevice
import org.linphone.services.APIClientService
import org.linphone.utils.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DimensionsAccountsManager(context: Context) {

    companion object {
        private val INSTANCE_REF: AtomicReference<WeakReference<DimensionsAccountsManager>> =
            AtomicReference(
                WeakReference(null)
            )

        @AnyThread
        fun getInstance(context: Context): DimensionsAccountsManager {
            var service = INSTANCE_REF.get().get()
            if (service == null) {
                service = DimensionsAccountsManager(context.applicationContext)
                INSTANCE_REF.set(WeakReference(service))
            }

            return service
        }
    }

    val devicesSubject = BehaviorSubject.create<SubscribableUserDeviceList>()

    private val mContext = context

    init {
        val asm = AuthStateManager.getInstance(context)
        val sub = asm.user
            .map { it.id ?: "" }
            .distinctUntilChanged()
            .subscribe(
                {
                    try {
                        Log.i("AUTH user ID : $it")
                        when (it) {
                            AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER -> Log.w(
                                "DimensionsAccountManager subscription triggered with initial AuthenticatedUser"
                            )
                            "" -> clear()
                            else -> load(it)
                        }
                    } catch (e: Exception) {
                        Log.e(e)
                    }
                },
                { Log.e("User ID error: " + it.message) }
            )
    }

    private fun load(userId: String) {
        Log.i("CoreContext.loadDimensionsAccounts")

        val dimensionsEnvironment = DimensionsEnvironmentService.getInstance(mContext).getCurrentEnvironment()
        val asm = AuthStateManager.getInstance(mContext)

        val apiClientService = APIClientService()
        val ucGatewayService = apiClientService.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(mContext).getAuthorizationServiceInstance(),
            asm
        )

        val userId = asm.getUser().id
        ucGatewayService.doGetUserDevices(userId).enqueue(object :
                Callback<List<UserDevice>> {
                override fun onFailure(call: Call<List<UserDevice>>, t: Throwable) {
                    Log.e(
                        "CoreContext.loadDimensionsAccounts.doGetUserDevices.onFailure::${t.message}"
                    )
                }

                override fun onResponse(
                    call: Call<List<UserDevice>>,
                    response: Response<List<UserDevice>>
                ) {
                    if (response.isSuccessful) {
                        devicesSubject.onNext(SubscribableUserDeviceList(response.body()))
                    } else {
                        Log.e(
                            "CoreContext.loadDimensionsAccounts.doGetUserDevices.onResponse::${response.message()}"
                        )
                    }
                }
            })
    }

    fun clear() {
        devicesSubject.onNext(SubscribableUserDeviceList(null))
    }
}
