package org.linphone.services.realtime

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.TransportEnum
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.DimensionsEnvironment
import org.linphone.models.realtime.EventSubscription
import org.linphone.models.realtime.RealtimeEventType
import org.linphone.models.realtime.SubscriptionData
import org.linphone.models.realtime.SubscriptionRequest
import org.linphone.models.realtime.SubscriptionState
import org.linphone.models.realtime.UnsubscriptionRequest
import org.linphone.utils.Log

// TODO - add errornotification service
open class RealtimeBaseService(context: Context, private val hubSuffix: String) : DefaultLifecycleObserver {

    private val environmentService = DimensionsEnvironmentService.getInstance(context)
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    var hubConnection: HubConnection? = null

    private var currentEnvironmentSubscription: Disposable? =
        environmentService.currentEnvironmentObservable.subscribe { environment ->
            try {
                createHubConnection(environment)
            } catch (e: Exception) {
                Log.e("RealtimeBaseService.currentEnvironmentSubscription", e)
            }
        }

    private var hubConnectionCompletable: Disposable? = null
    private var userSubscription: Disposable? = null

    private val isReadySubject = BehaviorSubject.createDefault(
        false
    )

    var ready = isReadySubject.map { x -> x }

    private var reconnectContext: ReconnectContext? = null

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        currentEnvironmentSubscription?.dispose()
        hubConnectionCompletable?.dispose()
    }

    private fun createHubConnection(environment: DimensionsEnvironment) {
        val connection = HubConnectionBuilder.create(
            "${environment.realtimeApiUri}/$hubSuffix"
        )
            .shouldSkipNegotiate(true)
            .withTransport(TransportEnum.WEBSOCKETS)
            .withAccessTokenProvider(
                Single.defer {
                    Single.just(
                        authStateManager.current.accessToken ?: throw Exception(
                            "Auth token is missing or expired"
                        )
                    )
                }
            )
            .build()

        hubConnection = connection

        connection.on(RealtimeEventType.ConnectEvent.eventName, { message: Any ->
            try {
                Log.d("RealtimeBaseService." + RealtimeEventType.ConnectEvent.eventName, message)

                runBlocking {
                    delay(1)
                    invokePendingHubRequests()
                }

                isReadySubject.onNext(true)
                onConnected()
            } catch (e: Exception) {
                Log.e("RealtimeBaseService." + RealtimeEventType.ConnectEvent.eventName, e)
            }
        }, Any::class.java)

        connection.on(RealtimeEventType.SubscribeResponse.eventName, { message: Any ->
            try {
                Log.d(
                    "RealtimeBaseService." + RealtimeEventType.SubscribeResponse.eventName,
                    message
                )
            } catch (e: Exception) {
                Log.e("RealtimeBaseService." + RealtimeEventType.SubscribeResponse.eventName, e)
            }
        }, Any::class.java)

        connection.on(RealtimeEventType.UnSubscribeResponse.eventName, { message: Any ->
            try {
                Log.d(
                    "RealtimeBaseService." + RealtimeEventType.UnSubscribeResponse.eventName,
                    message
                )
            } catch (e: Exception) {
                Log.e("RealtimeBaseService." + RealtimeEventType.UnSubscribeResponse.eventName, e)
            }
        }, Any::class.java)

        connection.onClosed {
            Log.w("RealtimeBaseService.connection closed")

            try {
                onDisconnected()

                // TODO Add warning notification

                CoroutineScope(Dispatchers.IO).launch {
                    tryReconnect()
                }
            } catch (e: Exception) {
                Log.e("RealtimeBaseService.onClosed", e)
            }
        }

        userSubscription = authStateManager.user
            .distinctUntilChanged { user -> user.id ?: "" }
            .takeUntil(destroy)
            .subscribe { u ->
                try {
                    if (u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) {
                        ensureConnected()
                    } else {
                        Log.d("RealtimeBaseService.userSubscription stopping connection")
                        connection.stop()
                    }
                } catch (e: Exception) {
                    Log.e("RealtimeBaseService.userSubscription", e)
                }
            }
    }

    private fun onConnected() {
        try {
            Log.d("RealtimeBaseService.OnConnected")
            // TODO Remove error notification
        } catch (e: Exception) {
            Log.e("RealtimeBaseService.OnConnected", e)
        }
    }

    private fun onDisconnected() {
        try {
            Log.d("RealtimeService.OnDisconnected")

            isReadySubject.onNext(false)
            // TODO Remove error notification
        } catch (e: Exception) {
            Log.e("RealtimeBaseService.OnConnected", e)
        }
    }

    private fun connect() {
        Log.d("RealtimeBaseService.connect")

        val connection = hubConnection

        if (connection != null) {
            hubConnectionCompletable?.dispose()
            hubConnectionCompletable = connection.start()
                .doOnComplete { Log.d("SignalR", "Connection started") }
                .doOnError { error ->
                    Log.e(
                        "SignalR",
                        "Error starting connection: ${error.message}"
                    )
                }
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                    {
                        println("SignalR connected.")
                    },
                    { e ->
                        println("SignalR connect error: $e")

                        CoroutineScope(Dispatchers.IO).launch {
                            tryReconnect()
                        }
                    }
                )
        } else {
            Log.w("RealtimeBaseService.connect called with no hubconnection")
        }
    }

    private fun invoke(methodName: String, args: Any): Completable {
        val connection = hubConnection
        if (connection != null) {
            return connection.invoke("${methodName}Request", args)
        } else {
            throw Exception("realtime invoke called with no hubconnection")
        }
    }

    private suspend fun tryReconnect() {
        if (reconnectContext == null) reconnectContext = ReconnectContext(0.0)

        val delayMs = nextRetryDelayInMilliseconds(reconnectContext!!)
        reconnectContext!!.previousRetryCount++

        delay(delayMs)

        ensureConnected()
    }

    private fun nextRetryDelayInMilliseconds(reconnectContext: ReconnectContext): Long {
        val seconds = reconnectContext.previousRetryCount.pow(1.5).coerceAtMost(30.0)
        val delay = Math.round(seconds * 1000) + 1000
        Log.d("RealtimeBaseService.nextRetryDelayInMilliseconds in $delay ms.")
        return delay
    }

    private fun ensureConnected() {
        Log.d("RealtimeBaseService.ensureConnected")
        val connection = hubConnection
        if (connection != null && connection.connectionState != HubConnectionState.CONNECTED) {
            if (connection.connectionState != HubConnectionState.DISCONNECTED) {
                Log.d("RealtimeBaseService.ensureConnected stopping connection")
                connection.stop()
            }

            connect()
        }
    }

    private val subscriptions = mutableMapOf<String, EventSubscription>()

    private suspend fun invokePendingHubRequests() = runBlocking {
        Log.d("RealtimeBaseService.invokePendingHubRequests Un/subscribe all pending.")

        Log.d(
            "RealtimeBaseService.invokePendingHubRequests State: ${hubConnection?.connectionState}"
        )

        Log.d("RealtimeBaseService.invokePendingHubRequests Subscriptions: $subscriptions")

        // When SignalR connects, invoke any pending tasks to subscribe to/unsubscribe from events.
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            val deferredCollection = subscriptions.map { (key, subscription) ->
                when (subscription.subscriptionState) {
                    SubscriptionState.FlaggedForSubscription -> {
                        async { subscribe(key, subscription) }
                    }
                    SubscriptionState.FlaggedForUnsubscription -> {
                        async { unsubscribe(key, subscription) }
                    }
                    else -> null
                }
            }.filterNotNull()

            deferredCollection.awaitAll()
        }
    }

    private fun <T : Any> Observable<T>.pairwise(): Observable<Pair<T, T>> {
        return Observable.defer {
            val subject = PublishSubject.create<T>()
            Observable.zip(
                this,
                subject
            ) { current, previous -> Pair(previous, current) }.doOnNext { pair ->
                subject.onNext(
                    pair.second
                )
            }
        }
    }

    /** Creates a flow that watches for changes to the current user and maintains
     * a realtime event subscription filtered to that user. */
    fun subscribeForCurrentUser(eventType: RealtimeEventType): Observable<Any> {
        return authStateManager.user
            .startWithItem(
                AuthenticatedUser(
                    AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            )
            .map { it.id.toString() }
            .pairwise()
            .map { (prevUserId, currentUserId) ->
                runBlocking {
                    if (prevUserId != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER && prevUserId != currentUserId) {
                        removeSubscription(eventType, prevUserId)
                    }
                    if (currentUserId != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER && currentUserId != prevUserId) {
                        addSubscription(eventType, currentUserId)
                    }
                }
            }
    }

    fun addSubscription(
        eventType: RealtimeEventType,
        id: String,
        additionalData: Map<String, Any>? = null
    ) {
        val key = subscriptionKey(eventType, id)

        var subscription = subscriptions[key]

        // Only need to act if it doesn't exist (unsubscribed) or it's currently pending unsubscribe.
        if (subscription == null ||
            subscription.subscriptionState == SubscriptionState.FlaggedForUnsubscription ||
            subscription.subscriptionState == SubscriptionState.Unsubscribing
        ) {
            val data = SubscriptionData(eventType.eventName, id).apply {
                // additionalData?.forEach { (k, v) -> this.additionalData[k] = v } //TODO add property spreading for tileEvent
            }

            val request = SubscriptionRequest(data)
            subscription = EventSubscription(request, SubscriptionState.FlaggedForSubscription)
            subscriptions[key] = subscription

            subscribe(key, subscription)
        }
    }

    private fun subscribe(key: String, subscription: EventSubscription) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                updateState(key, SubscriptionState.Subscribing)

                println("Subscribing $key")

                invoke("subscribe", subscription.requestInfo)

                updateState(key, SubscriptionState.Subscribed)

                println("Subscription $key subscribed.")
            }
        } catch (e: Exception) {
            if (subscription.subscriptionState == SubscriptionState.Subscribing) {
                updateState(key, SubscriptionState.FlaggedForSubscription)
            }
        }
    }

    suspend fun removeSubscription(
        eventType: RealtimeEventType,
        selector: String,
        delayMilliseconds: Long? = null
    ) {
        removeSubscriptionInternal(eventType, selector, delayMilliseconds)
    }

    suspend fun removeSubscription(
        eventType: RealtimeEventType,
        selector: (EventSubscription) -> Boolean,
        delayMilliseconds: Long? = null
    ) {
        val subscriptionIds = subscriptions
            .filter { (_, sub) -> selector(sub) }
            .map { (_, sub) -> sub.requestInfo.event.id }

        subscriptionIds.forEach { id ->
            removeSubscriptionInternal(eventType, id, delayMilliseconds)
        }
    }

    private suspend fun removeSubscriptionInternal(
        eventType: RealtimeEventType,
        id: String,
        delayMilliseconds: Long? = null
    ) {
        val key = subscriptionKey(eventType, id)
        println("removeSubscription $key ${subscriptions[key]}")

        // Only need to act if a matching subscription entry exists and is subscribed/subscribing
        if (isState(
                key,
                SubscriptionState.FlaggedForSubscription,
                SubscriptionState.Subscribing,
                SubscriptionState.Subscribed
            )
        ) {
            val data = SubscriptionData(eventType.eventName, id)
            val request = UnsubscriptionRequest(data)
            val subscription =
                EventSubscription(request, SubscriptionState.FlaggedForUnsubscription)
            subscriptions[key] = subscription

            println("Queued unsubscribe $key")

            // If desired, delay the unsubscribe by a few seconds to prevent unnecessary un/resubscribe
            // in the event we rapidly remove then re-add a subscription
            // (which is likely when switching user groups on the contacts page for example).
            delayMilliseconds?.let {
                delay(it)
            }

            // Check we still need to unsubscribe
            if (isState(key, SubscriptionState.FlaggedForUnsubscription)) {
                unsubscribe(key, subscription)
            } else {
                println("Unsubscribe for $key no longer relevant ${subscription.subscriptionState}")
            }
        }
    }

    private fun unsubscribe(key: String, subscription: EventSubscription) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                updateState(key, SubscriptionState.Unsubscribing)

                println("Unsubscribing $key...")

                invoke("unSubscribe", subscription.requestInfo)

                subscriptions.remove(key)

                println("Unsubscribed $key.")
            }
        } catch (e: Exception) {
            if (isState(key, SubscriptionState.Unsubscribing)) {
                updateState(key, SubscriptionState.FlaggedForUnsubscription)
            }
        }
    }

    private fun subscriptionKey(eventType: RealtimeEventType, id: String): String {
        return "$eventType:$id"
    }

    private fun updateState(key: String, state: SubscriptionState) {
        val subscription = subscriptions[key]
            ?: throw IllegalArgumentException(
                "Subscription $key not found attempting to update to $state"
            )

        subscriptions[key] = subscription.copy(subscriptionState = state)
    }

    private fun isState(key: String, vararg states: SubscriptionState): Boolean {
        val subscription = subscriptions[key]
        return subscription != null && states.contains(subscription.subscriptionState)
    }
}

data class ReconnectContext(var previousRetryCount: Double)
