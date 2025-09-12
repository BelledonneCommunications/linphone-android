package org.linphone.authentication

import android.content.Context
import androidx.annotation.AnyThread
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
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
            .distinctUntilChanged { user -> user.id ?: "" }
            .subscribe(
                {
                    try {
                        Log.i("AUTH user ID : ${it.id}")
                        if (it.hasValidId()) {
                            load(it.id!!)
                        } else {
                            clear()
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

        val asm = AuthStateManager.getInstance(mContext)
        val apiClientService = APIClientService(mContext)

        val requestUserId = asm.getUser().id

        apiClientService.getUCGatewayService().doGetUserDevices(requestUserId).enqueue(object :
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
