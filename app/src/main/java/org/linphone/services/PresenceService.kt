package org.linphone.services

import PresenceEventData
import PresenceObservable
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
import org.linphone.models.realtime.RealtimeEventPresence
import org.linphone.models.realtime.RealtimeEventType
import org.linphone.models.realtime.SetPresenceModel
import org.linphone.services.realtime.RealtimeUserService
import org.linphone.utils.Log
import org.linphone.utils.Optional
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PresenceService(val context: Context) : DefaultLifecycleObserver {
    private val destroy = PublishSubject.create<Unit>()
    private val authStateManager = AuthStateManager.getInstance(context)
    private val apiClient = APIClientService(context)
    private val realtimeUserService = RealtimeUserService.getInstance(context)

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()
    }

    companion object {
        private const val TAG: String = "PresenceService"

        private val instance: AtomicReference<PresenceService> =
            AtomicReference<PresenceService>()

        fun getInstance(context: Context): PresenceService {
            var svc = instance.get()
            if (svc == null) {
                svc = PresenceService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    private val presenceObservables = mutableMapOf<String, PresenceObservable>()

    val currentUserPresence: Observable<Optional<PresenceEventData>> = authStateManager.user
        .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
        .switchMap { user ->
            val result: Observable<Optional<PresenceEventData>>

            if (user.id.toString() != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) {
                result = getUserPresenceStream(user.id.toString())
                    .map {
                        Optional.of(it)
                    }
            } else {
                result = Observable.just(Optional.empty())
            }

            result
        }
        .replay(1)
        .autoConnect()

    init {
        realtimeUserService.hubConnection?.on(RealtimeEventType.PresenceEvent.eventName, { event: RealtimeEventPresence ->
            try {
                Log.d(RealtimeEventType.PresenceEvent.eventName, event)

                val observable = presenceObservables[event.userId]
                observable?.subject?.onNext(event.data)
            } catch (e: Exception) {
                Log.e(RealtimeEventType.PresenceEvent.eventName, e)
            }
        }, RealtimeEventPresence::class.java)
    }

    fun setPresenceState(userId: String, presence: SetPresenceModel) {
        apiClient.getUCGatewayService().doSetPresence(userId, presence)
            .enqueue(object : Callback<Void> {
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("applyPresence", t)
                }

                override fun onResponse(
                    call: Call<Void>,
                    response: Response<Void>
                ) {
                    try {
                        if (response.isSuccessful) {
                            Log.i("Presence update succeeded")
                        } else {
                            Log.i("Presence update failed with ${response.code()}")
                            Toast.makeText(
                                coreContext.context,
                                context.getString(R.string.presenceservice_presence_update_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("setPresenceState", e)
                    }
                }
            })
    }

    fun getUserPresenceStream(userId: String): Observable<PresenceEventData> {
        val existingObservable = presenceObservables[userId]
        return if (existingObservable != null) {
            existingObservable.data
        } else {
            val newObservable = PresenceObservable(
                { realtimeUserService.addSubscription(RealtimeEventType.PresenceEvent, userId) },
                { onObservableRemoved(userId) }
            )
            presenceObservables[userId] = newObservable
            newObservable.data
        }
    }

    // FIXME: think of a better way of getting the current users PresenceEventData
    fun getCurrent(userId: String): PresenceEventData? {
        val existingObservable = presenceObservables[userId]
        if (existingObservable != null) {
            return existingObservable.subject.value
        }
        return null
    }

    private fun onObservableRemoved(userId: String) {
        println("onObservableRemoved: $userId")

        runBlocking {
            realtimeUserService.removeSubscription(RealtimeEventType.PresenceEvent, userId, 5000)
        }
    }
}
