package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicReference
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
import org.linphone.models.realtime.PresenceProfile
import org.linphone.utils.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class PresenceProfileService(val context: Context) : DefaultLifecycleObserver {
    private val apiClient = APIClientService(context)
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    private val presenceProfilesSubject = BehaviorSubject.create<List<PresenceProfile>>()
    val presenceProfiles = presenceProfilesSubject.map { x -> x }
        .replay(1)
        .autoConnect()

    private var userSubscription: Disposable? = null

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        userSubscription?.dispose()
    }

    init {
        Log.d("Created PresenceProfileService")

        val userSubscription = authStateManager.user
            .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
            .distinctUntilChanged { user -> user.id ?: "" }
            .takeUntil(destroy)
            .subscribe { user ->
                try {
                    Log.d("Presence Profile user: " + user.name)
                    if ((user.id == null || user.id == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) && presenceProfilesSubject.value != null) {
                        presenceProfilesSubject.onNext(
                            listOf()
                        )
                    } else {
                        fetchPresenceProfiles()
                    }
                } catch (ex: Exception) {
                    Log.e(ex)
                }
            }
    }

    companion object {
        private const val TAG: String = "PresenceProfileService"

        private val instance: AtomicReference<PresenceProfileService> =
            AtomicReference<PresenceProfileService>()

        fun getInstance(context: Context): PresenceProfileService {
            var svc = instance.get()
            if (svc == null) {
                svc = PresenceProfileService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    private fun fetchPresenceProfiles() {
        Log.d("Fetch presence profiles...")

        val userId = authStateManager.getUser().id
        if (!userId.isNullOrBlank()) {
            apiClient.getUCGatewayService().doGetPresenceProfiles(userId)
                .enqueue(object : Callback<List<PresenceProfile>> {
                    override fun onFailure(call: Call<List<PresenceProfile>>, t: Throwable) {
                        Log.e("Failed to fetch presence profiles", t)
                    }

                    override fun onResponse(
                        call: Call<List<PresenceProfile>>,
                        response: Response<List<PresenceProfile>>
                    ) {
                        Timber.d("Got presence profiles from API")
                        response.body()?.let { presenceProfilesSubject.onNext(it) }
                    }
                })
        }
    }

    fun getPresenceProfileById(stateId: String): PresenceProfile? {
        val profiles = presenceProfilesSubject.value ?: return null

        return profiles.firstOrNull { x -> x.id == stateId }
    }
}
